package com.pilot51.voicenotify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class Service extends AccessibilityService {
	private static final int
		SPEAK = 1,
		STOP_SPEAK = 2,
		START_TTS = 3,
		STOP_TTS = 4;
	private String TAG;
	private TextToSpeech mTts;
	private boolean isInfrastructureInitialized;
	private HashMap<String, String> ttsStream = new HashMap<String, String>();
	private ArrayList<String> ignoredApps;
	private Common common;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case SPEAK:
				if (common.prefs.getString("ttsStream", null).contentEquals("notification"))
					ttsStream.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_NOTIFICATION));
				else ttsStream.clear();
				mTts.speak((String) message.obj, TextToSpeech.QUEUE_ADD, ttsStream);
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
		setServiceInfo(info);
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		PackageManager packMan = getPackageManager();
		ApplicationInfo appInfo = new ApplicationInfo();
		String pkgName = String.valueOf(event.getPackageName());
		try {
			appInfo = packMan.getApplicationInfo(pkgName, 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		ignoredApps = common.readList();
		String label = String.valueOf(appInfo.loadLabel(packMan));
		if (ignoredApps.contains(pkgName)) {
			Log.i(TAG, "Notification event ignored by user preference: " + label);
			return;
		} else if (event.getText().isEmpty()) {
			Log.i(TAG, "Notification event ignored due to empty message: " + label);
			return;
		}
		mHandler.obtainMessage(SPEAK, formatUtterance(event, label)).sendToTarget();
	}

	private String formatUtterance(AccessibilityEvent event, String label) {
		List<CharSequence> eventText = event.getText();
		StringBuilder mesg = new StringBuilder();
		if (!eventText.isEmpty())
			for (CharSequence subText : eventText)
				mesg.append(subText);
		String s = common.prefs.getString("ttsString", null);
		try {
			return String.format(s.replace("%t", "%1$s").replace("%m", "%2$s"), label, mesg);
		} catch(IllegalFormatException e) {
			Log.w(TAG, "Error formatting custom TTS string");
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
		TAG = common.TAG;
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
