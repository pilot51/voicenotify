package com.pilot51.voicenotify;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;

public class Common {
	protected static String TAG;
	private Activity activity;
	private Context context;
	protected static SharedPreferences prefs;
	/** Preference key name. */
	protected static final String
		SPEAK_SCREEN_OFF = "speakScreenOff",
		SPEAK_SCREEN_ON = "speakScreenOn",
		SPEAK_HEADSET_OFF = "speakHeadsetOff",
		SPEAK_HEADSET_ON = "speakHeadsetOn",
		SPEAK_SILENT_ON = "speakSilentOn";
	
	Common(Activity a) {
		activity = a;
		context = a;
		onStart();
		setVolumeStream();
	}
	Common(Context c) {
		context = c;
		onStart();
	}
	void onStart() {
		if (TAG == null) {
			TAG = context.getString(R.string.app_name);
			PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
			prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
			new Database(context);
		}
	}
	
	protected void setVolumeStream() {
		if (prefs.getString("ttsStream", null).contentEquals("media"))
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		else if (prefs.getString("ttsStream", null).contentEquals("notification"))
			activity.setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);
	}
}
