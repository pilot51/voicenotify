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

import android.util.Pair
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.KEY_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.setPref
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppDatabase
import com.pilot51.voicenotify.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PreferencesViewModel : ViewModel(), IPreferencesViewModel {
	private val settingsDao = AppDatabase.db.settingsDao
	private val globalSettingsFlow = settingsDao.getGlobalSettings().filterNotNull()
	override val configuringAppState = MutableStateFlow<App?>(null)
	override val configuringSettingsState = MutableStateFlow(Settings.defaults)
	override val configuringSettingsComboState = MutableStateFlow(Settings.defaults)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			var settingsFlowJob: Job? = null
			configuringAppState.collect { app ->
				settingsFlowJob?.cancel()
				settingsFlowJob = launch {
					val settingsFlow = app?.let {
						settingsDao.getAppSettings(app.packageName).map {
							it ?: Settings(appPackage = app.packageName)
						}
					} ?: globalSettingsFlow
					settingsFlow.collect {
						configuringSettingsState.value = it
						configuringSettingsComboState.value = globalSettingsFlow.first().let { gs ->
							if (app == null) gs else gs.merge(it)
						}
					}
				}
			}
		}
	}

	@Composable
	override fun getShakeThreshold(): State<Int> {
		val default = DEFAULT_SHAKE_THRESHOLD
		return if (isPreview) {
			remember { mutableIntStateOf(default) }
		} else {
			PreferenceHelper.getPrefFlow(KEY_SHAKE_THRESHOLD, default)
				.collectAsState(initial = default)
		}
	}

	override fun getApp(appPkg: String) = runBlocking(Dispatchers.IO) {
		AppDatabase.db.appDao.get(appPkg)
	}

	override fun setCurrentConfigApp(app: App?) {
		configuringAppState.value = app
	}

	@Composable
	override fun getSettingsState(app: App?) = (app?.let {
		settingsDao.getAppSettings(app.packageName).map {
			it ?: Settings(appPackage = app.packageName)
		}
	} ?: globalSettingsFlow).collectAsState(initial = Settings.defaults)

	override fun save(settings: Settings) {
		viewModelScope.launch(Dispatchers.IO) {
			if (settings.areAllSettingsNull()) {
				settingsDao.delete(settings)
			} else settingsDao.upsert(settings)
		}
	}

	override fun setShakeThreshold(threshold: Int?) {
		setPref(KEY_SHAKE_THRESHOLD, threshold)
	}

	override fun getTtsTextReplace(settings: Settings) = convertTextReplaceStringToList(settings.ttsTextReplace)

	override fun saveTtsTextReplace(
		settings: Settings,
		list: List<Pair<String, String>?>
	) {
		viewModelScope.launch {
			val trimmedList: MutableList<Pair<String, String>> = ArrayList(list.size)
			copyLoop@ for (pair in list) {
				if (pair != null && pair.first.isNotEmpty()) {
					for (p in trimmedList) {
						if (pair.first.equals(p.first, ignoreCase = true)) {
							continue@copyLoop
						}
					}
					trimmedList.add(pair)
				}
			}
			val savedList = convertTextReplaceStringToList(settings.ttsTextReplace)
			var changed = false
			if (trimmedList.size != savedList.size ||
				(!settings.isGlobal && trimmedList.isEmpty())
			) {
				changed = true
			} else {
				for (i in trimmedList.indices) {
					if (trimmedList[i] != savedList[i]) {
						changed = true
						break
					}
				}
			}
			if (changed) {
				save(settings.copy(
					ttsTextReplace = convertTextReplaceListToString(trimmedList)
						?: "".takeIf { !settings.isGlobal }
				))
			}
		}
	}

	companion object {
		fun updateListItem(
			position: Int,
			textFrom: String,
			textTo: String,
			list: MutableList<Pair<String, String>?>
		) {
			var listPair: Pair<String, String>? = null
			if (position < list.size) {
				listPair = list[position]
			}
			val replacePair = Pair(textFrom, textTo)
			if (replacePair.first.isNotEmpty()) {
				if (listPair == null) {
					list.add(position, replacePair)
				} else if (replacePair.first != listPair.first
					|| replacePair.second != listPair.second) {
					list[position] = replacePair
				}
			} else if (listPair != null) {
				list.removeAt(position)
			}
		}

		/**
		 * Checks if text is a duplicate of another entry in [list],
		 * otherwise clears error if not a duplicate.
		 * @param position The position of this entry in [list] to prevent detecting self as duplicate.
		 * @param textFrom The text to check if duplicated.
		 */
		fun isDuplicate(
			position: Int,
			textFrom: String,
			list: List<Pair<String, String>?>
		): Boolean {
			for (p in list) {
				if (p != null && list.indexOf(p) < position &&
					textFrom.equals(p.first, ignoreCase = true)
				) return true
			}
			return false
		}

		private fun convertTextReplaceListToString(list: List<Pair<String, String>?>): String? {
			if (list.filterNotNull().isEmpty()) return null
			val saveString = StringBuilder()
			for (pair in list) {
				if (pair == null) break
				if (saveString.isNotEmpty()) {
					saveString.append("\n")
				}
				saveString.append(pair.first)
				saveString.append("\n")
				saveString.append(pair.second)
			}
			return saveString.toString().ifEmpty { null }
		}

		/**
		 * Converts a string of paired substrings separated by newlines into a list of string pairs.
		 * @param string The string to convert. Each string in and between pairs must be separated by a newline.
		 * There should be an odd number of newlines for an even number of substrings (including zero-length),
		 * otherwise the last substring will be discarded.
		 * @return A List of string pairs.
		 */
		fun convertTextReplaceStringToList(string: String?): List<Pair<String, String>> {
			val list = mutableListOf<Pair<String, String>>()
			if (string.isNullOrEmpty()) return list
			val array = string.split("\n").dropLastWhile { it.isEmpty() }.toTypedArray()
			var i = 0
			while (i + 1 < array.size) {
				list.add(Pair(array[i], array[i + 1]))
				i += 2
			}
			return list
		}
	}
}

object PreferencesPreviewVM : IPreferencesViewModel

interface IPreferencesViewModel {
	val configuringAppState: MutableStateFlow<App?>
		get() = MutableStateFlow(null)
	val configuringSettingsState: MutableStateFlow<Settings>
		get() = MutableStateFlow(Settings.defaults)
	val configuringSettingsComboState: MutableStateFlow<Settings>
		get() = MutableStateFlow(Settings.defaults)

	@Composable
	fun getShakeThreshold(): State<Int> =
		remember { mutableIntStateOf(DEFAULT_SHAKE_THRESHOLD) }

	fun getApp(appPkg: String): App = App("app.package", "App Label", true)

	fun setCurrentConfigApp(app: App?) {}

	@Composable
	fun getSettingsState(app: App?): State<Settings> =
		remember { mutableStateOf(Settings.defaults) }

	fun save(settings: Settings) {}

	fun setShakeThreshold(threshold: Int?) {}

	fun getTtsTextReplace(settings: Settings): List<Pair<String, String>> = listOf(
		Pair("first", "second"),
		Pair("this", "that")
	)

	fun saveTtsTextReplace(settings: Settings, list: List<Pair<String, String>?>) {}
}