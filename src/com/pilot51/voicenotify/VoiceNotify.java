package com.pilot51.voicenotify;

import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.view.accessibility.AccessibilityEvent;

public class VoiceNotify extends AccessibilityService {
	private static final int
		SPEAK = 1,
		STOP_SPEAK = 2,
		START_TTS = 3,
		STOP_TTS = 4;
	private final StringBuilder mUtterance = new StringBuilder();
	private TextToSpeech mTts;
	private boolean isInfrastructureInitialized;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case SPEAK:
				//Log.d("Test", "Message: " + message.obj);
				mTts.speak((String) message.obj, TextToSpeech.QUEUE_ADD, null);
				return;
			case STOP_SPEAK:
				mTts.stop();
				return;
			case START_TTS:
				mTts = new TextToSpeech(VoiceNotify.this, null);
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
		/*
		Log.i("Test", event.toString());
		Log.d("Test", "ParcelableData: " + event.getParcelableData());
		Log.d("Test", "EventType: " + event.getEventType());
		Log.d("Test", "EventTime: " + event.getEventTime());
		Log.d("Test", "PackageName: " + event.getPackageName());
		Log.d("Test", "BeforeText: " + event.getBeforeText());
		Log.d("Test", "ClassName: " + event.getClassName());
		Log.d("Test", "Text: " + event.getText());
		Log.d("Test", "Label: " + label);
		*/
		mHandler.obtainMessage(SPEAK, formatUtterance(event, label)).sendToTarget();
	}

	private String formatUtterance(AccessibilityEvent event, String label) {
		StringBuilder utterance = mUtterance;
		utterance.delete(0, utterance.length());
		utterance.append(label + ". ");
		List<CharSequence> eventText = event.getText();
		if (!eventText.isEmpty())
			for (CharSequence subText : eventText)
				utterance.append(subText);
		//Log.d("Test", "Utterance: " + utterance.toString());
		return utterance.toString();
	}

	@Override
	public void onInterrupt() {
		mHandler.obtainMessage(STOP_SPEAK);
	}

	@Override
	public void onServiceConnected() {
		if (isInfrastructureInitialized) return;
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
