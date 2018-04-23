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

package com.pilot51.voicenotify;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class Service extends NotificationListenerService {
	private static final String TAG = Service.class.getSimpleName();
	private SharedPreferences prefs;
	private final Map<App, String> lastMsg = new HashMap<>();
	private final Map<App, Long> lastMsgTime = new HashMap<>();
	private TextToSpeech mTts;
	private boolean shouldRequestFocus;
	private AudioManager audioMan;
	private TelephonyManager telephony;
	private final DeviceStateReceiver stateReceiver = new DeviceStateReceiver();
	private RepeatTimer repeater;
	private Shake shake;
	private static boolean isInitialized, isSuspended, isScreenOn;
	private final List<NotificationInfo> repeatList = new ArrayList<>();
	
	/**
	 * this is used to determine if we are the first, middle, or last thing to be spoken at the moment, for enabling/disabling shake and audio focus request
	 * entries are added right before the call to speak and removed by onDone in the utteranceProgressListener
	 * if the list is empty when we enqueue a message, we trigger shaking and audio focus requesting
	 * if the list is empty when we finish speaking a message, we untrigger them.
	 */
	@SuppressLint("UseSparseArrays")
	private final Map<Long, NotificationInfo> ttsQueue = new HashMap<>();
	private final Handler handler = new Handler();
	private final OnStatusChangeListener statusListener = new OnStatusChangeListener() {
		@Override
		public void onStatusChanged() {
			if (isSuspended && mTts != null) {
				synchronized (ttsQueue) {
					for (NotificationInfo info : ttsQueue.values()) {
						info.addIgnoreReason(IgnoreReason.SUSPENDED);
					}
				}
				mTts.stop();
			}
			sendBroadcast(new Intent(getApplicationContext(), WidgetProvider.class)
					.setAction(WidgetProvider.ACTION_UPDATE));
		}
	};
	
	@Override
	public void onCreate() {
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				if (status == TextToSpeech.ERROR) {
					Log.w(TAG, getString(R.string.error_tts_init));
					Toast.makeText(getApplicationContext(), R.string.error_tts_init, Toast.LENGTH_LONG).show();
					return;
				}
				mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
					@Override
					public void onStart(String utteranceId) {
					}
					
					@Override
					public void onStop(String utteranceId, boolean interrupted) {
						if (interrupted) {
							NotificationInfo info = ttsQueue.get(Long.parseLong(utteranceId));
							if (info != null) {
								info.setSilenced();
								NotifyList.refresh();
							}
							ttsQueue.clear();
						}
						onDone(utteranceId);
					}
					
					@Override
					public void onDone(String utteranceId) {
						synchronized (ttsQueue) {
							ttsQueue.remove(Long.parseLong(utteranceId));
						}
						if (ttsQueue.isEmpty()) {
							if (shouldRequestFocus) {
								audioMan.abandonAudioFocus(null);
							}
							shake.disable();
						}
					}
					
					@Override
					public void onError(String utteranceId) {
						Log.e(TAG, "Utterance error");
					}
				});
			}
		});
		super.onCreate();
	}
	
	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		Notification notification = sbn.getNotification();
		if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
			return; // Completely ignore group summary notifications.
		}
		App app = AppListActivity.findOrAddApp(sbn.getPackageName(), this);
		final NotificationInfo info = new NotificationInfo(app, notification, getApplicationContext());
		long msgTime = info.getCalendar().getTimeInMillis();
		final String ttsMsg = info.getTtsMessage();
		final String[] ignoreStrings = prefs.getString(getString(R.string.key_ignore_strings), "").toLowerCase().split("\n");
		boolean stringIgnored = false;
		for (String s : ignoreStrings) {
			if (s.length() != 0 && ttsMsg != null && ttsMsg.toLowerCase().contains(s)) {
				stringIgnored = true;
				break;
			}
		}
		if (app != null && !app.getEnabled()) {
			info.addIgnoreReason(IgnoreReason.APP);
		}
		if (stringIgnored) {
			info.addIgnoreReason(IgnoreReason.STRING);
		}
		if (TextUtils.isEmpty(ttsMsg)
				&& prefs.getBoolean(getString(R.string.key_ignore_empty), false)) {
			info.addIgnoreReason(IgnoreReason.EMPTY_MSG);
		}
		int ignoreRepeat = -1;
		String ignoreRepeatStr = prefs.getString(getString(R.string.key_ignore_repeat), null);
		if (!TextUtils.isEmpty(ignoreRepeatStr)) {
			ignoreRepeat = Integer.parseInt(ignoreRepeatStr);
		}
		if (lastMsg.containsKey(app)) {
			if (lastMsg.get(app).equals(ttsMsg) && (ignoreRepeat == -1 || msgTime - lastMsgTime.get(app) < ignoreRepeat * 1000)) {
				info.addIgnoreReasonIdentical(ignoreRepeat);
			}
		}
		NotifyList.addNotification(info);
		if (info.getIgnoreReasons().isEmpty()) {
			int delay = 0;
			String delayStr = prefs.getString(getString(R.string.key_ttsDelay), null);
			if (!TextUtils.isEmpty(delayStr)) {
				delay = Integer.parseInt(delayStr);
			}
			if (!isScreenOn()) {
				int interval = 0;
				String intervalStr = prefs.getString(getString(R.string.key_tts_repeat), null);
				if (!TextUtils.isEmpty(intervalStr)) {
					interval = Integer.parseInt(intervalStr);
				}
				if (interval > 0) {
					synchronized (repeatList) {
						repeatList.add(info);
					}
					if (repeater == null) {
						repeater = new RepeatTimer(interval);
					}
				}
			}
			if (delay < 0) { // Just in case we get a weird value, don't want to try to make the Timer wait for negative time
				delay = 0;
			}
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					Set<IgnoreReason> ignoreReasons = ignore();
					if (!ignoreReasons.isEmpty()) {
						Log.i(TAG, "Notification ignored for reason(s): "
								+ IgnoreReason.convertSetToString(ignoreReasons, Service.this));
						info.addIgnoreReasons(ignoreReasons);
						return;
					}
					handler.post(new Runnable() {
						@Override
						public void run() {
							speak(info);
						}
					});
				}
			}, delay * 1000L); // A delay of 0 works fine, and means that all speak calls anywhere are running in their own thread and not blocking.
			lastMsg.put(app, ttsMsg);
			lastMsgTime.put(app, msgTime);
		} else {
			Log.i(TAG, "Notification from " + (app != null ? app.getLabel() : null)
					+ " ignored for reason(s): " + info.getIgnoreReasonsAsText(this));
		}
	}
	
	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
	}
	
	/**
	 * Send a notification to be spoken by TTS.
	 * @param info The info for the notification to be spoken.
	 */
	private void speak(final NotificationInfo info) {
		if (mTts == null) {
			Log.w(TAG, "Speak failed due to service destroyed");
			info.addIgnoreReason(IgnoreReason.SERVICE_STOPPED);
			NotifyList.refresh();
			return;
		}
		long notificationTime = info.getCalendar().getTimeInMillis();
		synchronized (ttsQueue) {
			if (ttsQueue.isEmpty()) { //if there are no messages in the queue, start up shake detection and audio focus requesting
				shake.enable();
				shouldRequestFocus = Common.getPrefs(getApplicationContext())
						.getBoolean(getString(R.string.key_audio_focus), false);
				if (shouldRequestFocus) {
					audioMan.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
							AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
				}
			}
			//regardless, add the message to the queue, parallelling the TextToSpeech queue since we can't access it.
			ttsQueue.put(notificationTime, info);
		}
		//once the message is in our queue, send it to the real one with the necessary parameters
		final HashMap<String, String> ttsParams = new HashMap<>();
		//needed because we want to apply stream changes immediately
		ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
				Integer.toString(Common.getSelectedAudioStream(getApplicationContext())));
		//not used anywhere, but has to be set to get the UtteranceProgressListener to trigger its submethods
		ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, Long.toString(notificationTime));
		if (mTts.speak(info.getTtsMessage(), TextToSpeech.QUEUE_ADD, ttsParams) != TextToSpeech.SUCCESS) {
			Log.e(TAG, "Error adding notification to TTS queue");
			synchronized (ttsQueue) {
				ttsQueue.remove(notificationTime);
			}
		}
	}
	
	/**
	 * Checks for any notification-independent ignore states.
	 * @return Set of ignore reasons.
	 */
	private Set<IgnoreReason> ignore() {
		Set<IgnoreReason> ignoreReasons = new HashSet<>();
		if (isSuspended) {
			ignoreReasons.add(IgnoreReason.SUSPENDED);
		}
		Calendar c = Calendar.getInstance();
		int calTime = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
		int quietStart = prefs.getInt(getString(R.string.key_quietStart), 0);
		int quietEnd = prefs.getInt(getString(R.string.key_quietEnd), 0);
		if ((quietStart < quietEnd & quietStart <= calTime & calTime < quietEnd)
				|| (quietEnd < quietStart && (quietStart <= calTime || calTime < quietEnd))) {
			ignoreReasons.add(IgnoreReason.QUIET);
		}
		if ((audioMan.getRingerMode() == AudioManager.RINGER_MODE_SILENT
				|| audioMan.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)
				&& !prefs.getBoolean(Common.KEY_SPEAK_SILENT_ON, false)) {
			ignoreReasons.add(IgnoreReason.SILENT);
		}
		if (telephony.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
			ignoreReasons.add(IgnoreReason.CALL);
		}
		if (!isScreenOn() && !prefs.getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true)) {
			ignoreReasons.add(IgnoreReason.SCREEN_OFF);
		}
		if (isScreenOn() && !prefs.getBoolean(Common.KEY_SPEAK_SCREEN_ON, true)) {
			ignoreReasons.add(IgnoreReason.SCREEN_ON);
		}
		if (!isHeadsetOn() && !prefs.getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true)) {
			ignoreReasons.add(IgnoreReason.HEADSET_OFF);
		}
		if (isHeadsetOn() && !prefs.getBoolean(Common.KEY_SPEAK_HEADSET_ON, true)) {
			ignoreReasons.add(IgnoreReason.HEADSET_ON);
		}
		return ignoreReasons;
	}
	
	private class RepeatTimer extends TimerTask {
		private RepeatTimer(int minuteInterval) {
			if (minuteInterval <= 0) return;
			long interval = minuteInterval * 60000L;
			new Timer().schedule(this, interval, interval);
		}
		
		@Override
		public void run() {
			if (!ignore().isEmpty()) return;
			handler.post(new Runnable() {
				@Override
				public void run() {
					synchronized (repeatList) {
						for (NotificationInfo info : repeatList) {
							speak(info);
						}
					}
				}
			});
		}
		
		@Override
		public boolean cancel() {
			repeater = null;
			return super.cancel();
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		if (isInitialized) return super.onBind(intent);
		Common.init(this);
		audioMan = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		telephony = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		registerReceiver(stateReceiver, filter);
		shake = new Shake(this);
		shake.setOnShakeListener(new Shake.OnShakeListener() {
			@Override
			public void onShake() {
				Log.i(TAG, "TTS silenced by shake");
				synchronized (ttsQueue) {
					for (NotificationInfo info : ttsQueue.values()) {
						info.addIgnoreReason(IgnoreReason.SHAKE);
					}
				}
				if (mTts != null) mTts.stop();
				NotifyList.refresh();
			}
		});
		registerOnStatusChangeListener(statusListener);
		setInitialized(true);
		return super.onBind(intent);
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		if (isInitialized) {
			if (mTts != null) mTts.stop();
			unregisterReceiver(stateReceiver);
			setInitialized(false);
			unregisterOnStatusChangeListener(statusListener);
		}
		return false;
	}
	
	@Override
	public void onDestroy() {
		if (mTts != null) {
			mTts.shutdown();
			mTts = null;
		}
		super.onDestroy();
	}
	
	private static final List<OnStatusChangeListener> statusListeners = new ArrayList<>();
	
	static void registerOnStatusChangeListener(OnStatusChangeListener listener) {
		statusListeners.add(listener);
	}
	
	static void unregisterOnStatusChangeListener(OnStatusChangeListener listener) {
		statusListeners.remove(listener);
	}
	
	interface OnStatusChangeListener {
		/**
		 * Called when the service status has changed.
		 * @see Service#isRunning()
		 * @see Service#isSuspended()
		 */
		void onStatusChanged();
	}
	
	private static void onStatusChanged() {
		for (OnStatusChangeListener l : statusListeners) {
			l.onStatusChanged();
		}
	}
	
	static boolean isRunning() {
		return isInitialized;
	}
	
	private void setInitialized(boolean initialized) {
		isInitialized = initialized;
		onStatusChanged();
	}
	
	static boolean isSuspended() {
		return isSuspended;
	}
	
	static boolean toggleSuspend() {
		isSuspended ^= true;
		onStatusChanged();
		return isSuspended;
	}

	static boolean toggleSuspend(boolean status) {
		isSuspended = status;
		onStatusChanged();
		return isSuspended;
	}

	private boolean isScreenOn() {
		isScreenOn = CheckScreen.isScreenOn(this);
		return isScreenOn;
	}
	
	private boolean isHeadsetOn() {
		return (audioMan.isBluetoothA2dpOn() || audioMan.isWiredHeadsetOn());
	}
	
	private static class CheckScreen {
		private static PowerManager powerMan;
		
		private static boolean isScreenOn(Context c) {
			if (powerMan == null) {
				powerMan = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
			}
			assert powerMan != null; // Prevent Lint warning. Should never be null, I want a crash report if it is.
			return powerMan.isScreenOn();
		}
	}
	
	private class DeviceStateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			boolean interruptIfIgnored = true;
			assert action != null; // Prevent Lint warning. Should never be null, I want a crash report if it is.
			switch (action) {
				case Intent.ACTION_SCREEN_ON:
					isScreenOn = true;
					if (repeater != null) {
						repeater.cancel();
						synchronized (repeatList) {
							repeatList.clear();
						}
					}
					interruptIfIgnored = false;
					break;
				case Intent.ACTION_SCREEN_OFF:
					isScreenOn = false;
					interruptIfIgnored = false;
					break;
			}
			if (interruptIfIgnored && mTts != null) {
				Set<IgnoreReason> ignoreReasons = ignore();
				if (!ignoreReasons.isEmpty()) {
					Log.i(TAG, "Notifications silenced/ignored for reason(s): "
							+ IgnoreReason.convertSetToString(ignoreReasons, context));
					synchronized (ttsQueue) {
						for (NotificationInfo info : ttsQueue.values()) {
							info.addIgnoreReasons(ignoreReasons);
						}
					}
					mTts.stop();
				}
			}
		}
	}
}
