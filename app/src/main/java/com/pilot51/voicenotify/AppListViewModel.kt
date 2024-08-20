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

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.icu.text.Collator
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pilot51.voicenotify.AppListViewModel.IgnoreType.*
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_APP_DEFAULT_ENABLE
import com.pilot51.voicenotify.PreferenceHelper.KEY_APP_DEFAULT_ENABLE
import com.pilot51.voicenotify.PreferenceHelper.getPrefFlow
import com.pilot51.voicenotify.PreferenceHelper.setPref
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppDatabase
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class AppInfo(
	val icon: Drawable,
	val label: String,
	val packageName: String,
	val isEnabled: Boolean
)


class AppListViewModel(application: Application) : AndroidViewModel(application) {
	private val appContext = application.applicationContext
	private val apps by Common::apps
	val filteredApps = apps.toMutableStateList()
	private var isUpdating = false
	private val syncAppsMutex by Common::syncAppsMutex
	var searchQuery by mutableStateOf<String?>(null)
	var showList by mutableStateOf(false)
	private val settingsDao = AppDatabase.db.settingsDao
	val packagesWithOverride @Composable get() =
		settingsDao.packagesWithOverride().collectAsState(listOf())
	var appEnable by mutableStateOf(false)
	// mutableMap
	var appListLocalMap = mutableMapOf<String, Char>(
		"" to ' '
	)


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
				val collator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					Collator.getInstance(Locale.getDefault())
				} else {
					TODO("VERSION.SDK_INT < N")
				}
				// Use a HashSet to quickly check if an app is already in the list
				val existingAppSet = apps.map { it.packageName }.toHashSet()



				// Fetch installed apps
				val installedApps = Common.getAppsInfo(appContext)

// 				val packMan = appContext.packageManager
//				val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//					packMan.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
//				} else {
//					packMan.getInstalledApplications(0)
//				}

				// Remove uninstalled apps
				apps.retainAll { app ->
					try {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
							appContext.packageManager.getApplicationInfo(app.packageName, PackageManager.ApplicationInfoFlags.of(0L))
						} else {
							appContext.packageManager.getApplicationInfo(app.packageName, 0)
						}
						true
					} catch (e: PackageManager.NameNotFoundException) {
						app.remove()
						false
					}
				}
				// Remove uninstalled apps
//				apps.retainAll { app ->
//					installedApps.any { it.packageName == app.packageName }
//				}

				// Add new apps
				val newApps = installedApps.filter { appInfo ->
					!existingAppSet.contains(appInfo.packageName)
				}.map { appInfo ->
					App(
						packageName = appInfo.packageName,
						label = appInfo.loadLabel(appContext.packageManager).toString(),
						isEnabled = appDefaultEnable
					).apply {
						if (apps.isNotEmpty()) updateDb()
					}
				}

				// Update the app list
				apps.addAll(newApps)


				appListLocalMap = apps.associateBy({ it.packageName }, { AlphabeticIndexHelper.computeSectionName(it.label).uppercase().first() }).toMutableMap()


				apps.sortWith(compareBy(collator) { appListLocalMap[it.packageName].toString() + it.label })

				// Batch update the database if it's the first load
				if (apps.isEmpty()) AppDatabase.db.appDao.upsert(apps)

				isUpdating = false
				filterApps()
				updateAppEnableState()
				showList = true
			}
		}
	}


	private fun updateAppEnableState() {
		appEnable = apps.all { it.enabled }
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
		appEnable = appDefaultEnable
		CoroutineScope(Dispatchers.IO).launch {
			syncAppsMutex.withLock {
				if (apps.isEmpty()) return@launch
				for (app in apps) {
					setIgnore(app, ignoreType)
				}
				updateAppEnableState()
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

		updateAppEnableState()
	}

	enum class IgnoreType {
		IGNORE_TOGGLE,
		IGNORE_ALL,
		IGNORE_NONE
	}

	companion object {
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



