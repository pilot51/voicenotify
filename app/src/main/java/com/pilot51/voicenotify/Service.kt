/*
 * Copyright 2011-2024 Mark Injerd
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
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
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.pilot51.voicenotify.PermissionHelper.isPermissionGranted
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_IS_SUSPENDED
import com.pilot51.voicenotify.PreferenceHelper.KEY_IS_SUSPENDED
import com.pilot51.voicenotify.PreferenceHelper.getPrefFlow
import com.pilot51.voicenotify.PreferenceHelper.setPref
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppDatabase
import com.pilot51.voicenotify.db.AppDatabase.Companion.db
import com.pilot51.voicenotify.db.Settings
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_AUDIO_FOCUS
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_EMPTY
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_GROUPS
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_QUIET_TIME
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_HEADSET_OFF
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_HEADSET_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SCREEN_OFF
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SCREEN_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SILENT_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_TTS_STREAM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

class Service : NotificationListenerService() {
	private val appContext by ::applicationContext
	private val lastMsg = mutableMapOf<App?, String?>()
	private val lastMsgTime = mutableMapOf<App?, Long>()
	private var tts: TextToSpeech? = null
	private var shouldRequestFocus = false
	private lateinit var audioMan: AudioManager
	private lateinit var telephony: TelephonyManager
	private val stateReceiver = DeviceStateReceiver()
	private var repeater: RepeatTimer? = null
	private val shake by lazy { Shake(appContext) }
	private val repeatList = mutableListOf<NotificationInfo>()
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
	private val ttsQueue = linkedMapOf<Long, NotificationInfo>()

	override fun onCreate() {
		val ioScope = CoroutineScope(Dispatchers.IO)
		ioScope.launch {
			isSuspended.collect {
				if (!it) return@collect
				tts?.run {
					synchronized(ttsQueue) {
						for (info in ttsQueue.values) {
							info.ignoreReasons.add(IgnoreReason.SUSPENDED)
						}
					}
					stop()
				}
			}
		}
		super.onCreate()
	}

	private fun initTts(onInit: () -> Unit)  {
		if (tts != null) {
			onInit()
			return
		}
		tts = TextToSpeech(appContext, OnInitListener { status ->
			if (status == TextToSpeech.SUCCESS) {
				onInit()
			} else {
				shutdownTts()
				val errorMsg = getString(R.string.error_tts_init, status)
				Log.w(TAG, errorMsg)
				Toast.makeText(appContext, errorMsg, Toast.LENGTH_LONG).show()
				return@OnInitListener
			}
			tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
				override fun onStart(utteranceId: String) {}
				override fun onStop(utteranceId: String, interrupted: Boolean) {
					if (interrupted) {
						val info = ttsQueue[utteranceId.toLong()]
						if (info != null) {
							info.isInterrupted = true
							if (info.ignoreReasons.isEmpty()) {
								info.ignoreReasons.add(IgnoreReason.TTS_INTERRUPTED)
							}
							NotifyList.updateInfo(info)
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
						shutdownTts()
					}
				}

				@Deprecated("Deprecated in Java")
				override fun onError(utteranceId: String) {
					Log.e(TAG, "Utterance error")
				}
			})
		})
	}

	private fun restartTts() {
		synchronized(ttsQueue) {
			ttsQueue.values.forEach {
				if (it.ignoreReasons.contains(IgnoreReason.TTS_FAILED)) return@forEach
				it.ignoreReasons.add(IgnoreReason.TTS_RESTARTED)
				it.isInterrupted = true
				NotifyList.updateInfo(it)
			}
		}
		shutdownTts()
		initTts {
			CoroutineScope(Dispatchers.IO).launch {
				synchronized(ttsQueue) {
					val queueIterator = ttsQueue.iterator()
					queueIterator.forEach {
						val info = it.value
						val isFailed = tts?.speak(
							info.ttsMessage, TextToSpeech.QUEUE_ADD,
							getTtsParams(info.settings), it.key.toString()
						) != TextToSpeech.SUCCESS
						if (isFailed) {
							Log.e(TAG, "Error adding notification to queue after TTS restart. Not retrying again.")
							info.ignoreReasons.add(IgnoreReason.TTS_FAILED)
							info.isInterrupted = false
							queueIterator.remove()
						} else if (info.ignoreReasons.contains(IgnoreReason.TTS_FAILED)) {
							info.isInterrupted = true
						}
						NotifyList.updateInfo(info)
					}
				}
			}
		}
	}

	private fun shutdownTts() {
		tts?.shutdown()
		tts = null
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		CoroutineScope(Dispatchers.IO).launch {
			val notification = sbn.notification
			val app = Common.findOrAddApp(sbn.packageName)
			val settings = getCombinedSettings(app)
			if (settings.ignoreGroups ?: DEFAULT_IGNORE_GROUPS
				&& notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
				return@launch  // Completely ignore group summary notifications.
			}
			val info = NotificationInfo(app, notification, settings)
			val msgTime = info.calendar.timeInMillis
			val ttsMsg = info.ttsMessage
			if (app != null && !app.enabled) {
				info.ignoreReasons.add(IgnoreReason.APP)
			}
			if (info.isEmpty && settings.ignoreEmpty ?: DEFAULT_IGNORE_EMPTY) {
				info.ignoreReasons.add(IgnoreReason.EMPTY_MSG)
			}
			if (ttsMsg != null) {
				val requireStrings = settings.requireStrings?.split("\n")
				val stringRequired = requireStrings?.all {
					it.isNotEmpty() && !ttsMsg.contains(it, true)
				} ?: false
				if (stringRequired) {
					info.ignoreReasons.add(IgnoreReason.STRING_REQUIRED)
				}
				val ignoreStrings = settings.ignoreStrings?.split("\n")
				val stringIgnored = ignoreStrings?.any {
					it.isNotEmpty() && ttsMsg.contains(it, true)
				} ?: false
				if (stringIgnored) {
					info.ignoreReasons.add(IgnoreReason.STRING_IGNORED)
				}
			}
			val ignoreRepeat = settings.ignoreRepeat ?: -1
			if (lastMsg.containsKey(app)) {
				if (lastMsg[app] == ttsMsg && (ignoreRepeat == -1 || msgTime - lastMsgTime[app]!! < ignoreRepeat * 1000)) {
					info.addIgnoreReasonIdentical(ignoreRepeat)
				}
			}
			NotifyList.addNotification(info)
			if (info.ignoreReasons.isEmpty()) {
				val delay = settings.ttsDelay ?: 0
				if (!isScreenOn()) {
					val interval = settings.ttsRepeat ?: 0.0
					if (interval > 0) {
						synchronized(repeatList) { repeatList.add(info) }
						if (repeater == null) {
							repeater = RepeatTimer(interval)
						}
					}
				}
				Timer().schedule(object : TimerTask() {
					override fun run() {
						val ignoreReasons = ignore(info.settings)
						if (ignoreReasons.isNotEmpty()) {
							Log.i(TAG, "Notification ignored for reason(s): "
								+ ignoreReasons.joinToString())
							info.ignoreReasons.addAll(ignoreReasons)
							return
						}
						CoroutineScope(Dispatchers.Main).launch {
							speak(info)
						}
					}
				}, (delay * 1000).toLong()) // A delay of 0 works fine, and means that all speak calls anywhere are running in their own thread and not blocking.
				lastMsg[app] = ttsMsg
				lastMsgTime[app] = msgTime
			} else {
				Log.i(TAG, "Notification from " + app?.label
					+ " ignored for reason(s): " + info.getIgnoreReasonsAsText())
			}
		}
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {}

	/**
	 * Send a notification to be spoken by TTS.
	 * @param info The info for the notification to be spoken.
	 */
	private fun speak(info: NotificationInfo) {
		if (!isRunning.value) {
			Log.w(TAG, "Speak failed due to service destroyed")
			info.ignoreReasons.add(IgnoreReason.SERVICE_STOPPED)
			NotifyList.updateInfo(info)
			return
		}
		if ((info.ttsMessage?.length ?: 0) > TextToSpeech.getMaxSpeechInputLength()) {
			info.ignoreReasons.add(IgnoreReason.TTS_LENGTH_LIMIT)
			info.isInterrupted = true
		}
		val notificationTime = info.calendar.timeInMillis
		synchronized(ttsQueue) {
			if (ttsQueue.isEmpty()) { //if there are no messages in the queue, start up shake detection and audio focus requesting
				shake.enable()
				shouldRequestFocus = info.settings.audioFocus ?: DEFAULT_AUDIO_FOCUS
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
		val utteranceId = notificationTime.toString()
		initTts {
			CoroutineScope(Dispatchers.IO).launch {
				val isSpeakFailed = tts?.speak(
					info.ttsMessage, TextToSpeech.QUEUE_ADD, getTtsParams(info.settings), utteranceId
				) != TextToSpeech.SUCCESS
				if (isSpeakFailed) {
					Log.e(TAG, "Error adding notification to TTS queue. Attempting to restart TTS.")
					info.ignoreReasons.add(IgnoreReason.TTS_FAILED)
					info.isInterrupted = false
					restartTts()
				}
				if (info.ignoreReasons.isNotEmpty()) {
					NotifyList.updateInfo(info)
				}
			}
		}
	}

	/**
	 * Checks for any notification-independent ignore states.
	 * @return Set of ignore reasons.
	 */
	private fun ignore(settings: Settings): Set<IgnoreReason> {
		val ignoreReasons: MutableSet<IgnoreReason> = HashSet()
		if (isSuspended.value) {
			ignoreReasons.add(IgnoreReason.SUSPENDED)
		}
		val c = Calendar.getInstance()
		val calTime = c[Calendar.HOUR_OF_DAY] * 60 + c[Calendar.MINUTE]
		val quietStart = settings.quietStart ?: DEFAULT_QUIET_TIME
		val quietEnd = settings.quietEnd ?: DEFAULT_QUIET_TIME
		if ((quietStart < quietEnd && quietStart <= calTime && calTime < quietEnd)
			|| (quietEnd < quietStart && (quietStart <= calTime || calTime < quietEnd))
		) ignoreReasons.add(IgnoreReason.QUIET)
		if ((audioMan.ringerMode == AudioManager.RINGER_MODE_SILENT
				|| audioMan.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
			&& !(settings.speakSilentOn ?: DEFAULT_SPEAK_SILENT_ON)) {
			ignoreReasons.add(IgnoreReason.SILENT)
		}
		val telecomMan = getSystemService(TELECOM_SERVICE) as TelecomManager
		@SuppressLint("MissingPermission")
		val isInCall = if (isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
			telecomMan.isInCall
		} else false
		if (isInCall) {
			ignoreReasons.add(IgnoreReason.CALL)
		}
		if (!isScreenOn() && !(settings.speakScreenOff ?: DEFAULT_SPEAK_SCREEN_OFF)) {
			ignoreReasons.add(IgnoreReason.SCREEN_OFF)
		}
		if (isScreenOn() && !(settings.speakScreenOn ?: DEFAULT_SPEAK_SCREEN_ON)) {
			ignoreReasons.add(IgnoreReason.SCREEN_ON)
		}
		if (!isHeadsetOn() && !(settings.speakHeadsetOff ?: DEFAULT_SPEAK_HEADSET_OFF)) {
			ignoreReasons.add(IgnoreReason.HEADSET_OFF)
		}
		if (isHeadsetOn() && !(settings.speakHeadsetOn ?: DEFAULT_SPEAK_HEADSET_ON)) {
			ignoreReasons.add(IgnoreReason.HEADSET_ON)
		}
		return ignoreReasons
	}

	private inner class RepeatTimer(minuteInterval: Double) : TimerTask() {
		init {
			if (minuteInterval > 0) {
				val interval = (minuteInterval * 60000).toLong()
				Timer().schedule(this, interval, interval)
			}
		}

		override fun run() {
			synchronized(repeatList) {
				for (info in repeatList) {
					if (ignore(info.settings).isNotEmpty()) continue
					speak(info)
				}
			}
		}

		override fun cancel(): Boolean {
			repeater = null
			return super.cancel()
		}
	}


	override fun onBind(intent: Intent): IBinder? {
		if (isRunning.value) return super.onBind(intent)
		audioMan = getSystemService(AUDIO_SERVICE) as AudioManager
		telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
		val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
		filter.addAction(Intent.ACTION_SCREEN_ON)
		filter.addAction(Intent.ACTION_SCREEN_OFF)
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
		registerReceiver(stateReceiver, filter)
		shake.onShake = {
			Log.i(TAG, "TTS silenced by shake")
			synchronized(ttsQueue) {
				for (info in ttsQueue.values) {
					info.ignoreReasons.add(IgnoreReason.SHAKE)
					NotifyList.updateInfo(info)
				}
			}
			shutdownTts()
		}
		setInitialized(true)
		return super.onBind(intent)
	}

	override fun onUnbind(intent: Intent): Boolean {
		if (isRunning.value) {
			shutdownTts()
			unregisterReceiver(stateReceiver)
			setInitialized(false)
		}
		return false
	}

	override fun onDestroy() {
		shutdownTts()
		super.onDestroy()
	}

	private fun setInitialized(initialized: Boolean) {
		isInitialized.value = initialized
	}

	private fun isScreenOn() =
		(appContext.getSystemService(POWER_SERVICE) as PowerManager).isInteractive &&
			(appContext.getSystemService(DISPLAY_SERVICE) as DisplayManager)
				.getDisplay(Display.DEFAULT_DISPLAY).state == Display.STATE_ON

	@get:RequiresApi(Build.VERSION_CODES.M)
	@delegate:RequiresApi(Build.VERSION_CODES.M)
	private val audioDeviceTypes by lazy {
		mutableListOf(
			AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
			AudioDeviceInfo.TYPE_WIRED_HEADSET,
			AudioDeviceInfo.TYPE_WIRED_HEADPHONES
		).apply {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@apply
			add(AudioDeviceInfo.TYPE_USB_HEADSET)
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@apply
			add(AudioDeviceInfo.TYPE_BLE_HEADSET)
		}.toTypedArray()
	}

	private fun isHeadsetOn(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			audioMan.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
				it.type.isAny(*audioDeviceTypes)
			}
		} else {
			@Suppress("DEPRECATION")
			audioMan.isBluetoothA2dpOn || audioMan.isWiredHeadsetOn
		}
	}

	private inner class DeviceStateReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val action = intent.action
			var interruptIfIgnored = true
			when (action) {
				Intent.ACTION_SCREEN_ON -> {
					if (!isScreenOn()) return
					if (repeater != null) {
						repeater!!.cancel()
						synchronized(repeatList) { repeatList.clear() }
					}
					interruptIfIgnored = false
				}
				Intent.ACTION_SCREEN_OFF -> {
					if (isScreenOn()) return
					interruptIfIgnored = false
				}
			}
			if (interruptIfIgnored) {
				tts?.run {
					synchronized(ttsQueue) {
						for (info in ttsQueue.values) {
							val ignoreReasons = ignore(info.settings)
							if (ignoreReasons.isNotEmpty()) {
								Log.i(TAG, "Notification from ${info.app?.label} silenced/ignored" +
									" for reason(s): ${ignoreReasons.joinToString()}")
								info.ignoreReasons.addAll(ignoreReasons)
							}
						}
					}
					stop()
				}
			}
		}
	}

	private suspend fun getCombinedSettings(app: App?): Settings {
		val gs = AppDatabase.globalSettingsFlow.first()
		return app?.run {
			db.settingsDao.getAppSettings(packageName).firstOrNull()?.let { gs.merge(it) }
		} ?: gs
	}

	companion object {
		private val TAG = Service::class.simpleName
		private val isInitialized = MutableStateFlow(false)
		val isRunning: StateFlow<Boolean> = isInitialized
		var isSuspended = MutableStateFlow(false)

		init {
			CoroutineScope(Dispatchers.IO).launch {
				getPrefFlow(KEY_IS_SUSPENDED, DEFAULT_IS_SUSPENDED).collect {
					isSuspended.value = it
				}
			}
		}

		private fun getTtsParams(settings: Settings) = Bundle().apply {
			putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, settings.ttsStream ?: DEFAULT_TTS_STREAM)
		}

		fun toggleSuspend(): Boolean {
			val suspended = isSuspended.value xor true
			setPref(KEY_IS_SUSPENDED, suspended)
			return suspended
		}
	}
}
