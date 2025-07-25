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
package com.pilot51.voicenotify.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pilot51.voicenotify.BuildConfig
import com.pilot51.voicenotify.Constants.DEV_EMAIL
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.VNApplication.Companion.logFile
import com.pilot51.voicenotify.prefs.DataStoreManager.getPrefStateFlow
import com.pilot51.voicenotify.prefs.DataStoreManager.setPref
import com.pilot51.voicenotify.prefs.PreferenceHelper.DEFAULT_SHAKE_THRESHOLD
import com.pilot51.voicenotify.prefs.PreferenceHelper.KEY_SHAKE_THRESHOLD
import com.pilot51.voicenotify.prefs.PreferenceHelper.dataFiles
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.prefs.db.AppDatabase
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.db
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.getAppSettingsFlow
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.globalSettingsFlow
import com.pilot51.voicenotify.prefs.db.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PreferencesViewModel : ViewModel(), IPreferencesViewModel {
	override val configuringAppState = MutableStateFlow<App?>(null)
	override val configuringSettingsState = MutableStateFlow(Settings.defaults)
	override val configuringSettingsComboState = MutableStateFlow(Settings.defaults)

	init {
		viewModelScope.launch(Dispatchers.IO) {
			var settingsFlowJob: Job? = null
			var gSettingsFlowJob: Job? = null
			configuringAppState.collect { app ->
				settingsFlowJob?.cancel()
				settingsFlowJob = launch {
					val settingsFlow = app?.let { getAppSettingsFlow(it) } ?: globalSettingsFlow
					settingsFlow.collect {
						gSettingsFlowJob?.cancel()
						configuringSettingsState.value = it
						if (settingsFlow == globalSettingsFlow) {
							configuringSettingsComboState.value = it
						} else {
							gSettingsFlowJob = launch {
								globalSettingsFlow.collect { gs ->
									configuringSettingsComboState.value = gs.merge(it)
								}
							}
						}
					}
				}
			}
		}
	}

	@Composable
	override fun getShakeThreshold(): State<Int> {
		return if (isPreview) {
			remember { mutableIntStateOf(DEFAULT_SHAKE_THRESHOLD) }
		} else {
			getPrefStateFlow(KEY_SHAKE_THRESHOLD, 0).collectAsState()
		}
	}

	override fun setShakeThreshold(threshold: Int?) {
		setPref(KEY_SHAKE_THRESHOLD, threshold)
	}

	override fun getApp(appPkg: String) = runBlocking(Dispatchers.IO) {
		db.appDao.get(appPkg)
	}

	override fun setCurrentConfigApp(app: App?) {
		configuringAppState.value = app
	}

	@Composable
	override fun getSettingsState(app: App?) =
		(app?.let { getAppSettingsFlow(it) } ?: globalSettingsFlow.filterNotNull())
			.collectAsState(initial = Settings.defaults)

	override fun save(settings: Settings) {
		viewModelScope.launch(Dispatchers.IO) {
			db.settingsDao.run {
				if (!settings.isGlobal && settings.areAllSettingsNull()) {
					delete(settings)
				} else upsert(settings)
			}
		}
	}

	override fun getTtsTextReplace(settings: Settings) = convertTextReplaceStringToList(settings.ttsTextReplace)

	override fun saveTtsTextReplace(
		settings: Settings,
		list: List<Pair<String, String>>
	) {
		viewModelScope.launch {
			val trimmedList: MutableList<Pair<String, String>> = ArrayList(list.size)
			copyLoop@ for (pair in list) {
				if (pair.first.isNotEmpty()) {
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
		private val TAG = PreferencesViewModel::class.simpleName

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

		private fun convertTextReplaceListToString(list: List<Pair<String, String>>) = list
			.flatMap { listOf(it.first, it.second) }
			.joinToString("\n")
			.ifEmpty { null }

		/**
		 * Converts a string of paired substrings separated by newlines into a list of string pairs.
		 * @param string The string to convert. Each string in and between pairs must be separated by a newline.
		 * @return A List of string pairs.
		 */
		fun convertTextReplaceStringToList(string: String?) =
			if (string.isNullOrEmpty()) {
				listOf()
			} else {
				string.split("\n").dropLastWhile { it.isEmpty() }
					.chunked(2) { it.first() to it.getOrElse(1) { "" } }
			}

		fun readDebugLog(
			scope: CoroutineScope,
			onReadLine: (line: String) -> Unit,
			onDone: () -> Unit
		) {
			scope.launch(Dispatchers.IO) {
				logFile?.run {
					if (exists()) {
						bufferedReader().useLines { lines ->
							lines.forEach { onReadLine(it) }
						}
					}
				}
				onDone()
			}
		}

		/**
		 * @param context Should be an activity context so the intent doesn't need to use [Intent.FLAG_ACTIVITY_NEW_TASK].
		 * @param includeLog `true` to attach the debug log to the email.
		 * @param includeSettings `true` to attach the database and datastore to the email.
		 */
		fun sendEmail(context: Context, includeLog: Boolean, includeSettings: Boolean) {
			val iEmail = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
				type = "plain/text"
				putExtra(Intent.EXTRA_EMAIL, arrayOf(DEV_EMAIL))
				putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject))
				putExtra(Intent.EXTRA_TEXT, context.getString(
					R.string.email_body,
					BuildConfig.VERSION_NAME,
					Build.VERSION.RELEASE,
					Build.ID,
					"${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}"
				))
				if (includeLog || includeSettings) {
					val uris = ArrayList<Uri>(3)
					val auth = "${context.packageName}.fileprovider"
					if (includeLog) {
						logFile?.let {
							if (it.exists()) {
								try {
									uris.add(FileProvider.getUriForFile(context, auth, it))
								} catch (e: IllegalArgumentException) {
									Log.w(TAG, e)
								}
							}
						}
					}
					if (includeSettings) {
						val (dbFile, dsFile) = dataFiles
						db.close()
						try {
							uris.add(FileProvider.getUriForFile(context, auth, dbFile))
						} catch (e: IllegalArgumentException) {
							Log.w(TAG, e)
						}
						AppDatabase.openDB()
						try {
							uris.add(FileProvider.getUriForFile(context, auth, dsFile))
						} catch (e: IllegalArgumentException) {
							Log.w(TAG, e)
						}
					}
					putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
					addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
			}
			try {
				context.startActivity(Intent.createChooser(iEmail, context.getString(R.string.support_email)))
			} catch (e: ActivityNotFoundException) {
				Log.w(TAG, e)
				Toast.makeText(context, R.string.error_email, Toast.LENGTH_LONG).show()
			}
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

	fun setShakeThreshold(threshold: Int?) {}

	fun getApp(appPkg: String): App = App("app.package", "App Label", true)

	fun setCurrentConfigApp(app: App?) {}

	@Composable
	fun getSettingsState(app: App?): State<Settings> =
		remember { mutableStateOf(Settings.defaults) }

	fun save(settings: Settings) {}

	fun getTtsTextReplace(settings: Settings): List<Pair<String, String>> = listOf(
		Pair("first", "second"),
		Pair("this", "that")
	)

	fun saveTtsTextReplace(settings: Settings, list: List<Pair<String, String>>) {}
}
