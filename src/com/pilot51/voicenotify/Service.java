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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Timer;
import java.util.TimerTask;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class Service extends AccessibilityService {
	private String lastMsg = "";
	private static final int
		SPEAK = 1,
		STOP_SPEAK = 2,
		START_TTS = 3,
		STOP_TTS = 4;
	private long lastMsgTime;
	private TextToSpeech mTts;
	private AudioManager audioMan;
	private TelephonyManager telephony;
	private HeadsetReceiver headsetReceiver = new HeadsetReceiver();
	private RepeatTimer repeater;
	private Shake shake;
	private boolean isInitialized, isScreenOn, isHeadsetPlugged, isBluetoothConnected;
	private HashMap<String, String> ttsParams = new HashMap<String, String>();
	private ArrayList<String> ignoreReasons = new ArrayList<String>(),
			repeatList = new ArrayList<String>();
	private String lastQueueTime;
	private Handler ttsHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case SPEAK:
				shake.enable();
				ttsParams.clear();
				boolean isNotificationStream = Common.prefs.getString("ttsStream", null).contentEquals("notification");
				if (isNotificationStream) {
					ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_NOTIFICATION));
				}
				lastQueueTime = Long.toString(System.currentTimeMillis());
				ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, lastQueueTime);
				mTts.speak((String)message.obj, TextToSpeech.QUEUE_ADD, ttsParams);
				break;
			case STOP_SPEAK:
				mTts.stop();
				break;
			case START_TTS:
				mTts = new TextToSpeech(Service.this, new OnInitListener() {
					@Override
					public void onInit(int status) {
						mTts.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
							@Override
							public void onUtteranceCompleted(String utteranceId) {
								if (utteranceId.equals(lastQueueTime)) {
									shake.disable();
								}
							}
						});
					}
				});
				break;
			case STOP_TTS:
				mTts.shutdown();
				break;
			}
		}
	};
	
	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		if (!Common.prefs.getBoolean("toasts", false) && !(event.getParcelableData() instanceof Notification)) {
			return;
		}
		long newMsgTime = System.currentTimeMillis();
		PackageManager packMan = getPackageManager();
		ApplicationInfo appInfo = new ApplicationInfo();
		String pkgName = String.valueOf(event.getPackageName());
		try {
			appInfo = packMan.getApplicationInfo(pkgName, 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		StringBuilder notifyMsg = new StringBuilder();
		if (!event.getText().isEmpty()) {
			for (CharSequence subText : event.getText()) {
				notifyMsg.append(subText);
			}
		}
		final String label = String.valueOf(appInfo.loadLabel(packMan)),
			ttsStringPref = Common.prefs.getString("ttsString", null);
		NotifyList.addNotification(label, notifyMsg.toString());
		String newMsg;
		try {
			newMsg = String.format(ttsStringPref.replace("%t", "%1$s").replace("%m", "%2$s"), label, notifyMsg.toString().replaceAll("[\\|\\[\\]\\{\\}\\*<>]+", " "));
		} catch(IllegalFormatException e) {
			Log.w(Common.TAG, "Error formatting custom TTS string!");
			e.printStackTrace();
			newMsg = ttsStringPref;
		}
		final String[] ignoreStrings = Common.prefs.getString("ignore_strings", "").toLowerCase().split("\n");
		boolean stringIgnored = false;
		if (ignoreStrings != null) {
			for (String s : ignoreStrings) {
				if (s.length() != 0 && notifyMsg.toString().toLowerCase().contains(s)) {
					stringIgnored = true;
					break;
				}
			}
		}
		if (!AppList.getIsEnabled(pkgName)) {
			ignoreReasons.add(getString(R.string.reason_app));
		}
		if (stringIgnored) {
			ignoreReasons.add(getString(R.string.reason_string));
		}
		if (event.getText().isEmpty()) {
			ignoreReasons.add(getString(R.string.reason_empty_msg));
		}
		int ignoreRepeat;
		try {
			ignoreRepeat = Integer.parseInt(Common.prefs.getString("ignore_repeat", null));
		} catch (NumberFormatException e) {
			ignoreRepeat = -1;
		}
		if (lastMsg.contentEquals(newMsg) && (ignoreRepeat == -1 || newMsgTime - lastMsgTime < ignoreRepeat * 1000)) {
			ignoreReasons.add(MessageFormat.format(getString(R.string.reason_identical), ignoreRepeat));
		}
		if (ignoreReasons.isEmpty()) {
			int delay = 0;
			try {
				delay = Integer.parseInt(Common.prefs.getString("ttsDelay", null));
			} catch (NumberFormatException e) {}
			if (!isScreenOn()) {
				int interval;
				try {
					interval = Integer.parseInt(Common.prefs.getString("tts_repeat", "0"));
				} catch (NumberFormatException e) {
					interval = 0;
				}
				if (interval > 0) {
					repeatList.add(newMsg);
					if (repeater == null)
						repeater = new RepeatTimer(interval);
					else repeater.checkInterval(interval);
				}
			}
			if (delay > 0) {
				final String msg = newMsg;
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						speak(msg, true);
					}
				}, delay * 1000);
			} else speak(newMsg, true);
		} else {
			String reasons = ignoreReasons.toString().replaceAll("\\[|\\]", "");
			Log.i(Common.TAG, "Notification from " + label + " ignored for reason(s): " + reasons);
			NotifyList.setLastIgnore(reasons, true);
			ignoreReasons.clear();
		}
		lastMsg = newMsg;
		lastMsgTime = newMsgTime;
	}
	
	/**
	 * Sends msg to TTS if ignore condition is not met.
	 * @param msg The string to be spoken.
	 * @param isNew True if notification is new, otherwise false if it is being repeated.
	 */
	private void speak(String msg, boolean isNew) {
		if (ignore(isNew)) return;
		ttsHandler.obtainMessage(SPEAK, msg).sendToTarget();
	}
	
	/**
	 * Checks for any notification-independent ignore states.
	 * @param isNew True if notification is new, otherwise false if it is being repeated.
	 * @returns True if an ignore condition is met, false otherwise.
	 */
	private boolean ignore(boolean isNew) {
		Calendar c = Calendar.getInstance();
		int calTime = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
			quietStart = Common.prefs.getInt("quietStart", 0),
			quietEnd = Common.prefs.getInt("quietEnd", 0);
		if ((quietStart < quietEnd & quietStart <= calTime & calTime < quietEnd)
				| (quietEnd < quietStart & (quietStart <= calTime | calTime < quietEnd))) {
			ignoreReasons.add(getString(R.string.reason_quiet));
		}
		if ((audioMan.getRingerMode() == AudioManager.RINGER_MODE_SILENT
				|| audioMan.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)
				&& !Common.prefs.getBoolean(Common.SPEAK_SILENT_ON, false)) {
			ignoreReasons.add(getString(R.string.reason_silent));
		}
		if (telephony.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK
				| telephony.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
			ignoreReasons.add(getString(R.string.reason_call));
		}
		if (!isScreenOn() & !Common.prefs.getBoolean(Common.SPEAK_SCREEN_OFF, true)) {
			ignoreReasons.add(getString(R.string.reason_screen_off));
		}
		if (isScreenOn() & !Common.prefs.getBoolean(Common.SPEAK_SCREEN_ON, true)) {
			ignoreReasons.add(getString(R.string.reason_screen_on));
		}
		if (!(isHeadsetPlugged | isBluetoothConnected) & !Common.prefs.getBoolean(Common.SPEAK_HEADSET_OFF, true)) {
			ignoreReasons.add(getString(R.string.reason_headset_off));
		}
		if ((isHeadsetPlugged | isBluetoothConnected) & !Common.prefs.getBoolean(Common.SPEAK_HEADSET_ON, true)) {
			ignoreReasons.add(getString(R.string.reason_headset_on));
		}
		if (!ignoreReasons.isEmpty()) {
			String reasons = ignoreReasons.toString().replaceAll("\\[|\\]", "");
			Log.i(Common.TAG, "Notification ignored for reason(s): " + reasons);
			NotifyList.setLastIgnore(reasons, isNew);
			ignoreReasons.clear();
			return true;
		}
		return false;
	}
	
	private class RepeatTimer extends TimerTask {
		private int interval;
		private RepeatTimer(int interval) {
			this.interval = interval;
			if (interval <= 0) return;
			new Timer().schedule(this, interval * 60000, interval * 60000);
		}
		
		/**
		 * If passed interval is different from current timer interval,
		 *  cancels current timer and, if interval > 0, creates new instance with passed interval.
		 * @param interval The interval to check against.
		 */
		private void checkInterval(int interval) {
			if (this.interval != interval) {
				cancel();
				if (interval > 0) repeater = new RepeatTimer(interval);
			}
		}
		
		@Override
		public void run() {
			if (isScreenOn()) {
				repeatList.clear();
				cancel();
			}
			for (String s : repeatList) {
				speak(s, false);
			}
		}
		
		@Override
		public boolean cancel() {
			repeater = null;
			return super.cancel();
		}
	}
	
	@Override
	public void onInterrupt() {
		ttsHandler.sendEmptyMessage(STOP_SPEAK);
	}
	
	@Override
	public void onServiceConnected() {
		if (isInitialized) return;
		new Common(this);
		ttsHandler.sendEmptyMessage(START_TTS);
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
		setServiceInfo(info);
		audioMan = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		telephony = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		IntentFilter filter =  new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(headsetReceiver, filter);
		shake = new Shake(this);
		shake.setOnShakeListener(new Shake.OnShakeListener() {
			@Override
			public void onShake() {
				Log.i(Common.TAG, "TTS silenced by shake");
				NotifyList.setLastIgnore(getString(R.string.reason_shake), false);
				ttsHandler.sendEmptyMessage(STOP_SPEAK);
			}
		});
		isInitialized = true;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		if (isInitialized) {
			ttsHandler.sendEmptyMessage(STOP_TTS);
			unregisterReceiver(headsetReceiver);
			isInitialized = false;
		}
		return false;
	}
	
	private boolean isScreenOn() {
		if (android.os.Build.VERSION.SDK_INT >= 7) {
			isScreenOn = CheckScreen.isScreenOn(this);
		}
		return isScreenOn;
	}
	
	private static class CheckScreen {
		private static PowerManager powerMan;
		private static boolean isScreenOn(Context c) {
			if (powerMan == null) {
				powerMan = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
			}
			return powerMan.isScreenOn();
		}
	}
	
	private class HeadsetReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
			} else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
				isBluetoothConnected = true;
			} else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
				isBluetoothConnected = false;
			} else if (action.equals(Intent.ACTION_SCREEN_ON)) {
				isScreenOn = true;
			} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
				isScreenOn = false;
			}
			if (mTts.isSpeaking() && ignore(false)) {
				ttsHandler.sendEmptyMessage(STOP_SPEAK);
			}
		}
	}
}
