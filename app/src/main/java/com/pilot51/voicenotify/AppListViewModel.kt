/*
 * Copyright 2011-2023 Mark Injerd
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

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import com.pilot51.voicenotify.AppListViewModel.IgnoreType.*
import com.pilot51.voicenotify.PreferenceHelper.KEY_APP_DEFAULT_ENABLE
import com.pilot51.voicenotify.PreferenceHelper.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class AppListViewModel(application: Application) : AndroidViewModel(application) {
	private val appContext = application.applicationContext
	private val apps by Common::apps
	val filteredApps = apps.toMutableStateList()
	private var isUpdating = false
	private val syncAppsMutex by Common::syncAppsMutex
	var searchQuery by mutableStateOf<String?>(null)
	var showList by mutableStateOf(false)

	init {
		updateAppsList()
	}

	private fun updateAppsList() {
		if (isUpdating) return
		showList = false
		isUpdating = true
		CoroutineScope(Dispatchers.IO).launch {
			syncAppsMutex.withLock {
				apps.clear()
				apps.addAll(AppDatabase.db.appDao.getAll())
				val isFirstLoad = apps.isEmpty()
				val packMan = appContext.packageManager

				// Remove uninstalled
				for (a in apps.indices.reversed()) {
					val app = apps[a]
					try {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							packMan.getApplicationInfo(app.packageName, PackageManager.ApplicationInfoFlags.of(0L))
						} else {
							packMan.getApplicationInfo(app.packageName, 0)
						}
					} catch (e: PackageManager.NameNotFoundException) {
						if (!isFirstLoad) app.remove()
						apps.removeAt(a)
					}
				}

				// Add new
				val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					packMan.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
				} else {
					packMan.getInstalledApplications(0)
				}
				inst@ for (appInfo in installedApps) {
					for (app in apps) {
						if (app.packageName == appInfo.packageName) {
							continue@inst
						}
					}
					val app = App(
						packageName = appInfo.packageName,
						label = appInfo.loadLabel(packMan).toString(),
						isEnabled = appDefaultEnable
					)
					apps.add(app)
					if (!isFirstLoad) app.updateDb()
				}
				apps.sortWith { app1, app2 -> app1.label.compareTo(app2.label, ignoreCase = true) }
				if (isFirstLoad) AppDatabase.db.appDao.upsert(apps)
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
				for (app in apps) {
					setIgnore(app, ignoreType)
				}
				filterApps()
			}
			AppDatabase.db.appDao.upsert(apps)
		}
	}

	fun setIgnore(app: App, ignoreType: IgnoreType) {
		if (!app.enabled && (ignoreType == IGNORE_TOGGLE || ignoreType == IGNORE_NONE)) {
			app.setEnabled(true, ignoreType == IGNORE_TOGGLE)
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(appContext, appContext.getString(R.string.app_is_not_ignored, app.label), Toast.LENGTH_SHORT).show()
			}
		} else if (app.enabled && (ignoreType == IGNORE_TOGGLE || ignoreType == IGNORE_ALL)) {
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
		/** The default enabled value for new apps. */
		var appDefaultEnable = prefs.getBoolean(KEY_APP_DEFAULT_ENABLE, PreferenceHelper.DEFAULT_APP_DEFAULT_ENABLE)
			set(value) {
				field = value
				prefs.edit().putBoolean(KEY_APP_DEFAULT_ENABLE, value).apply()
			}
	}
}
