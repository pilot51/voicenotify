/*
 * Copyright 2012-2023 Mark Injerd
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
package com.pilot51.voicenotify

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.pilot51.voicenotify.VNApplication.Companion.appContext

object Common {
	val prefs: SharedPreferences by lazy {
		PreferenceManager.setDefaultValues(appContext, R.xml.preferences, true)
		PreferenceManager.setDefaultValues(appContext, R.xml.preferences_tts, true)
		PreferenceManager.getDefaultSharedPreferences(appContext).apply {
			convertOldStreamPref()
		}
	}

	/** Preference key name. */
	const val KEY_SPEAK_SCREEN_OFF = "speakScreenOff"
	const val KEY_SPEAK_SCREEN_ON = "speakScreenOn"
	const val KEY_SPEAK_HEADSET_OFF = "speakHeadsetOff"
	const val KEY_SPEAK_HEADSET_ON = "speakHeadsetOn"
	const val KEY_SPEAK_SILENT_ON = "speakSilentOn"

	/**
	 * If necessary, converts audio stream preference from obsolete word string to integer string.
	 * @since v1.0.11
	 */
	private fun SharedPreferences.convertOldStreamPref() {
		val currentStream = getString(appContext.getString(R.string.key_ttsStream), null)
		currentStream?.toIntOrNull() ?: run {
			val newStream = if (currentStream == "notification") {
				AudioManager.STREAM_NOTIFICATION
			} else AudioManager.STREAM_MUSIC
			edit().putString(appContext.getString(R.string.key_ttsStream),
				newStream.toString()).apply()
		}
	}

	/**
	 * Sets the volume control stream defined in preferences.
	 */
	fun setVolumeStream(activity: Activity) {
		activity.volumeControlStream = getSelectedAudioStream()
	}

	/** @return The selected audio stream matching the STREAM_ constant from [AudioManager]. */
	fun getSelectedAudioStream(): Int {
		return prefs.getString(appContext.getString(R.string.key_ttsStream),
			AudioManager.STREAM_MUSIC.toString())!!.toInt()
	}

	val notificationListenerSettingsIntent: Intent by lazy {
		Intent(
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
				Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
			} else "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
		)
	}
}
