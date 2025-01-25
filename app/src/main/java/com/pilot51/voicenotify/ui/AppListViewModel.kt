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

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pilot51.voicenotify.Common
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_APP_DEFAULT_ENABLE
import com.pilot51.voicenotify.PreferenceHelper.KEY_APP_DEFAULT_ENABLE
import com.pilot51.voicenotify.PreferenceHelper.getPrefFlow
import com.pilot51.voicenotify.PreferenceHelper.setPref
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppDatabase.Companion.db
import com.pilot51.voicenotify.ui.AppListViewModel.IgnoreType.*
import com.pilot51.voicenotify.withTimeoutInterruptible
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

class AppListViewModel(application: Application) : AndroidViewModel(application) {
	private val appContext = application.applicationContext
	private val apps by Common::apps
	val filteredApps = apps.toMutableStateList()
	private var isUpdating = false
	private val syncAppsMutex by Common::syncAppsMutex
	var searchQuery by mutableStateOf<String?>(null)
	var showList by mutableStateOf(false)
	private val settingsDao = db.settingsDao
	val packagesWithOverride @Composable get() =
		settingsDao.packagesWithOverride().collectAsState(listOf())

	init {
		updateAppsList()
	}

	fun removeOverrides(app: App) {
		viewModelScope.launch(Dispatchers.IO) {
			settingsDao.deleteByPackage(app.packageName)
		}
	}

	private fun updateAppsList() {
		if (isUpdating) return
		showList = false
		isUpdating = true
		CoroutineScope(Dispatchers.IO).launch {
			syncAppsMutex.withLock {
				apps.clear()
				apps.addAll(db.appDao.getAll())
				val isFirstLoad = apps.isEmpty()
				val packMan = appContext.packageManager
				val installedApps = try {
					withTimeoutInterruptible(10.seconds) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							packMan.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
						} else {
							packMan.getInstalledApplications(0)
						}
					}
				} catch (e: TimeoutCancellationException) {
					Log.e(TAG, "Timed out fetching list of installed apps")
					return@withLock
				}

				// Remove uninstalled
				val appIter = apps.iterator()
				for (app in appIter) {
					if (installedApps.none { it.packageName == app.packageName }) {
						if (!isFirstLoad) app.remove()
						appIter.remove()
					}
				}

				// Add new
				for (appInfo in installedApps) {
					if (apps.any { it.packageName == appInfo.packageName }) {
						continue
					}
					val label = try {
						withTimeoutInterruptible(1.seconds) {
							appInfo.loadLabel(packMan)
						}.toString()
					} catch (e: TimeoutCancellationException) {
						Log.e(TAG, "Timed out fetching app label for package ${appInfo.packageName}")
						continue
					}
					val app = App(
						packageName = appInfo.packageName,
						label = label,
						isEnabled = appDefaultEnable
					)
					apps.add(app)
					if (!isFirstLoad) app.updateDb()
				}

				// Sort list
				apps.sortWith { app1, app2 -> app1.label.compareTo(app2.label, ignoreCase = true) }

				// Bulk add apps to DB if this is the first load
				if (isFirstLoad) db.appDao.insert(apps)
			}
			isUpdating = false
			filterApps()
			showList = true
		}
	}

	fun filterApps(search: String? = searchQuery) {
		filteredApps.clear()
		filteredApps.addAll(if (search.isNullOrEmpty()) {
			apps.toList()
		} else {
			val prefixString = search.lowercase()
			val newValues = mutableListOf<App>()
			for (app in apps) {
				if (app.label.lowercase().contains(prefixString)
					|| app.packageName.lowercase().contains(prefixString)) {
					newValues.add(app)
				}
			}
			newValues
		})
	}

	fun massIgnore(ignoreType: IgnoreType) {
		if (ignoreType == IGNORE_ALL) appDefaultEnable = false
		else if (ignoreType == IGNORE_NONE) appDefaultEnable = true
		CoroutineScope(Dispatchers.IO).launch {
			syncAppsMutex.withLock {
				if (apps.isEmpty()) return@launch
				apps.forEach {
					setIgnore(it, ignoreType)
				}
				filterApps()
				db.appDao.update(apps)
			}
		}
	}

	fun setIgnore(app: App, ignoreType: IgnoreType) {
		if (!app.isEnabled && (ignoreType == IGNORE_TOGGLE || ignoreType == IGNORE_NONE)) {
			app.setEnabled(true, ignoreType == IGNORE_TOGGLE)
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(appContext, appContext.getString(R.string.app_is_not_ignored, app.label), Toast.LENGTH_SHORT).show()
			}
		} else if (app.isEnabled && (ignoreType == IGNORE_TOGGLE || ignoreType == IGNORE_ALL)) {
			app.setEnabled(false, ignoreType == IGNORE_TOGGLE)
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(appContext, appContext.getString(R.string.app_is_ignored, app.label), Toast.LENGTH_SHORT).show()
			}
		}
		if (ignoreType == IGNORE_TOGGLE) {
			filterApps()
		}
	}

	enum class IgnoreType {
		IGNORE_TOGGLE,
		IGNORE_ALL,
		IGNORE_NONE
	}

	companion object {
		private val TAG = AppListViewModel::class.simpleName
		/** The default enabled value for new apps. */
		var appDefaultEnable =
			runBlocking(Dispatchers.IO) {
				getPrefFlow(KEY_APP_DEFAULT_ENABLE, DEFAULT_APP_DEFAULT_ENABLE).first()
			}
			set(value) {
				field = value
				setPref(KEY_APP_DEFAULT_ENABLE, value)
			}
	}
}
