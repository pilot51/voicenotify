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
import java.util.concurrent.LinkedBlockingQueue;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class Service extends AccessibilityService {
	private static String TAG = Service.class.getSimpleName();
	private String lastMsg = "";
	private long lastMsgTime;
	private TextToSpeech mTts;
	private boolean shouldRequestFocus;
	private AudioManager audioMan;
	private TelephonyManager telephony;
	private final DeviceStateReceiver stateReceiver = new DeviceStateReceiver();
	private RepeatTimer repeater;
	private Shake shake;
	private static boolean isInitialized, isSuspended, isScreenOn, isHeadsetPlugged, isBluetoothConnected;
	private final ArrayList<String> ignoreReasons = new ArrayList<String>(),
	                                repeatList = new ArrayList<String>();
    /**
     * this is used to determine if we are the first, middle, or last thing to be spoken at the moment, for enabling/disabling shake and audio focus request
     * entries are added right before the call to speak and removed by onDone in the utteranceProgressListener
     * if the list is empty when we enqueue a message, we trigger shaking and audio focus requesting
     * if the list is empty when we finish speaking a message, we untrigger them.
     */
    private LinkedBlockingQueue<Boolean> messageStatuses = new LinkedBlockingQueue<Boolean>();
	private Handler handler = new Handler();
	private OnStatusChangeListener statusListener = new OnStatusChangeListener() {
		@Override
		public void onStatusChanged() {
			if (isSuspended && mTts != null) mTts.stop();
			sendBroadcast(new Intent(getApplicationContext(), WidgetProvider.class)
			              .setAction(WidgetProvider.ACTION_UPDATE));
		}
	};

    @Override
    public void onCreate() {
        mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.ERROR) {
                    Log.w(TAG, getString(R.string.error_tts_init));
                    Toast.makeText(getApplicationContext(), R.string.error_tts_init, Toast.LENGTH_LONG).show();
                    return;
                }
                mTts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                    @Override
                    public void onUtteranceCompleted(String utteranceId) {
                        messageStatuses.poll();
                        if (messageStatuses.isEmpty()) {
							if (Build.VERSION.SDK_INT >= 8 && shouldRequestFocus) {
								AudioFocus.abandonFocus(audioMan);
							}
                            shake.disable();
                        }
                    }
                });
                /* added in API 15 (4.0.3).  Once enough users are on that version to justify it, uncomment this block, as onUtteranceCompletedListener is now deprecated
                mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        messageStatuses.poll();
                        if(messageStatuses.isEmpty()){
                            if (shouldRequestFocus) {
                                audioMan.abandonAudioFocus(null);
                            }
                            shake.disable();
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.w(TAG, getString(R.string.error_tts_init));
                        Toast.makeText(getApplicationContext(), R.string.error_tts_init, Toast.LENGTH_LONG).show();
                    }
                });
                */
            }
        });
        super.onCreate();
    }

    @Override
	public synchronized void onAccessibilityEvent(AccessibilityEvent event) {
		if (!Common.getPrefs(this).getBoolean(getString(R.string.key_toasts), false) && !(event.getParcelableData() instanceof Notification)) {
			return;
		}
		long newMsgTime = System.currentTimeMillis();
		StringBuilder notifyMsg = new StringBuilder();
		if (!event.getText().isEmpty()) {
			for (CharSequence subText : event.getText()) {
				notifyMsg.append(subText);
			}
		}
		final String ttsStringPref = Common.getPrefs(this).getString(getString(R.string.key_ttsString), null);
		App app = AppList.findOrAddApp(event.getPackageName().toString(), this);
		NotifyList.addNotification(app, notifyMsg.toString());
		String newMsg;
		try {
			if (event.getText().isEmpty()) {
				newMsg = getString(R.string.notification_from, app.getLabel());
			} else {
				newMsg = String.format(ttsStringPref.replace("%t", "%1$s").replace("%m", "%2$s"),
				                       app.getLabel(), notifyMsg.toString().replaceAll("[\\|\\[\\]\\{\\}\\*<>]+", " "));
			}
		} catch(IllegalFormatException e) {
			Log.w(TAG, "Error formatting custom TTS string!");
			e.printStackTrace();
			newMsg = ttsStringPref;
		}
		final String[] ignoreStrings = Common.getPrefs(this).getString(getString(R.string.key_ignore_strings), "").toLowerCase().split("\n");
		boolean stringIgnored = false;
		if (ignoreStrings != null) {
			for (String s : ignoreStrings) {
				if (s.length() != 0 && notifyMsg.toString().toLowerCase().contains(s)) {
					stringIgnored = true;
					break;
				}
			}
		}
		if (isSuspended) {
			ignoreReasons.add(getString(R.string.reason_suspended));
		}
		if (!app.getEnabled()) {
			ignoreReasons.add(getString(R.string.reason_app));
		}
		if (stringIgnored) {
			ignoreReasons.add(getString(R.string.reason_string));
		}
		if (event.getText().isEmpty() && Common.getPrefs(this).getBoolean(getString(R.string.key_ignore_empty), false)) {
			ignoreReasons.add(getString(R.string.reason_empty_msg));
		}
		int ignoreRepeat;
		try {
			ignoreRepeat = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_ignore_repeat), null));
		} catch (NumberFormatException e) {
			ignoreRepeat = -1;
		}
		if (lastMsg.equals(newMsg) && (ignoreRepeat == -1 || newMsgTime - lastMsgTime < ignoreRepeat * 1000)) {
			ignoreReasons.add(MessageFormat.format(getString(R.string.reason_identical), ignoreRepeat));
		}
		if (ignoreReasons.isEmpty()) {
			int delay = 0;
			try {
				delay = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_ttsDelay), null));
			} catch (NumberFormatException e) {}
			if (!isScreenOn()) {
				int interval;
				try {
					interval = Integer.parseInt(Common.getPrefs(this).getString(getString(R.string.key_tts_repeat), "0"));
				} catch (NumberFormatException e) {
					interval = 0;
				}
				if (interval > 0) {
					repeatList.add(newMsg);
					if (repeater == null) {
						repeater = new RepeatTimer(interval);
					}
				}
			}
            if(delay < 0){ //just in case we get a weird value, don't want to try to make the Timer wait for negative time
                delay = 0;
            }
            final String msg = newMsg;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            speak(msg, true);
                        }
                    });
                }
            }, delay * 1000L); //a delay of 0 works fine, and means that all speak calls anywhere are running in their own thread and not blocking.
		} else {
			String reasons = ignoreReasons.toString().replaceAll("\\[|\\]", "");
			Log.i(TAG, "Notification from " + app.getLabel() + " ignored for reason(s): " + reasons);
			NotifyList.setLastIgnore(reasons, true);
			ignoreReasons.clear();
		}
		lastMsg = newMsg;
		lastMsgTime = newMsgTime;
	}

    /**
     * Sends msg to TTS if ignore condition is not met.
     * ALWAYS CALLED INSIDE A SPAWNED THREAD
     * @param msg The string to be spoken.
     * @param isNew True if the notification was just received, otherwise false if it is being repeated.
     */
    private void speak(final String msg, boolean isNew) {
        if (ignore(isNew)) return;
        if (messageStatuses.isEmpty()) { //if there are no messages in the queue, start up shake detection and audio focus requesting
            shake.enable();
            shouldRequestFocus = Common.getPrefs(getApplicationContext())
                    .getBoolean(getString(R.string.key_audio_focus), false);
			if (Build.VERSION.SDK_INT >= 8 && shouldRequestFocus) {
				AudioFocus.requestFocus(audioMan);
			}
        }
        //regardless, add the message to the queue, parallelling the TextToSpeech queue since we can't access it.
        try {
            messageStatuses.put(false);
        } catch (InterruptedException e) {
            //we had an error with too many threads trying to speak at once, cancel the whole thing before adding to the mess
            return;
        }
        //once the message is in our queue, send it to the real one with the necessary parameters
        final HashMap<String, String> ttsParams = new HashMap<String, String>();
        //needed because we want to apply stream changes immediately
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                      Integer.toString(Common.getSelectedAudioStream(getApplicationContext())));
        //not used anywhere, but has to be set to get the UtteranceProgressListener to trigger its submethods
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, Long.toString(System.currentTimeMillis()));
        mTts.speak(msg, TextToSpeech.QUEUE_ADD, ttsParams);
    }

    /**
	 * Checks for any notification-independent ignore states.
	 * @param isNew True for a notification that was just received, otherwise false.
	 * @returns True if an ignore condition is met, false otherwise.
	 */
	private boolean ignore(boolean isNew) {
		Calendar c = Calendar.getInstance();
		int calTime = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
			quietStart = Common.getPrefs(this).getInt(getString(R.string.key_quietStart), 0),
			quietEnd = Common.getPrefs(this).getInt(getString(R.string.key_quietEnd), 0);
		if ((quietStart < quietEnd & quietStart <= calTime & calTime < quietEnd)
				|| (quietEnd < quietStart && (quietStart <= calTime || calTime < quietEnd))) {
			ignoreReasons.add(getString(R.string.reason_quiet));
		}
		if ((audioMan.getRingerMode() == AudioManager.RINGER_MODE_SILENT
				|| audioMan.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)
				&& !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SILENT_ON, false)) {
			ignoreReasons.add(getString(R.string.reason_silent));
		}
		if (telephony.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
			ignoreReasons.add(getString(R.string.reason_call));
		}
		if (!isScreenOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true)) {
			ignoreReasons.add(getString(R.string.reason_screen_off));
		}
		if (isScreenOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SCREEN_ON, true)) {
			ignoreReasons.add(getString(R.string.reason_screen_on));
		}
		if (!isHeadsetOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true)) {
			ignoreReasons.add(getString(R.string.reason_headset_off));
		}
		if (isHeadsetOn() && !Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_HEADSET_ON, true)) {
			ignoreReasons.add(getString(R.string.reason_headset_on));
		}
		if (!ignoreReasons.isEmpty()) {
			String reasons = ignoreReasons.toString().replaceAll("\\[|\\]", "");
			Log.i(TAG, "Notification ignored for reason(s): " + reasons);
			NotifyList.setLastIgnore(reasons, isNew);
			ignoreReasons.clear();
			return true;
		}
		return false;
	}

	private class RepeatTimer extends TimerTask {
		private RepeatTimer(int minuteInterval) {
			if (minuteInterval <= 0) return;
			long interval = minuteInterval * 60000L;
			new Timer().schedule(this, interval, interval);
		}

		@Override
		public void run() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (String s : repeatList) {
						speak(s, false);
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
	public void onInterrupt() {
		if (mTts != null) mTts.stop();
	}

	@Override
	public void onServiceConnected() {
		if (isInitialized) return;
		Common.init(this);
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
		setServiceInfo(info);
		audioMan = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		telephony = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		IntentFilter filter =  new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(stateReceiver, filter);
		shake = new Shake(this);
		shake.setOnShakeListener(new Shake.OnShakeListener() {
			@Override
			public void onShake() {
				Log.i(TAG, "TTS silenced by shake");
				NotifyList.setLastIgnore(getString(R.string.reason_shake), false);
				if (mTts != null) mTts.stop();
			}
		});
		registerOnStatusChangeListener(statusListener);
		setInitialized(true);
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

	private static final ArrayList<OnStatusChangeListener> statusListeners = new ArrayList<OnStatusChangeListener>();
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

	private boolean isScreenOn() {
		if (Build.VERSION.SDK_INT >= 7) {
			isScreenOn = CheckScreen.isScreenOn(this);
		}
		return isScreenOn;
	}

	@SuppressLint("NewApi")
	private boolean isHeadsetOn() {
		if (Build.VERSION.SDK_INT >= 5) {
			return (audioMan.isBluetoothA2dpOn() || audioMan.isWiredHeadsetOn());
		}
		else
		{
			return (isHeadsetPlugged || isBluetoothConnected);
		}
	}

	@SuppressLint("NewApi")
	private static class CheckScreen {
		private static PowerManager powerMan;
		private static boolean isScreenOn(Context c) {
			if (powerMan == null) {
				powerMan = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
			}
			return powerMan.isScreenOn();
		}
	}

	@SuppressLint("NewApi")
	private static class AudioFocus {
		private static void abandonFocus(AudioManager audioMan) {
			audioMan.abandonAudioFocus(null);
		}

		private static void requestFocus(AudioManager audioMan) {
			audioMan.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
					AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
		}
	}

	private class DeviceStateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			boolean interruptIfIgnored = true;
			if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
				isHeadsetPlugged = intent.getIntExtra("state", 0) == 1;
			} else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
				isBluetoothConnected = true;
			} else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
				isBluetoothConnected = false;
			} else if (action.equals(Intent.ACTION_SCREEN_ON)) {
				isScreenOn = true;
				if (repeater != null) {
					repeater.cancel();
					repeatList.clear();
				}
				interruptIfIgnored = false;
			} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
				isScreenOn = false;
				interruptIfIgnored = false;
			}
			if (interruptIfIgnored && mTts != null && ignore(false)) {
				mTts.stop();
			}
		}
	}
}
