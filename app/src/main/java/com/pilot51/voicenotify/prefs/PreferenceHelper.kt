/*
 * Copyright 2011-2025 Mark Injerd
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
package com.pilot51.voicenotify.prefs

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.preference.PreferenceManager
import com.pilot51.voicenotify.BuildConfig
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.VNApplication
import com.pilot51.voicenotify.prefs.PreferenceHelper.LogIgnoredValue.entries
import com.pilot51.voicenotify.prefs.db.AppDatabase
import com.pilot51.voicenotify.prefs.db.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
object PreferenceHelper {
	private val TAG = PreferenceHelper::class.simpleName

	val KEY_DISABLE_AUTOSTART_MSG = booleanPreferencesKey("disable_autostart_msg")

	// Main
	val KEY_SHAKE_THRESHOLD = intPreferencesKey("shake_threshold")
	val KEY_LOG_IGNORED = intPreferencesKey("log_ignored")
	val KEY_LOG_IGNORED_APPS = intPreferencesKey("log_ignored_apps")

	// App List
	val KEY_APP_DEFAULT_ENABLE = booleanPreferencesKey("defEnable")

	// Service
	val KEY_IS_SUSPENDED = booleanPreferencesKey("isSuspended")

	// Defaults
	const val DEFAULT_SHAKE_THRESHOLD = 100
	const val DEFAULT_APP_DEFAULT_ENABLE = true
	const val DEFAULT_IS_SUSPENDED = false
	private val DEFAULT_LOG_IGNORED = LogIgnoredValue.SHOW
	private val DEFAULT_LOG_IGNORED_APPS = LogIgnoredValue.SHOW

	val dataFiles get() = arrayOf(
		VNApplication.Companion.appContext.getDatabasePath(AppDatabase.Companion.DB_NAME),
		VNApplication.Companion.appContext.preferencesDataStoreFile(DataStoreManager.DS_NAME)
	)

	val logIgnoredStateFlow = getLogIgnoredStateFlow(
		key = KEY_LOG_IGNORED,
		default = DEFAULT_LOG_IGNORED
	)

	val logIgnoredAppsStateFlow = getLogIgnoredStateFlow(
		key = KEY_LOG_IGNORED_APPS,
		default = DEFAULT_LOG_IGNORED_APPS
	)

	init {
		CoroutineScope(Dispatchers.IO).launch {
			initSettings()
		}
	}

	private fun getLogIgnoredStateFlow(
		key: Preferences.Key<Int>,
		default: LogIgnoredValue
	) = DataStoreManager.getPrefFlow(key, default.prefValue).mapLatest {
		LogIgnoredValue.fromPrefValue(it)
	}.stateIn(
		scope = CoroutineScope(Dispatchers.IO),
		started = SharingStarted.Companion.Eagerly,
		initialValue = null
	)

	fun setLogIgnored(setting: LogIgnoredSetting, value: LogIgnoredValue) {
		val currentAllValue = logIgnoredStateFlow.value!!
		val currentAppsValue = logIgnoredAppsStateFlow.value!!
		when (setting.prefKey) {
			KEY_LOG_IGNORED -> {
				if (value < currentAppsValue || currentAllValue == currentAppsValue) {
					DataStoreManager.setPref(KEY_LOG_IGNORED_APPS, value.prefValue)
				}
				DataStoreManager.setPref(setting.prefKey, value.prefValue)
			}
			KEY_LOG_IGNORED_APPS -> {
				if (value <= currentAllValue) {
					DataStoreManager.setPref(setting.prefKey, value.prefValue)
				}
			}
		}
	}

	/**
	 * Creates global settings in the database and DataStore
	 * with default values or migrated from shared preferences.
	 */
	private suspend fun initSettings() {
		val settingsDao = AppDatabase.Companion.db.settingsDao
		settingsDao.getGlobalSettings().first()?.let {
			DataStoreManager.dataStore.edit {
				it.initPref(KEY_LOG_IGNORED, DEFAULT_LOG_IGNORED.prefValue)
				it.initPref(KEY_LOG_IGNORED_APPS, DEFAULT_LOG_IGNORED_APPS.prefValue)
			}
			if (it.ttsSpeakEmojis == null) {
				settingsDao.update(it.copy(ttsSpeakEmojis = Settings.Companion.DEFAULT_SPEAK_EMOJIS))
			}
			return
		}
		val spDir = File(VNApplication.Companion.appContext.applicationInfo.dataDir, "shared_prefs")
		val spName = "${BuildConfig.APPLICATION_ID}_preferences"
		val spFile = File(spDir, "$spName.xml")
		if (spFile.exists()) {
			val sp = PreferenceManager.getDefaultSharedPreferences(VNApplication.Companion.appContext)
			settingsDao.insert(
				Settings.Companion.defaults.copy(
				audioFocus = sp.getBoolean("audio_focus", Settings.Companion.DEFAULT_AUDIO_FOCUS),
				requireStrings = sp.getString("require_strings", null),
				ignoreStrings = sp.getString("ignore_strings", null),
				ignoreEmpty = sp.getBoolean("ignore_empty", Settings.Companion.DEFAULT_IGNORE_EMPTY),
				ignoreGroups = sp.getBoolean("ignore_groups", Settings.Companion.DEFAULT_IGNORE_GROUPS),
				ignoreRepeat = sp.getString("ignore_repeat", Settings.Companion.DEFAULT_IGNORE_REPEAT.toString())
					?.toIntOrNull() ?: -1,
				speakScreenOff = sp.getBoolean("speakScreenOff", Settings.Companion.DEFAULT_SPEAK_SCREEN_OFF),
				speakScreenOn = sp.getBoolean("speakScreenOn", Settings.Companion.DEFAULT_SPEAK_SCREEN_ON),
				speakHeadsetOff = sp.getBoolean("speakHeadsetOff", Settings.Companion.DEFAULT_SPEAK_HEADSET_OFF),
				speakHeadsetOn = sp.getBoolean("speakHeadsetOn", Settings.Companion.DEFAULT_SPEAK_HEADSET_ON),
				speakSilentOn = sp.getBoolean("speakSilentOn", Settings.Companion.DEFAULT_SPEAK_SILENT_ON),
				quietStart = sp.getInt("quietStart", Settings.Companion.DEFAULT_QUIET_TIME),
				quietEnd = sp.getInt("quietEnd", Settings.Companion.DEFAULT_QUIET_TIME),
				ttsString = sp.getString("ttsString", Settings.Companion.DEFAULT_TTS_STRING),
				ttsTextReplace = sp.getString("ttsTextReplace", null),
				ttsMaxLength = sp.getString("key_max_length", null)
					?.toIntOrNull() ?: Settings.Companion.DEFAULT_MAX_LENGTH,
				ttsStream = sp.getString("ttsStream", null)
					?.toIntOrNull() ?: Settings.Companion.DEFAULT_TTS_STREAM,
				ttsDelay = sp.getString("ttsDelay", null)
					?.toDoubleOrNull()?.roundToInt(),
				ttsRepeat = sp.getString("tts_repeat", null)
					?.toDoubleOrNull() ?: 0.0
			))
			DataStoreManager.dataStore.edit { prefs ->
				sp.getString("shake_threshold", null)?.toDoubleOrNull()?.roundToInt()?.let {
					prefs[KEY_SHAKE_THRESHOLD] = it
				}
				prefs[KEY_APP_DEFAULT_ENABLE] = sp.getBoolean("defEnable", DEFAULT_APP_DEFAULT_ENABLE)
				prefs[KEY_IS_SUSPENDED] = sp.getBoolean("isSuspended", DEFAULT_IS_SUSPENDED)
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				VNApplication.Companion.appContext.deleteSharedPreferences(spName)
			} else {
				spFile.delete()
			}
		} else {
			settingsDao.insert(Settings.Companion.defaults)
			DataStoreManager.dataStore.edit { prefs ->
				prefs[KEY_SHAKE_THRESHOLD] = DEFAULT_SHAKE_THRESHOLD
				prefs[KEY_APP_DEFAULT_ENABLE] = DEFAULT_APP_DEFAULT_ENABLE
				prefs[KEY_IS_SUSPENDED] = DEFAULT_IS_SUSPENDED
			}
		}
		DataStoreManager.dataStore.edit {
			it[KEY_LOG_IGNORED] = DEFAULT_LOG_IGNORED.prefValue
			it[KEY_LOG_IGNORED_APPS] = DEFAULT_LOG_IGNORED_APPS.prefValue
		}
	}

	/** Initializes a preference with [default] if it hasn't been set yet. */
	private fun <T> MutablePreferences.initPref(key: Preferences.Key<T>, default: T) {
		if (!contains(key)) this[key] = default
	}

	fun exportBackup(uri: Uri) {
		CoroutineScope(Dispatchers.IO).launch {
			AppDatabase.Companion.closeDB()
			DataStoreManager.closeDataStore()
			VNApplication.Companion.appContext.contentResolver.openOutputStream(uri)?.use { outStream ->
				ZipOutputStream(BufferedOutputStream(outStream)).use { zipOut ->
					dataFiles.forEach { file ->
						BufferedInputStream(FileInputStream(file)).use { origin ->
							val buffer = ByteArray(1024)
							val entry = ZipEntry(file.name)
							zipOut.putNextEntry(entry)
							var length: Int
							while (origin.read(buffer).also { length = it } != -1) {
								zipOut.write(buffer, 0, length)
							}
						}
						Log.d(TAG, "Backed up file: ${file.name}")
					}
				}
			}
			DataStoreManager.openDataStore()
			AppDatabase.Companion.openDB()
		}
	}

	fun importBackup(uri: Uri) {
		CoroutineScope(Dispatchers.IO).launch {
			AppDatabase.Companion.closeDB()
			DataStoreManager.closeDataStore()
			VNApplication.Companion.appContext.contentResolver.openInputStream(uri)?.use { inStream ->
				ZipInputStream(BufferedInputStream(inStream)).use { zipIn ->
					var entry = zipIn.nextEntry
					while (entry != null) {
						dataFiles.find { it.name == entry.name }?.let { outFile ->
							FileOutputStream(outFile).use { fos ->
								val buffer = ByteArray(1024)
								var length: Int
								while (zipIn.read(buffer).also { length = it } > 0) {
									fos.write(buffer, 0, length)
								}
								fos.flush()
							}
							Log.d(TAG, "Restored file: ${entry.name}")
						} ?: Log.w(TAG, "Skipping unexpected file in backup archive: ${entry.name}")
						zipIn.closeEntry()
						entry = zipIn.nextEntry
					}
				}
			}
			DataStoreManager.openDataStore()
			AppDatabase.Companion.openDB()
		}
	}

	enum class LogIgnoredSetting(
		@StringRes val textRes: Int
	) {
		NOTIFICATIONS(R.string.notifications),
		APPS(R.string.apps);

		val prefKey by lazy {
			when (this) {
				NOTIFICATIONS -> KEY_LOG_IGNORED
				APPS -> KEY_LOG_IGNORED_APPS
			}
		}
	}

	enum class LogIgnoredValue(
		val prefValue: Int,
		@StringRes val textRes: Int
	) {
		NO_LOG(-1, R.string.do_not_log_ignored),
		HIDE(0, R.string.hide_ignored),
		SHOW(1, R.string.show_ignored);

		companion object {
			val dropdownList = entries.asReversed()
			fun fromPrefValue(value: Int) = entries.single { it.prefValue == value }
		}
	}
}