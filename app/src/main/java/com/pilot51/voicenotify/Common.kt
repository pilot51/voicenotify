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
package com.pilot51.voicenotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceManager

object Common {
	private lateinit var prefs: SharedPreferences

	/** Preference key name. */
	const val KEY_SPEAK_SCREEN_OFF = "speakScreenOff"
	const val KEY_SPEAK_SCREEN_ON = "speakScreenOn"
	const val KEY_SPEAK_HEADSET_OFF = "speakHeadsetOff"
	const val KEY_SPEAK_HEADSET_ON = "speakHeadsetOn"
	const val KEY_SPEAK_SILENT_ON = "speakSilentOn"

	/**
	 * Initializes default [SharedPreferences] if needed and sets volume control stream.
	 */
	fun init(activity: Activity) {
		init(activity.applicationContext)
		setVolumeStream(activity)
	}

	/**
	 * Initializes default [SharedPreferences] if needed.
	 */
	fun init(context: Context) {
		if (!::prefs.isInitialized) {
			PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
			PreferenceManager.setDefaultValues(context, R.xml.preferences_tts, true)
			prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
			convertOldStreamPref(context)
		}
	}

	/**
	 * If necessary, converts audio stream preference from obsolete word string to integer string.
	 * @since v1.0.11
	 */
	private fun convertOldStreamPref(c: Context) {
		val currentStream = prefs.getString(c.getString(R.string.key_ttsStream), null)
		currentStream?.toIntOrNull() ?: run {
			val newStream = if (currentStream == "notification") {
				AudioManager.STREAM_NOTIFICATION
			} else AudioManager.STREAM_MUSIC
			prefs.edit().putString(c.getString(R.string.key_ttsStream), newStream.toString()).apply()
		}
	}

	/**
	 * Sets the volume control stream defined in preferences.
	 */
	fun setVolumeStream(activity: Activity) {
		activity.volumeControlStream = getSelectedAudioStream(activity)
	}

	/**
	 * @param c Context used to get the preference key name from resources.
	 * @return The selected audio stream matching the STREAM_ constant from [AudioManager].
	 */
	fun getSelectedAudioStream(c: Context): Int {
		return prefs.getString(c.getString(R.string.key_ttsStream),
			AudioManager.STREAM_MUSIC.toString())!!.toInt()
	}

	/**
	 * @param context Context used to get a default [SharedPreferences] instance if we don't already have one.
	 * @return A default [SharedPreferences] instance.
	 */
	fun getPrefs(context: Context): SharedPreferences {
		if (!::prefs.isInitialized) {
			prefs = PreferenceManager.getDefaultSharedPreferences(context)
		}
		return prefs
	}

	val notificationListenerSettingsIntent: Intent
		get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
			Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
		} else {
			Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
		}
}
