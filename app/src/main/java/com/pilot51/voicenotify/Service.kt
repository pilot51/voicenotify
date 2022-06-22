/*
 * Copyright 2012 Mark Injerd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pilot51.voicenotify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.pilot51.voicenotify.AppListFragment.Companion.findOrAddApp
import com.pilot51.voicenotify.Common.getPrefs
import com.pilot51.voicenotify.Common.getSelectedAudioStream
import com.pilot51.voicenotify.Common.init
import com.pilot51.voicenotify.IgnoreReason.Companion.convertSetToString
import com.pilot51.voicenotify.NotifyList.Companion.addNotification
import com.pilot51.voicenotify.NotifyList.Companion.refresh
import com.pilot51.voicenotify.Shake.OnShakeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class Service : NotificationListenerService() {
	private lateinit var prefs: SharedPreferences
	private val lastMsg: MutableMap<App?, String?> = HashMap()
	private val lastMsgTime: MutableMap<App?, Long> = HashMap()
	private var tts: TextToSpeech? = null
	private var shouldRequestFocus = false
	private lateinit var audioMan: AudioManager
	private lateinit var telephony: TelephonyManager
	private val stateReceiver = DeviceStateReceiver()
	private var repeater: RepeatTimer? = null
	private lateinit var shake: Shake
	private val repeatList: MutableList<NotificationInfo> = ArrayList()
	private val audioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
			.setAudioAttributes(AudioAttributes.Builder()
				.setLegacyStreamType(AudioManager.STREAM_MUSIC).build())
			.build()
	} else null

	/**
	 * this is used to determine if we are the first, middle, or last thing to be spoken at the moment, for enabling/disabling shake and audio focus request
	 * entries are added right before the call to speak and removed by onDone in the utteranceProgressListener
	 * if the list is empty when we enqueue a message, we trigger shaking and audio focus requesting
	 * if the list is empty when we finish speaking a message, we untrigger them.
	 */
	@SuppressLint("UseSparseArrays")
	private val ttsQueue: MutableMap<Long, NotificationInfo> = HashMap()
	private val statusListener = object : OnStatusChangeListener {
		override fun onStatusChanged() {
			if (isSuspended && tts != null) {
				synchronized(ttsQueue) {
					for (info in ttsQueue.values) {
						info.addIgnoreReason(IgnoreReason.SUSPENDED)
					}
				}
				tts!!.stop()
			}
			sendBroadcast(Intent(applicationContext, WidgetProvider::class.java)
				.setAction(WidgetProvider.ACTION_UPDATE))
		}
	}

	override fun onCreate() {
		prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		tts = TextToSpeech(applicationContext, OnInitListener { status ->
			if (status == TextToSpeech.ERROR) {
				Log.w(TAG, getString(R.string.error_tts_init))
				Toast.makeText(applicationContext, R.string.error_tts_init, Toast.LENGTH_LONG).show()
				return@OnInitListener
			}
			tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
				override fun onStart(utteranceId: String) {}
				override fun onStop(utteranceId: String, interrupted: Boolean) {
					if (interrupted) {
						val info = ttsQueue[utteranceId.toLong()]
						if (info != null) {
							info.setSilenced()
							refresh()
						}
						ttsQueue.clear()
					}
					onDone(utteranceId)
				}

				override fun onDone(utteranceId: String) {
					synchronized(ttsQueue) { ttsQueue.remove(utteranceId.toLong()) }
					if (ttsQueue.isEmpty()) {
						if (shouldRequestFocus) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
								audioMan.abandonAudioFocusRequest(audioFocusRequest!!)
							} else {
								@Suppress("DEPRECATION")
								audioMan.abandonAudioFocus(null)
							}
						}
						shake.disable()
					}
				}

				@Deprecated("Deprecated in Java")
				override fun onError(utteranceId: String) {
					Log.e(TAG, "Utterance error")
				}
			})
		})
		super.onCreate()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		val notification = sbn.notification
		if (prefs.getBoolean(getString(R.string.key_ignore_groups), true)
			&& notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
			return  // Completely ignore group summary notifications.
		}
		val app = findOrAddApp(sbn.packageName, this)
		val info = NotificationInfo(app, notification, applicationContext)
		val msgTime = info.calendar.timeInMillis
		val ttsMsg = info.ttsMessage
		val ignoreStrings = prefs.getString(getString(R.string.key_ignore_strings), "")!!.lowercase().split("\n").toTypedArray()
		var stringIgnored = false
		for (s in ignoreStrings) {
			if (s.isNotEmpty() && ttsMsg != null && ttsMsg.lowercase().contains(s)) {
				stringIgnored = true
				break
			}
		}
		if (app != null && !app.enabled) {
			info.addIgnoreReason(IgnoreReason.APP)
		}
		if (stringIgnored) {
			info.addIgnoreReason(IgnoreReason.STRING)
		}
		if (TextUtils.isEmpty(ttsMsg)
			&& prefs.getBoolean(getString(R.string.key_ignore_empty), false)) {
			info.addIgnoreReason(IgnoreReason.EMPTY_MSG)
		}
		var ignoreRepeat = -1
		val ignoreRepeatStr = prefs.getString(getString(R.string.key_ignore_repeat), null)
		if (!TextUtils.isEmpty(ignoreRepeatStr)) {
			ignoreRepeat = ignoreRepeatStr!!.toInt()
		}
		if (lastMsg.containsKey(app)) {
			if (lastMsg[app] == ttsMsg && (ignoreRepeat == -1 || msgTime - lastMsgTime[app]!! < ignoreRepeat * 1000)) {
				info.addIgnoreReasonIdentical(ignoreRepeat)
			}
		}
		addNotification(info)
		if (info.getIgnoreReasons().isEmpty()) {
			var delay = 0
			val delayStr = prefs.getString(getString(R.string.key_ttsDelay), null)
			if (!TextUtils.isEmpty(delayStr)) {
				delay = delayStr!!.toInt()
			}
			if (!isScreenOn()) {
				var interval = 0
				val intervalStr = prefs.getString(getString(R.string.key_tts_repeat), null)
				if (!TextUtils.isEmpty(intervalStr)) {
					interval = intervalStr!!.toInt()
				}
				if (interval > 0) {
					synchronized(repeatList) { repeatList.add(info) }
					if (repeater == null) {
						repeater = RepeatTimer(interval)
					}
				}
			}
			if (delay < 0) { // Just in case we get a weird value, don't want to try to make the Timer wait for negative time
				delay = 0
			}
			Timer().schedule(object : TimerTask() {
				override fun run() {
					val ignoreReasons = ignore()
					if (ignoreReasons.isNotEmpty()) {
						Log.i(TAG, "Notification ignored for reason(s): "
							+ convertSetToString(ignoreReasons, this@Service))
						info.addIgnoreReasons(ignoreReasons)
						return
					}
					CoroutineScope(Dispatchers.Main).launch {
						speak(info)
					}
				}
			}, delay * 1000L) // A delay of 0 works fine, and means that all speak calls anywhere are running in their own thread and not blocking.
			lastMsg[app] = ttsMsg
			lastMsgTime[app] = msgTime
		} else {
			Log.i(TAG, "Notification from " + app?.label
				+ " ignored for reason(s): " + info.getIgnoreReasonsAsText(this))
		}
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {}

	/**
	 * Send a notification to be spoken by TTS.
	 * @param info The info for the notification to be spoken.
	 */
	private fun speak(info: NotificationInfo) {
		if (tts == null) {
			Log.w(TAG, "Speak failed due to service destroyed")
			info.addIgnoreReason(IgnoreReason.SERVICE_STOPPED)
			refresh()
			return
		}
		val notificationTime = info.calendar.timeInMillis
		synchronized(ttsQueue) {
			if (ttsQueue.isEmpty()) { //if there are no messages in the queue, start up shake detection and audio focus requesting
				shake.enable()
				shouldRequestFocus = getPrefs(applicationContext)
					.getBoolean(getString(R.string.key_audio_focus), false)
				if (shouldRequestFocus) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						audioMan.requestAudioFocus(audioFocusRequest!!)
					} else {
						@Suppress("DEPRECATION")
						audioMan.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
							AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
					}
				}
			}
			//regardless, add the message to the queue, parallelling the TextToSpeech queue since we can't access it.
			ttsQueue.put(notificationTime, info)
		}
		//once the message is in our queue, send it to the real one with the necessary parameters
		val audioStream = getSelectedAudioStream(applicationContext)
		val utteranceId = notificationTime.toString()
		val isSpeakFailed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			val ttsParams = Bundle()
			ttsParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream)
			tts!!.speak(info.ttsMessage, TextToSpeech.QUEUE_ADD, ttsParams, utteranceId)
		} else {
			val ttsParams = HashMap<String, String>()
			//needed because we want to apply stream changes immediately
			ttsParams[TextToSpeech.Engine.KEY_PARAM_STREAM] = audioStream.toString()
			//not used anywhere, but has to be set to get the UtteranceProgressListener to trigger its submethods
			ttsParams[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
			@Suppress("DEPRECATION")
			tts!!.speak(info.ttsMessage, TextToSpeech.QUEUE_ADD, ttsParams)
		} != TextToSpeech.SUCCESS
		if (isSpeakFailed) {
			Log.e(TAG, "Error adding notification to TTS queue")
			synchronized(ttsQueue) { ttsQueue.remove(notificationTime) }
		}
	}

	/**
	 * Checks for any notification-independent ignore states.
	 * @return Set of ignore reasons.
	 */
	private fun ignore(): Set<IgnoreReason> {
		val ignoreReasons: MutableSet<IgnoreReason> = HashSet()
		if (isSuspended) {
			ignoreReasons.add(IgnoreReason.SUSPENDED)
		}
		val c = Calendar.getInstance()
		val calTime = c[Calendar.HOUR_OF_DAY] * 60 + c[Calendar.MINUTE]
		val quietStart = prefs.getInt(getString(R.string.key_quietStart), 0)
		val quietEnd = prefs.getInt(getString(R.string.key_quietEnd), 0)
		if ((quietStart < quietEnd) and (quietStart <= calTime) and (calTime < quietEnd)
			|| quietEnd < quietStart && (quietStart <= calTime || calTime < quietEnd)) {
			ignoreReasons.add(IgnoreReason.QUIET)
		}
		if ((audioMan.ringerMode == AudioManager.RINGER_MODE_SILENT
				|| audioMan.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
			&& !prefs.getBoolean(Common.KEY_SPEAK_SILENT_ON, false)) {
			ignoreReasons.add(IgnoreReason.SILENT)
		}
		val isInCall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			val telecomMan = getSystemService(TELECOM_SERVICE) as TelecomManager
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
				== PackageManager.PERMISSION_GRANTED
			) telecomMan.isInCall else false
		} else {
			@Suppress("DEPRECATION")
			telephony.callState == TelephonyManager.CALL_STATE_OFFHOOK
		}
		if (isInCall) {
			ignoreReasons.add(IgnoreReason.CALL)
		}
		if (!isScreenOn() && !prefs.getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true)) {
			ignoreReasons.add(IgnoreReason.SCREEN_OFF)
		}
		if (isScreenOn() && !prefs.getBoolean(Common.KEY_SPEAK_SCREEN_ON, true)) {
			ignoreReasons.add(IgnoreReason.SCREEN_ON)
		}
		if (!isHeadsetOn() && !prefs.getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true)) {
			ignoreReasons.add(IgnoreReason.HEADSET_OFF)
		}
		if (isHeadsetOn() && !prefs.getBoolean(Common.KEY_SPEAK_HEADSET_ON, true)) {
			ignoreReasons.add(IgnoreReason.HEADSET_ON)
		}
		return ignoreReasons
	}

	private inner class RepeatTimer(minuteInterval: Int) : TimerTask() {
		init {
			if (minuteInterval > 0) {
				val interval = minuteInterval * 60000L
				Timer().schedule(this, interval, interval)
			}
		}

		override fun run() {
			if (ignore().isNotEmpty()) return
			CoroutineScope(Dispatchers.Main).launch {
				synchronized(repeatList) {
					for (info in repeatList) {
						speak(info)
					}
				}
			}
		}

		override fun cancel(): Boolean {
			repeater = null
			return super.cancel()
		}
	}

	override fun onBind(intent: Intent): IBinder? {
		if (isInitialized) return super.onBind(intent)
		init(this)
		audioMan = getSystemService(AUDIO_SERVICE) as AudioManager
		telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
		val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
		registerReceiver(stateReceiver, filter)
		shake = Shake(this)
		shake.setOnShakeListener(object : OnShakeListener {
			override fun onShake() {
				Log.i(TAG, "TTS silenced by shake")
				synchronized(ttsQueue) {
					for (info in ttsQueue.values) {
						info.addIgnoreReason(IgnoreReason.SHAKE)
					}
				}
				tts?.stop()
				refresh()
			}
		})
		registerOnStatusChangeListener(statusListener)
		setInitialized(true)
		return super.onBind(intent)
	}

	override fun onUnbind(intent: Intent): Boolean {
		if (isInitialized) {
			tts?.stop()
			unregisterReceiver(stateReceiver)
			setInitialized(false)
			unregisterOnStatusChangeListener(statusListener)
		}
		return false
	}

	override fun onDestroy() {
		tts?.shutdown()
		tts = null
		super.onDestroy()
	}

	interface OnStatusChangeListener {
		/**
		 * Called when the service status has changed.
		 * @see Service.isRunning
		 * @see Service.isSuspended
		 */
		fun onStatusChanged()
	}

	private fun setInitialized(initialized: Boolean) {
		isInitialized = initialized
		onStatusChanged()
	}

	private fun isScreenOn(): Boolean {
		isScreenOn = CheckScreen.isScreenOn(this)
		return isScreenOn
	}

	private fun isHeadsetOn(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			audioMan.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
				it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
			}
		} else {
			@Suppress("DEPRECATION")
			audioMan.isBluetoothA2dpOn || audioMan.isWiredHeadsetOn
		}
	}

	private object CheckScreen {
		private lateinit var powerMan: PowerManager

		fun isScreenOn(c: Context): Boolean {
			if (!::powerMan.isInitialized) {
				powerMan = c.getSystemService(POWER_SERVICE) as PowerManager
			}
			return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
				powerMan.isInteractive
			} else {
				@Suppress("DEPRECATION")
				powerMan.isScreenOn
			}
		}
	}

	private inner class DeviceStateReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val action = intent.action
			var interruptIfIgnored = true
			when (action) {
				Intent.ACTION_SCREEN_ON -> {
					isScreenOn = true
					if (repeater != null) {
						repeater!!.cancel()
						synchronized(repeatList) { repeatList.clear() }
					}
					interruptIfIgnored = false
				}
				Intent.ACTION_SCREEN_OFF -> {
					isScreenOn = false
					interruptIfIgnored = false
				}
			}
			if (interruptIfIgnored && tts != null) {
				val ignoreReasons = ignore()
				if (ignoreReasons.isNotEmpty()) {
					Log.i(TAG, "Notifications silenced/ignored for reason(s): "
						+ convertSetToString(ignoreReasons, context))
					synchronized(ttsQueue) {
						for (info in ttsQueue.values) {
							info.addIgnoreReasons(ignoreReasons)
						}
					}
					tts!!.stop()
				}
			}
		}
	}

	companion object {
		private val TAG = Service::class.simpleName
		private var isInitialized = false
		val isRunning get() = isInitialized
		var isSuspended = false
			private set
		private var isScreenOn = false
		private val statusListeners: MutableList<OnStatusChangeListener> = ArrayList()

		fun registerOnStatusChangeListener(listener: OnStatusChangeListener) {
			statusListeners.add(listener)
		}

		fun unregisterOnStatusChangeListener(listener: OnStatusChangeListener) {
			statusListeners.remove(listener)
		}

		private fun onStatusChanged() {
			for (l in statusListeners) {
				l.onStatusChanged()
			}
		}

		fun toggleSuspend(): Boolean {
			isSuspended = isSuspended xor true
			onStatusChanged()
			return isSuspended
		}

		fun toggleSuspend(status: Boolean): Boolean {
			isSuspended = status
			onStatusChanged()
			return isSuspended
		}
	}
}
