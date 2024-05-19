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

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceManager
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import com.pilot51.voicenotify.db.AppDatabase
import com.pilot51.voicenotify.db.Settings
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_AUDIO_FOCUS
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_EMPTY
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_GROUPS
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_REPEAT
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_MAX_LENGTH
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_QUIET_TIME
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_HEADSET_OFF
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_HEADSET_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SCREEN_OFF
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SCREEN_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SILENT_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_TTS_STREAM
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_TTS_STRING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

object PreferenceHelper {
	// Main
	val KEY_SHAKE_THRESHOLD = intPreferencesKey("shake_threshold")

	// App List
	val KEY_APP_DEFAULT_ENABLE = booleanPreferencesKey("defEnable")

	// Service
	val KEY_IS_SUSPENDED = booleanPreferencesKey("isSuspended")

	// Defaults
	const val DEFAULT_SHAKE_THRESHOLD = 100
	const val DEFAULT_APP_DEFAULT_ENABLE = true
	const val DEFAULT_IS_SUSPENDED = false

	private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("prefs")
	private val dataStore = appContext.dataStore
	private val settingsDao = AppDatabase.db.settingsDao
	private val globalSettingsFlow = settingsDao.getGlobalSettings().filterNotNull()
	private lateinit var globalSettings: Settings

	init {
		CoroutineScope(Dispatchers.IO).launch {
			initSettings()
			launch {
				globalSettingsFlow.collect {
					globalSettings = it
				}
			}
		}
	}

	fun <T> getPrefFlow(key: Preferences.Key<T>, default: T) =
		dataStore.data.map { it[key] ?: default }

	/** Sets the value of the preference for [key], or removes it if [newValue] is `null`. */
	fun <T> setPref(key: Preferences.Key<T>, newValue: T?) {
		CoroutineScope(Dispatchers.IO).launch {
			dataStore.edit { pref ->
				newValue?.let { pref[key] = it } ?: pref.remove(key)
			}
		}
	}

	/**
	 * Creates global settings in the database and DataStore
	 * with default values or migrated from shared preferences.
	 */
	private suspend fun initSettings() {
		if (settingsDao.hasGlobalSettings()) return
		val spDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
		val spName = "com.pilot51.voicenotify_preferences"
		val spFile = File(spDir, "$spName.xml")
		if (spFile.exists()) {
			val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
			settingsDao.insert(Settings(
				audioFocus = sp.getBoolean("audio_focus", DEFAULT_AUDIO_FOCUS),
				requireStrings = sp.getString("require_strings", null),
				ignoreStrings = sp.getString("ignore_strings", null),
				ignoreEmpty = sp.getBoolean("ignore_empty", DEFAULT_IGNORE_EMPTY),
				ignoreGroups = sp.getBoolean("ignore_groups", DEFAULT_IGNORE_GROUPS),
				ignoreRepeat = sp.getString("ignore_repeat", DEFAULT_IGNORE_REPEAT.toString())
					?.toIntOrNull() ?: -1,
				speakScreenOff = sp.getBoolean("speakScreenOff", DEFAULT_SPEAK_SCREEN_OFF),
				speakScreenOn = sp.getBoolean("speakScreenOn", DEFAULT_SPEAK_SCREEN_ON),
				speakHeadsetOff = sp.getBoolean("speakHeadsetOff", DEFAULT_SPEAK_HEADSET_OFF),
				speakHeadsetOn = sp.getBoolean("speakHeadsetOn", DEFAULT_SPEAK_HEADSET_ON),
				speakSilentOn = sp.getBoolean("speakSilentOn", DEFAULT_SPEAK_SILENT_ON),
				quietStart = sp.getInt("quietStart", DEFAULT_QUIET_TIME),
				quietEnd = sp.getInt("quietEnd", DEFAULT_QUIET_TIME),
				ttsString = sp.getString("ttsString", DEFAULT_TTS_STRING),
				ttsTextReplace = sp.getString("ttsTextReplace", null),
				ttsMaxLength = sp.getString("key_max_length", null)
					?.toIntOrNull() ?: DEFAULT_MAX_LENGTH,
				ttsStream = sp.getString("ttsStream", null)
					?.toIntOrNull() ?: DEFAULT_TTS_STREAM,
				ttsDelay = sp.getString("ttsDelay", null)
					?.toDoubleOrNull()?.roundToInt(),
				ttsRepeat = sp.getString("tts_repeat", null)
					?.toDoubleOrNull() ?: 0.0
			))
			dataStore.edit { prefs ->
				prefs[KEY_SHAKE_THRESHOLD] = sp.getString("shake_threshold", null)
					?.toDoubleOrNull()?.roundToInt() ?: DEFAULT_SHAKE_THRESHOLD
				prefs[KEY_APP_DEFAULT_ENABLE] = sp.getBoolean("defEnable", DEFAULT_APP_DEFAULT_ENABLE)
				prefs[KEY_IS_SUSPENDED] = sp.getBoolean("isSuspended", DEFAULT_IS_SUSPENDED)
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				appContext.deleteSharedPreferences(spName)
			} else {
				spFile.delete()
			}
		} else settingsDao.insert(Settings.defaults)
	}
}
