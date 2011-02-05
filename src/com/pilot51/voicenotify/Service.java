package com.pilot51.voicenotify;

import java.util.Arrays;
import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.view.accessibility.AccessibilityEvent;

public class Service extends AccessibilityService {
	private static final int
		SPEAK = 1,
		STOP_SPEAK = 2,
		START_TTS = 3,
		STOP_TTS = 4;
	private TextToSpeech mTts;
	private boolean isInfrastructureInitialized;
	private SharedPreferences prefs;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case SPEAK:
				//Log.d("VoiceNotify", "Message: " + message.obj);
				mTts.speak((String) message.obj, TextToSpeech.QUEUE_ADD, null);
				return;
			case STOP_SPEAK:
				mTts.stop();
				return;
			case START_TTS:
				mTts = new TextToSpeech(Service.this, null);
				return;
			case STOP_TTS:
				mTts.shutdown();
				return;
			}
		}
	};

	private void setServiceInfo(int feedbackType) {
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
		info.feedbackType = feedbackType;
		//info.notificationTimeout = 0;
		//info.packageNames = new String[] {"com.pilot51.predisat", "com.pilot51.lclock"};
		setServiceInfo(info);
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		PackageManager packMan = getPackageManager();
		PackageInfo packInfo = new PackageInfo();
		try {
			packInfo = packMan.getPackageInfo(String.valueOf(event.getPackageName()), 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		String label = String.valueOf(packMan.getApplicationLabel(packInfo.applicationInfo));
		String[] ignoredApps = prefs.getString("ignoredApps", "").toLowerCase().split(";");
		for (int i = 0, length = ignoredApps.length; i < length; i++) {
			if (ignoredApps[i] != null) ignoredApps[i] = ignoredApps[i].trim();
		}
		if (Arrays.asList(ignoredApps).contains(label.toLowerCase())) return;
		/*
		Log.i("VoiceNotify", event.toString());
		Log.d("VoiceNotify", "ParcelableData: " + event.getParcelableData());
		Log.d("VoiceNotify", "EventType: " + event.getEventType());
		Log.d("VoiceNotify", "EventTime: " + event.getEventTime());
		Log.d("VoiceNotify", "PackageName: " + event.getPackageName());
		Log.d("VoiceNotify", "BeforeText: " + event.getBeforeText());
		Log.d("VoiceNotify", "ClassName: " + event.getClassName());
		Log.d("VoiceNotify", "Text: " + event.getText());
		Log.d("VoiceNotify", "Label: " + label);
		*/
		mHandler.obtainMessage(SPEAK, formatUtterance(event, label)).sendToTarget();
	}

	private String formatUtterance(AccessibilityEvent event, String label) {
		List<CharSequence> eventText = event.getText();
		StringBuilder mesg = new StringBuilder();
		if (!eventText.isEmpty())
			for (CharSequence subText : eventText)
				mesg.append(subText);
		String s = prefs.getString("ttsString", null);
		s = s.replaceAll("%t", "%1$s").replaceAll("%m", "%2$s");
		return String.format(s, label, mesg);
	}

	@Override
	public void onInterrupt() {
		mHandler.obtainMessage(STOP_SPEAK);
	}

	@Override
	public void onServiceConnected() {
		if (isInfrastructureInitialized) return;
		PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mHandler.sendEmptyMessage(START_TTS);
		setServiceInfo(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
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
