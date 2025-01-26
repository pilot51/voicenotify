/*
 * Copyright 2011-2025 Mark Injerd
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
import android.media.AudioManager.OnModeChangedListener
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.pilot51.voicenotify.NotifyList.notifyListMutex
import com.pilot51.voicenotify.PermissionHelper.isPermissionGranted
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_IS_SUSPENDED
import com.pilot51.voicenotify.PreferenceHelper.KEY_IS_SUSPENDED
import com.pilot51.voicenotify.PreferenceHelper.getPrefFlow
import com.pilot51.voicenotify.PreferenceHelper.setPref
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppDatabase
import com.pilot51.voicenotify.db.AppDatabase.Companion.db
import com.pilot51.voicenotify.db.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.PatternSyntaxException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Service : NotificationListenerService() {
	private val appContext by ::applicationContext
	private val ioScope = CoroutineScope(Dispatchers.IO)
	private var tts: TextToSpeech? = null
	private var isAwaitingTtsInit = MutableStateFlow(false)
	private var latestTtsStatus = TextToSpeech.STOPPED
	private var shouldRequestFocus = false
	private lateinit var audioMan: AudioManager
	private lateinit var telephonyMan: TelephonyManager
	private val stateReceiver = DeviceStateReceiver()
	private var repeaterJob: Job? = null
	private val shake by lazy { Shake(appContext) }
	private val repeatList = mutableListOf<NotificationInfo>()
	@get:RequiresApi(26)
	@delegate:RequiresApi(26)
	private val audioFocusRequest by lazy {
		AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
			.setAudioAttributes(AudioAttributes.Builder()
				.setLegacyStreamType(AudioManager.STREAM_MUSIC).build())
			.build()
	}
	private val phoneStateListener by lazy {
		@Suppress("DEPRECATION")
		object : PhoneStateListener() {
			@Deprecated("Deprecated in Java")
			override fun onCallStateChanged(state: Int, phoneNumber: String?) {
				if (isPhoneStateInCall(state)) processIgnoreForQueue()
			}
		}
	}
	private val audioModeListener by lazy {
		OnModeChangedListener { mode ->
			if (isAudioModeInCall(mode)) processIgnoreForQueue()
		}
	}

	/**
	 * this is used to determine if we are the first, middle, or last thing to be spoken at the moment, for enabling/disabling shake and audio focus request
	 * entries are added right before the call to speak and removed by onDone in the utteranceProgressListener
	 * if the list is empty when we enqueue a message, we trigger shaking and audio focus requesting
	 * if the list is empty when we finish speaking a message, we untrigger them.
	 */
	private val ttsQueue = linkedMapOf<Long, NotificationInfo>()

	override fun onCreate() {
		ioScope.launch {
			isSuspended.collect {
				if (!it) return@collect
				tts?.run {
					ttsQueueMutex.withLock {
						for (info in ttsQueue.values) {
							info.addIgnoreReasons(IgnoreReason.SUSPENDED)
						}
					}
					stop()
				}
			}
		}
	}

	private suspend fun initTts(onInit: (initSuccess: Boolean) -> Unit)  {
		val isAwaiting = isAwaitingTtsInit.value
		if (tts != null || isAwaiting) {
			if (isAwaiting) try {
				withTimeout(2.seconds) {
					isAwaitingTtsInit.first { !it }
				}
			} catch (e: TimeoutCancellationException) {
				Log.w(TAG, "Timed out waiting for prior TTS init to complete")
			}
			onInit(latestTtsStatus == TextToSpeech.SUCCESS)
			return
		}
		isAwaitingTtsInit.value = true
		tts = TextToSpeech(appContext, OnInitListener { status ->
			latestTtsStatus = status
			isAwaitingTtsInit.value = false
			if (status == TextToSpeech.SUCCESS) {
				onInit(true)
			} else {
				shutdownTts()
				val errorMsg = getString(R.string.error_tts_init, status)
				Log.w(TAG, errorMsg)
				onInit(false)
				CoroutineScope(Dispatchers.Main).launch {
					Toast.makeText(appContext, errorMsg, Toast.LENGTH_LONG).show()
				}
				return@OnInitListener
			}
			tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
				override fun onStart(utteranceId: String) {
					Log.d(TAG, "TTS starting utterance ID $utteranceId")
					speakingUtteranceId = utteranceId.toLong()
				}
				override fun onStop(utteranceId: String, interrupted: Boolean) {
					Log.d(TAG, "Stopped utterance ID $utteranceId - interrupted? $interrupted")
					if (interrupted) {
						ttsQueueMutex.launchWithLock(ioScope) {
							val info = ttsQueue[utteranceId.toLong()]
							if (info != null) {
								info.isInterrupted = true
								if (info.ignoreReasons.isEmpty()) {
									info.addIgnoreReasons(IgnoreReason.TTS_INTERRUPTED)
								}
								NotifyList.updateInfo(info)
							}
							ttsQueue.clear()
						}
					}
					onDone(utteranceId)
				}

				override fun onDone(utteranceId: String) {
					Log.d(TAG, "Completed utterance ID $utteranceId")
					speakingUtteranceId = null
					ioScope.launch {
						ttsQueueMutex.withLock {
							ttsQueue.remove(utteranceId.toLong())
						}
						if (ttsQueue.isEmpty()) onDoneSpeaking()
					}
				}

				@Deprecated("Deprecated in Java")
				override fun onError(utteranceId: String) {
					Log.e(TAG, "Error on utterance ID $utteranceId")
					speakingUtteranceId = null
				}
			})
		})
	}

	private var restartingTts = false

	private suspend fun restartTts() {
		if (restartingTts) return
		restartingTts = true
		Log.d(TAG, "Restarting TTS")
		ttsQueueMutex.withLock {
			ttsQueue.values.forEach {
				if (it.ignoreReasons.contains(IgnoreReason.TTS_FAILED)) return@forEach
				it.addIgnoreReasons(IgnoreReason.TTS_RESTARTED)
				it.isInterrupted = true
				NotifyList.updateInfo(it)
			}
		}
		shutdownTts()
		initTts { initSuccess ->
			ttsQueueMutex.launchWithLock(ioScope) {
				val queueIterator = ttsQueue.iterator()
				queueIterator.forEach {
					val info = it.value
					val isFailed = !initSuccess || tts?.speak(
						info.ttsMessage, TextToSpeech.QUEUE_ADD,
						getTtsParams(info.settings), it.key.toString()
					) != TextToSpeech.SUCCESS
					if (isFailed) {
						Log.e(TAG, "Error adding notification to queue after TTS restart. Not retrying again.")
						info.addIgnoreReasons(IgnoreReason.TTS_FAILED)
						info.isInterrupted = false
						queueIterator.remove()
					} else if (info.ignoreReasons.contains(IgnoreReason.TTS_FAILED)) {
						info.isInterrupted = true
					}
					NotifyList.updateInfo(info)
				}
				Log.d(TAG, "Messages in TTS queue after restart: ${ttsQueue.size}")
				if (ttsQueue.isEmpty()) {
					onDoneSpeaking()
				}
				restartingTts = false
			}
		}
	}

	private fun onDoneSpeaking() {
		if (shouldRequestFocus) {
			Log.d(TAG, "Abandoning audio focus")
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

	private fun shutdownTts() {
		tts?.run {
			tts = null
			latestTtsStatus = TextToSpeech.STOPPED
			isAwaitingTtsInit.value = false
			try {
				shutdown()
			} catch (e: IllegalArgumentException) {
				e.printStackTrace()
			}
		}
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		ioScope.launch {
			val notification = sbn.notification
			val app = Common.findOrAddApp(sbn.packageName)
			val settings = getCombinedSettings(app)
			if (settings.ignoreGroups!!
				&& notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
				return@launch  // Completely ignore group summary notifications.
			}
			val info = NotificationInfo(app, sbn, settings)
			val ttsMsg = info.ttsMessage
			if (app != null && !app.isEnabled) {
				info.addIgnoreReasons(IgnoreReason.APP)
			}
			if (info.isEmpty && settings.ignoreEmpty!!) {
				info.addIgnoreReasons(IgnoreReason.EMPTY_MSG)
			}
			if (ttsMsg != null) {
				fun containsOrMatchesRegex(it: String): Boolean {
					return if (it.startsWith(Constants.REGEX_PREFIX))
						try {
							val pattern = it.removePrefix(Constants.REGEX_PREFIX)
							Regex(pattern).containsMatchIn(ttsMsg)
						} catch (e: PatternSyntaxException) {
							e.printStackTrace()
							false
						}
					else
						ttsMsg.contains(it, true)
				}

				val requireStrings = settings.requireStrings?.split("\n")
				val stringRequired = requireStrings?.all {
					it.isNotEmpty() && !containsOrMatchesRegex(it)
				} ?: false
				if (stringRequired) {
					info.addIgnoreReasons(IgnoreReason.STRING_REQUIRED)
				}
				val ignoreStrings = settings.ignoreStrings?.split("\n")
				val stringIgnored = ignoreStrings?.any {
					it.isNotEmpty() && containsOrMatchesRegex(it)
				} ?: false
				if (stringIgnored) {
					info.addIgnoreReasons(IgnoreReason.STRING_IGNORED)
				}
			}
			val ignoreRepeat = settings.ignoreRepeat?.takeIf { it > -1 }?.run { Duration.ofSeconds(toLong()) }
			if (ignoreRepeat?.isZero != true) {
				notifyListMutex.withLock {
					for (priorInfo in NotifyList.notifyList) {
						val elapsed = Duration.between(priorInfo.instant, info.instant)
						if (ignoreRepeat != null && elapsed >= ignoreRepeat) break
						if (priorInfo.app?.packageName != app?.packageName) continue
						if (priorInfo.ttsMessage == ttsMsg) {
							info.addIgnoreReasonIdentical(ignoreRepeat?.seconds?.toInt() ?: -1)
							break
						}
					}
				}
			}
			NotifyList.addNotification(info)
			if (info.ignoreReasons.isEmpty()) {
				val delay = settings.ttsDelay ?: 0
				if (!isScreenOn()) {
					val interval = settings.ttsRepeat ?: 0.0
					if (interval > 0) {
						repeatListMutex.withLock { repeatList.add(info) }
						startRepeatTimer(interval)
					}
				}
				launch prepSpeak@{
					delay(delay.seconds)
					val ignoreReasons = ignore(info.settings)
					if (ignoreReasons.isNotEmpty()) {
						Log.i(TAG, "Notification ignored for reason(s): ${ignoreReasons.joinToString()}")
						info.addIgnoreReasons(*ignoreReasons.toTypedArray())
						return@prepSpeak
					}
					speak(info)
				}
			} else {
				Log.i(TAG, "Notification from ${app?.label} ignored for reason(s): ${info.getIgnoreReasonsAsText()}")
			}
		}
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {}

	/**
	 * Send a notification to be spoken by TTS.
	 * @param info The info for the notification to be spoken.
	 */
	private suspend fun speak(info: NotificationInfo) {
		if (!isRunning.value) {
			Log.w(TAG, "Speak failed due to service destroyed")
			info.addIgnoreReasons(IgnoreReason.SERVICE_STOPPED)
			NotifyList.updateInfo(info)
			return
		}
		ttsQueueMutex.withLock {
			if (ttsQueue.any { it.key != speakingUtteranceId && it.value == info }) {
				Log.d(TAG, "Notification already waiting in TTS queue, not adding again")
				return
			}
		}
		if ((info.ttsMessage?.length ?: 0) > TextToSpeech.getMaxSpeechInputLength()) {
			info.addIgnoreReasons(IgnoreReason.TTS_LENGTH_LIMIT)
			info.isInterrupted = true
		}
		val utteranceId = ++lastQueuedUtteranceId
		ttsQueueMutex.withLock {
			if (ttsQueue.isEmpty()) { //if there are no messages in the queue, start up shake detection and audio focus requesting
				shake.enable()
				shouldRequestFocus = info.settings.audioFocus!!
				if (shouldRequestFocus) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						audioMan.requestAudioFocus(audioFocusRequest)
					} else {
						@Suppress("DEPRECATION")
						audioMan.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
							AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
					}.let {
						val focusResult = when (it) {
							AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
							AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "failed"
							AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
							else -> " result unknown ($it)"
						}
						Log.d(TAG, "Audio focus request $focusResult")
					}
				}
			}
			//regardless, add the message to the queue, parallelling the TextToSpeech queue since we can't access it.
			ttsQueue.put(utteranceId, info)
		}
		//once the message is in our queue, send it to the real one with the necessary parameters
		Log.d(TAG, "Adding to ttsQueue with utterance ID $utteranceId")
		initTts { initSuccess ->
			ioScope.launch {
				val isSpeakFailed = !initSuccess || tts?.speak(
					info.ttsMessage, TextToSpeech.QUEUE_ADD, getTtsParams(info.settings), utteranceId.toString()
				) != TextToSpeech.SUCCESS
				if (isSpeakFailed) {
					Log.e(TAG, "Error adding notification to TTS queue.")
					info.isInterrupted = false
					if (restartingTts) {
						info.addIgnoreReasons(IgnoreReason.TTS_RESTARTED)
					} else {
						info.addIgnoreReasons(IgnoreReason.TTS_FAILED)
						restartTts()
					}
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
		val ignoreReasons = mutableSetOf<IgnoreReason>()
		if (isSuspended.value) {
			ignoreReasons.add(IgnoreReason.SUSPENDED)
		}
		val c = Calendar.getInstance()
		val calTime = c[Calendar.HOUR_OF_DAY] * 60 + c[Calendar.MINUTE]
		val quietStart = settings.quietStart!!
		val quietEnd = settings.quietEnd!!
		if ((quietStart < quietEnd && quietStart <= calTime && calTime < quietEnd)
			|| (quietEnd < quietStart && (quietStart <= calTime || calTime < quietEnd))
		) ignoreReasons.add(IgnoreReason.QUIET)
		if ((audioMan.ringerMode == AudioManager.RINGER_MODE_SILENT
				|| audioMan.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
			&& !settings.speakSilentOn!!
		) {
			ignoreReasons.add(IgnoreReason.SILENT)
		}
		if (isAudioModeInCall() || (usePhoneState && isPhoneStateInCall())) {
			ignoreReasons.add(IgnoreReason.CALL)
		}
		if (!isScreenOn() && !(settings.speakScreenOff!!)) {
			ignoreReasons.add(IgnoreReason.SCREEN_OFF)
		}
		if (isScreenOn() && !(settings.speakScreenOn!!)) {
			ignoreReasons.add(IgnoreReason.SCREEN_ON)
		}
		if (!isHeadsetOn() && !(settings.speakHeadsetOff!!)) {
			ignoreReasons.add(IgnoreReason.HEADSET_OFF)
		}
		if (isHeadsetOn() && !(settings.speakHeadsetOn!!)) {
			ignoreReasons.add(IgnoreReason.HEADSET_ON)
		}
		return ignoreReasons
	}

	private fun startRepeatTimer(minuteInterval: Double) {
		if (minuteInterval <= 0 || repeaterJob != null) return
		repeaterJob = ioScope.launch {
			while (isActive) {
				delay(minuteInterval.minutes)
				if (isScreenOn()) {
					Log.d(TAG, "Screen is on, canceling repeater")
					cancel()
					repeaterJob = null
				}
				repeatListMutex.withLock {
					for (info in repeatList) {
						if (ignore(info.settings).isNotEmpty()) continue
						speak(info)
					}
				}
			}
		}
	}

	override fun onListenerConnected() {
		Log.i(TAG, "Notification listener connected")
		if (isRunning.value) return
		audioMan = getSystemService(AUDIO_SERVICE) as AudioManager
		if (usePhoneState) {
			telephonyMan = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
			@Suppress("DEPRECATION")
			telephonyMan.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
		} else {
			audioMan.addOnModeChangedListener(Executors.newSingleThreadExecutor(), audioModeListener)
		}
		val filter = IntentFilter().apply {
			addAction(Intent.ACTION_HEADSET_PLUG)
			addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
			addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
			addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
			addAction(Intent.ACTION_SCREEN_ON)
			addAction(Intent.ACTION_SCREEN_OFF)
		}
		registerReceiver(stateReceiver, filter)
		shake.onShake = {
			Log.i(TAG, "TTS silenced by shake")
			ttsQueueMutex.launchWithLock(ioScope) {
				for (info in ttsQueue.values) {
					info.addIgnoreReasons(IgnoreReason.SHAKE)
					if (info == ttsQueue[speakingUtteranceId]) {
						info.isInterrupted = true
					}
					NotifyList.updateInfo(info)
				}
				ttsQueue.clear()
			}
			onDoneSpeaking()
		}
		setInitialized(true)
	}

	override fun onListenerDisconnected() {
		Log.i(TAG, "Notification listener disconnected")
		if (isRunning.value) {
			if (usePhoneState) {
				@Suppress("DEPRECATION")
				telephonyMan.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
			} else {
				audioMan.removeOnModeChangedListener(audioModeListener)
			}
			unregisterReceiver(stateReceiver)
			setInitialized(false)
		}
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

	/**
	 * @param mode The audio mode to check. Defaults to `mode` from [audioMan].
	 * @return `true` if [mode] is in call or in communication.
	 */
	private fun isAudioModeInCall(mode: Int = audioMan.mode) = mode.isAny(
		AudioManager.MODE_IN_CALL,
		AudioManager.MODE_IN_COMMUNICATION
	) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mode.isAny(
		AudioManager.MODE_CALL_REDIRECT,
		AudioManager.MODE_COMMUNICATION_REDIRECT
	))

	/**
	 * @param state The phone state to check.
	 * Defaults to `callState` from [telephonyMan] if `READ_PHONE_STATE` permission is granted, otherwise -1.
	 * @return `true` if [state] is off-hook, `false` if not or if permission is denied for default value.
	 */
	private fun isPhoneStateInCall(
		state: Int = if (isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
			@Suppress("DEPRECATION") telephonyMan.callState
		} else -1
	) = state == TelephonyManager.CALL_STATE_OFFHOOK

	private inner class DeviceStateReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val action = intent.action
			Log.d(TAG, "Received device state: $action")
			var interruptIfIgnored = true
			when (action) {
				Intent.ACTION_SCREEN_ON -> {
					if (!isScreenOn()) return
					repeaterJob?.let {
						repeaterJob = null
						it.cancel()
						repeatListMutex.launchWithLock(ioScope) { repeatList.clear() }
						Log.d(TAG, "Canceled repeater")
					}
					interruptIfIgnored = false
				}
				Intent.ACTION_SCREEN_OFF -> {
					if (isScreenOn()) return
					interruptIfIgnored = false
				}
			}
			if (interruptIfIgnored) {
				processIgnoreForQueue()
			}
		}
	}

	private fun processIgnoreForQueue() {
		tts?.run {
			ttsQueueMutex.launchWithLock(ioScope) {
				for (info in ttsQueue.values) {
					val ignoreReasons = ignore(info.settings)
					if (ignoreReasons.isNotEmpty()) {
						Log.i(TAG, "Notification from ${info.app?.label} silenced/ignored" +
							" for reason(s): ${ignoreReasons.joinToString()}")
						info.addIgnoreReasons(*ignoreReasons.toTypedArray())
					}
				}
			}
			stop()
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
		private val ttsQueueMutex = Mutex()
		private val repeatListMutex = Mutex()
		private val usePhoneState = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
		private val isInitialized = MutableStateFlow(false)
		private var lastQueuedUtteranceId = 0L
		private var speakingUtteranceId: Long? = null
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
			putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, settings.ttsStream!!)
		}

		fun toggleSuspend(): Boolean {
			val suspended = isSuspended.value xor true
			setPref(KEY_IS_SUSPENDED, suspended)
			return suspended
		}
	}
}
