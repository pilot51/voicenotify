package com.pilot51.voicenotify;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class Service extends AccessibilityService {
	private Common common;
	private String lastMsg = "";
	private static final int
		SPEAK = 1,
		STOP_SPEAK = 2,
		START_TTS = 3,
		STOP_TTS = 4;
	private long lastMsgTime;
	private TextToSpeech mTts;
	private PowerManager powerMan;
	private AudioManager audioMan;
	private TelephonyManager telephony;
	private boolean isInfrastructureInitialized;
	private HashMap<String, String> ttsParams = new HashMap<String, String>();
	private ArrayList<String> ignoredApps;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case SPEAK:
				boolean isNotificationStream = Common.prefs.getString("ttsStream", null).contentEquals("notification");
				if (isNotificationStream)
					ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_NOTIFICATION));
				mTts.speak((String)message.obj, TextToSpeech.QUEUE_ADD, ttsParams);
				if (isNotificationStream) {
					ttsParams.clear();
					mTts.speak(" ", TextToSpeech.QUEUE_ADD, null);
				}
				break;
			case STOP_SPEAK:
				mTts.stop();
				break;
			case START_TTS:
				mTts = new TextToSpeech(Service.this, null);
				break;
			case STOP_TTS:
				mTts.shutdown();
				break;
			}
		}
	};

	private void setServiceInfo(int feedbackType) {
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
		info.feedbackType = feedbackType;
		setServiceInfo(info);
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		Calendar c = Calendar.getInstance();
		int calTime = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
			quietStart = Common.prefs.getInt("quietStart", 0),
			quietEnd = Common.prefs.getInt("quietEnd", 0);
		if ((quietStart < quietEnd & quietStart <= calTime & calTime < quietEnd)
				| (quietEnd < quietStart & (quietStart <= calTime | calTime < quietEnd))) {
			Log.i(Common.TAG, "Notification ignored by quiet time");
			return;
		} else if (audioMan.getRingerMode() == AudioManager.RINGER_MODE_SILENT
				| audioMan.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
			Log.i(Common.TAG, "Notification ignored due to silent or vibrate mode");
			return;
		} else if (telephony.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK
				| telephony.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
			Log.i(Common.TAG, "Notification ignored due to active or ringing call");
			return;
		} else if (!powerMan.isScreenOn() & !Common.prefs.getBoolean("speakScreenOff", true)) {
			Log.i(Common.TAG, "Notification ignored due to screen off (user preference)");
			return;
		} else if (powerMan.isScreenOn() & !Common.prefs.getBoolean("speakScreenOn", true)) {
			Log.i(Common.TAG, "Notification ignored due to screen on (user preference)");
			return;
		}
		PackageManager packMan = getPackageManager();
		ApplicationInfo appInfo = new ApplicationInfo();
		String pkgName = String.valueOf(event.getPackageName());
		try {
			appInfo = packMan.getApplicationInfo(pkgName, 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		ignoredApps = common.readList();
		final String label = String.valueOf(appInfo.loadLabel(packMan)),
			newMsg = formatUtterance(event, label);
		long newMsgTime = System.currentTimeMillis();
		if (ignoredApps.contains(pkgName)) {
			Log.i(Common.TAG, "Notification ignored by user preference: " + label);
		} else if (event.getText().isEmpty()) {
			Log.i(Common.TAG, "Notification ignored due to empty message: " + label);
		} else if (lastMsg.contentEquals(newMsg) & newMsgTime - lastMsgTime < 10000) {
			Log.i(Common.TAG, "Notification ignored due to identical message within 10 seconds: " + label);
		} else {
			int delay = 0;
			try {
				delay = Integer.parseInt(Common.prefs.getString("ttsDelay", null));
			} catch (NumberFormatException e) {}
			if (delay > 0) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						mHandler.obtainMessage(SPEAK, newMsg).sendToTarget();
					}
				}, delay * 1000);
			} else mHandler.obtainMessage(SPEAK, newMsg).sendToTarget();
		}
		lastMsg = newMsg;
		lastMsgTime = newMsgTime;
	}

	private String formatUtterance(AccessibilityEvent event, String label) {
		List<CharSequence> eventText = event.getText();
		StringBuilder mesg = new StringBuilder();
		if (!eventText.isEmpty())
			for (CharSequence subText : eventText)
				mesg.append(subText);
		String s = Common.prefs.getString("ttsString", null);
		try {
			return String.format(s.replace("%t", "%1$s").replace("%m", "%2$s"), label, mesg.toString().replaceAll("[\\|\\[\\]\\{\\}\\*<>]+", " "));
		} catch(IllegalFormatException e) {
			Log.w(Common.TAG, "Error formatting custom TTS string");
			e.printStackTrace();
			return s;
		}
	}

	@Override
	public void onInterrupt() {
		mHandler.obtainMessage(STOP_SPEAK);
	}

	@Override
	public void onServiceConnected() {
		if (isInfrastructureInitialized) return;
		common = new Common(this);
		mHandler.sendEmptyMessage(START_TTS);
		setServiceInfo(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
		powerMan = (PowerManager)getSystemService(Context.POWER_SERVICE);
		audioMan = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		telephony = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		isInfrastructureInitialized = true;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		if (isInfrastructureInitialized) {
			mHandler.sendEmptyMessage(STOP_TTS);
			isInfrastructureInitialized = false;
		}
		return false;
	}
}
