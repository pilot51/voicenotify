/*
 * Copyright 2011-2024 Mark Injerd
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

import android.content.SharedPreferences
import android.media.AudioManager
import androidx.core.text.isDigitsOnly
import androidx.preference.PreferenceManager
import com.pilot51.voicenotify.VNApplication.Companion.appContext

object PreferenceHelper {
	// Main
	const val KEY_AUDIO_FOCUS = "audio_focus"
	const val KEY_SHAKE_THRESHOLD = "shake_threshold"
	const val KEY_REQUIRE_STRINGS = "require_strings"
	const val KEY_IGNORE_STRINGS = "ignore_strings"
	const val KEY_IGNORE_EMPTY = "ignore_empty"
	const val KEY_IGNORE_GROUPS = "ignore_groups"
	const val KEY_IGNORE_REPEAT = "ignore_repeat"
	const val KEY_QUIET_START = "quietStart"
	const val KEY_QUIET_END = "quietEnd"

	// Device states
	const val KEY_SPEAK_SCREEN_OFF = "speakScreenOff"
	const val KEY_SPEAK_SCREEN_ON = "speakScreenOn"
	const val KEY_SPEAK_HEADSET_OFF = "speakHeadsetOff"
	const val KEY_SPEAK_HEADSET_ON = "speakHeadsetOn"
	const val KEY_SPEAK_SILENT_ON = "speakSilentOn"

	// TTS
	const val KEY_TTS_STRING = "ttsString"
	const val KEY_TTS_TEXT_REPLACE = "ttsTextReplace"
	const val KEY_MAX_LENGTH = "key_max_length"
	const val KEY_TTS_STREAM = "ttsStream"
	const val KEY_TTS_DELAY = "ttsDelay"
	const val KEY_TTS_REPEAT = "tts_repeat"

	// App List
	const val KEY_APP_DEFAULT_ENABLE = "defEnable"

	// Service
	const val KEY_IS_SUSPENDED = "isSuspended"

	// Defaults
	const val DEFAULT_AUDIO_FOCUS = true
	const val DEFAULT_SHAKE_THRESHOLD = 100
	const val DEFAULT_IGNORE_EMPTY = true
	const val DEFAULT_IGNORE_GROUPS = true
	const val DEFAULT_IGNORE_REPEAT = 10
	const val DEFAULT_QUIET_TIME = 0
	const val DEFAULT_SPEAK_SCREEN_OFF = true
	const val DEFAULT_SPEAK_SCREEN_ON = true
	const val DEFAULT_SPEAK_HEADSET_OFF = true
	const val DEFAULT_SPEAK_HEADSET_ON = true
	const val DEFAULT_SPEAK_SILENT_ON = false
	const val DEFAULT_TTS_STRING = "#A\n#C\n#M"
	const val DEFAULT_MAX_LENGTH = 500
	const val DEFAULT_TTS_STREAM = AudioManager.STREAM_MUSIC
	const val DEFAULT_APP_DEFAULT_ENABLE = true
	const val DEFAULT_IS_SUSPENDED = false

	val prefs: SharedPreferences by lazy {
		PreferenceManager.getDefaultSharedPreferences(appContext).apply {
			convertOldStreamPref()
		}
	}

	/** @return The selected audio stream matching the `STREAM_` constant from [AudioManager]. */
	fun getSelectedAudioStream() = prefs.getString(KEY_TTS_STREAM, null)
		?.toIntOrNull() ?: DEFAULT_TTS_STREAM

	/**
	 * If necessary, converts audio stream preference from obsolete word string to integer string.
	 * @since v1.0.11
	 */
	private fun SharedPreferences.convertOldStreamPref() {
		getString(KEY_TTS_STREAM, null)?.takeUnless { it.isDigitsOnly() }?.let {
			val newStream = when (it) {
				"media" -> AudioManager.STREAM_MUSIC
				"notification" -> AudioManager.STREAM_NOTIFICATION
				else -> {
					edit().remove(KEY_TTS_STREAM).apply()
					return
				}
			}
			edit().putString(KEY_TTS_STREAM, newStream.toString()).apply()
		}
	}
}
