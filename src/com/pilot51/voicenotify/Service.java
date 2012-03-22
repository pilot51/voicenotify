package com.pilot51.voicenotify;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Timer;
import java.util.TimerTask;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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
	private AudioManager audioMan;
	private TelephonyManager telephony;
	private HeadsetReceiver headsetReceiver = new HeadsetReceiver();
	private boolean isInitialized, isScreenOn, isHeadsetPlugged, isBluetoothConnected;
	private HashMap<String, String> ttsParams = new HashMap<String, String>();
	private ArrayList<String> ignoredApps, ignoreReasons = new ArrayList<String>();

	private Handler ttsHandler = new Handler() {
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
		long newMsgTime = System.currentTimeMillis();
		PackageManager packMan = getPackageManager();
		ApplicationInfo appInfo = new ApplicationInfo();
		String pkgName = String.valueOf(event.getPackageName());
		try {
			appInfo = packMan.getApplicationInfo(pkgName, 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		ignoredApps = common.readList();
		StringBuilder notifyMsg = new StringBuilder();
		if (!event.getText().isEmpty())
			for (CharSequence subText : event.getText())
				notifyMsg.append(subText);
		final String label = String.valueOf(appInfo.loadLabel(packMan)),
			ttsStringPref = Common.prefs.getString("ttsString", null);
		String newMsg;
		try {
			newMsg = String.format(ttsStringPref.replace("%t", "%1$s").replace("%m", "%2$s"), label, notifyMsg.toString().replaceAll("[\\|\\[\\]\\{\\}\\*<>]+", " "));
		} catch(IllegalFormatException e) {
			Log.w(Common.TAG, "Error formatting custom TTS string!");
			e.printStackTrace();
			newMsg = ttsStringPref;
		}
		final String[] ignoreStrings = Common.prefs.getString("ignore_strings", null).split("\n");
		boolean stringIgnored = false;
		if (ignoreStrings != null) {
			for (int i = 0; i < ignoreStrings.length; i++) {
				if (ignoreStrings[i].length() != 0 && notifyMsg.toString().toLowerCase().contains(ignoreStrings[i])) {
					stringIgnored = true;
					break;
				}
			}
		}
		if (ignoredApps.contains(pkgName))
			ignoreReasons.add("ignored app (pref.)");
		if (stringIgnored)
			ignoreReasons.add("ignored string (pref.)");
		if (event.getText().isEmpty())
			ignoreReasons.add("empty message");
		int ignoreRepeat;
		try {
			ignoreRepeat = Integer.parseInt(Common.prefs.getString("ignore_repeat", null));
		} catch (NumberFormatException e) {
			ignoreRepeat = -1;
		}
		if (lastMsg.contentEquals(newMsg) && (ignoreRepeat == -1 || newMsgTime - lastMsgTime < ignoreRepeat * 1000))
			ignoreReasons.add("identical message within " + (ignoreRepeat == -1 ? "infinite" : ignoreRepeat) + " seconds (pref.)");
		if (ignoreReasons.isEmpty()) {
			int delay = 0;
			try {
				delay = Integer.parseInt(Common.prefs.getString("ttsDelay", null));
			} catch (NumberFormatException e) {}
			if (delay > 0) {
				final String msg = newMsg;
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						speak(msg);
					}
				}, delay * 1000);
			} else speak(newMsg);
		} else {
			Log.i(Common.TAG, "Notification from " + label + " ignored for reason(s): " + ignoreReasons.toString().replaceAll("\\[|\\]", ""));
			ignoreReasons.clear();
		}
		lastMsg = newMsg;
		lastMsgTime = newMsgTime;
	}
	
	/**
	 * Sends msg to TTS if ignore condition is not met.
	 * @param msg The string to be spoken.
	 */
	private void speak(String msg) {
		if (ignore()) return;
		ttsHandler.obtainMessage(SPEAK, msg).sendToTarget();
	}
	
	/**
	 * Checks for any notification-independent ignore states.
	 * @returns True if an ignore condition is met, false otherwise.
	 */
	private boolean ignore() {
		Calendar c = Calendar.getInstance();
		int calTime = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
			quietStart = Common.prefs.getInt("quietStart", 0),
			quietEnd = Common.prefs.getInt("quietEnd", 0);
		if ((quietStart < quietEnd & quietStart <= calTime & calTime < quietEnd)
				| (quietEnd < quietStart & (quietStart <= calTime | calTime < quietEnd))) {
			ignoreReasons.add("quiet time (pref.)");
		}
		if (audioMan.getRingerMode() == AudioManager.RINGER_MODE_SILENT
				| audioMan.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
			ignoreReasons.add("silent or vibrate mode");
		}
		if (telephony.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK
				| telephony.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
			ignoreReasons.add("active or ringing call");
		}
		if (!isScreenOn() & !Common.prefs.getBoolean("speakScreenOff", true)) {
			ignoreReasons.add("screen off (pref.)");
		}
		if (isScreenOn() & !Common.prefs.getBoolean("speakScreenOn", true)) {
			ignoreReasons.add("screen on (pref.)");
		}
		if (!(isHeadsetPlugged | isBluetoothConnected) & !Common.prefs.getBoolean("speakHeadsetOff", true)) {
			ignoreReasons.add("headset off (pref.)");
		}
		if ((isHeadsetPlugged | isBluetoothConnected) & !Common.prefs.getBoolean("speakHeadsetOn", true)) {
			ignoreReasons.add("headset on (pref.)");
		}
		if (!ignoreReasons.isEmpty()) {
			Log.i(Common.TAG, "Notification ignored for reason(s): " + ignoreReasons.toString().replaceAll("\\[|\\]", ""));
			ignoreReasons.clear();
			return true;
		}
		return false;
	}

	@Override
	public void onInterrupt() {
		ttsHandler.sendEmptyMessage(STOP_SPEAK);
	}

	@Override
	public void onServiceConnected() {
		if (isInitialized) return;
		common = new Common(this);
		ttsHandler.sendEmptyMessage(START_TTS);
		setServiceInfo(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
		audioMan = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		telephony = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		IntentFilter filter =  new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(headsetReceiver, filter);
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
		if (android.os.Build.VERSION.SDK_INT >= 7)
			isScreenOn = CheckScreen.isScreenOn(this);
		return isScreenOn;
	}
	
	private static class CheckScreen {
		private static PowerManager powerMan;
		private static boolean isScreenOn(Context c) {
			if (powerMan == null)
				powerMan = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
			return powerMan.isScreenOn();
		}
	}
	
	private class HeadsetReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_HEADSET_PLUG))
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
			else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED))
				isBluetoothConnected = true;
			else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED))
				isBluetoothConnected = false;
			else if (action.equals(Intent.ACTION_SCREEN_ON))
				isScreenOn = true;
			else if (action.equals(Intent.ACTION_SCREEN_OFF))
				isScreenOn = false;
			if (mTts.isSpeaking() && ignore())
				ttsHandler.sendEmptyMessage(STOP_SPEAK);
		}
	}
}
