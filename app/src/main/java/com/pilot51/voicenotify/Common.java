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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;

public class Common {
	private static SharedPreferences prefs;
	/** Preference key name. */
	static final String
		KEY_SPEAK_SCREEN_OFF = "speakScreenOff",
		KEY_SPEAK_SCREEN_ON = "speakScreenOn",
		KEY_SPEAK_HEADSET_OFF = "speakHeadsetOff",
		KEY_SPEAK_HEADSET_ON = "speakHeadsetOn",
		KEY_SPEAK_SILENT_ON = "speakSilentOn";
	
	private Common() {}
	
	/**
	 * Initializes default {@link SharedPreferences} and {@link Database} if needed and sets volume control stream.
	 */
	static void init(Activity activity) {
		init(activity.getApplicationContext());
		setVolumeStream(activity);
	}
	
	/**
	 * Initializes default {@link SharedPreferences} and {@link Database} if needed.
	 */
	static void init(Context context) {
		if (prefs == null) {
			PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
			prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
			convertOldStreamPref(context);
		}
		if (Database.getInstance() == null) {
			Database.init(context);
		}
	}
	
	/**
	 * If necessary, converts audio stream preference from obsolete word string to integer string.
	 * @since v1.0.11
	 */
	private static void convertOldStreamPref(Context c) {
		String currentStream = prefs.getString(c.getString(R.string.key_ttsStream), null);
		try {
			Integer.parseInt(currentStream);
		} catch (NumberFormatException e) {
			int newStream = AudioManager.STREAM_MUSIC;
			if (currentStream != null && currentStream.equals("notification")) {
				newStream = AudioManager.STREAM_NOTIFICATION;
			}
			prefs.edit().putString(c.getString(R.string.key_ttsStream), Integer.toString(newStream)).commit();
		}
	}
	
	/**
	 * Sets the volume control stream defined in preferences.
	 */
	static void setVolumeStream(Activity activity) {
		activity.setVolumeControlStream(getSelectedAudioStream(activity));
	}
	
	/**
	 * @param c Context used to get the preference key name from resources.
	 * @return The selected audio stream matching the STREAM_ constant from {@link AudioManager}.
	 */
	static int getSelectedAudioStream(Context c) {
		return Integer.parseInt(prefs.getString(c.getString(R.string.key_ttsStream),
		                                        Integer.toString(AudioManager.STREAM_MUSIC)));
	}
	
	/**
	 * @param context Context used to get a default {@link SharedPreferences} instance if we don't already have one.
	 * @return A default {@link SharedPreferences} instance.
	 */
	static SharedPreferences getPrefs(Context context) {
		if (prefs == null) {
			prefs = PreferenceManager.getDefaultSharedPreferences(context);
		}
		return prefs;
	}
}
