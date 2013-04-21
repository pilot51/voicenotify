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
	protected static String TAG;
	private Activity activity;
	private Context context;
	protected static SharedPreferences prefs;
	/** Preference key name. */
	protected static final String
		KEY_SPEAK_SCREEN_OFF = "speakScreenOff",
		KEY_SPEAK_SCREEN_ON = "speakScreenOn",
		KEY_SPEAK_HEADSET_OFF = "speakHeadsetOff",
		KEY_SPEAK_HEADSET_ON = "speakHeadsetOn",
		KEY_SPEAK_SILENT_ON = "speakSilentOn";
	
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
		String stream = prefs.getString(context.getString(R.string.key_ttsStream), "");
		if (stream.contentEquals("media")) {
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		} else if (stream.contentEquals("notification")) {
			activity.setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);
		}
	}
}
