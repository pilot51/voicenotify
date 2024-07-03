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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.util.Locale



@RequiresApi(Build.VERSION_CODES.N)
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


	init {
		updateAppsList()
	}

	fun removeOverrides(app: App) {
		viewModelScope.launch(Dispatchers.IO) {
			settingsDao.deleteByPackage(app.packageName)
		}
	}


	private fun isSystemApp(applicationInfo: ApplicationInfo): Boolean {
		return applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
	}

	/**
	 * get application info use queryIntentActivities method
	 * @param activity
	 * @return
	 */
	private fun getInstalledApps(context: Context, includeSystemApps: Boolean): MutableMap<String, AppInfo> {
		val pm = context.packageManager
		val intent = Intent(Intent.ACTION_MAIN, null)
		intent.addCategory(Intent.CATEGORY_LAUNCHER)
		val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
		val apps = mutableMapOf<String, AppInfo>()
		for (resolveInfo in resolveInfos) {
			// filer system apps
			if (!includeSystemApps && isSystemApp(resolveInfo.activityInfo.applicationInfo)) continue
			apps[resolveInfo.activityInfo.applicationInfo.packageName] = AppInfo(
				resolveInfo.activityInfo.applicationInfo.loadIcon(pm),
				resolveInfo.activityInfo.applicationInfo.loadLabel(pm).toString(),
				resolveInfo.activityInfo.applicationInfo.packageName,
				appDefaultEnable
			)
		}
		return apps
	}

	@RequiresApi(Build.VERSION_CODES.N)
	private fun updateAppsList() {
		if (isUpdating) return
		showList = false
		isUpdating = true
		val collator = Collator.getInstance(Locale.getDefault())
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
				// val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				// 	packMan.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
				// } else {
				// 	packMan.getInstalledApplications(0)
				// }
				var installedApps = Common.getAppsInfo(appContext)



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

				apps.sortWith(compareBy(collator) { AlphabeticIndexHelper.computeSectionName(it.label) + it.label})
				if (isFirstLoad) AppDatabase.db.appDao.upsert(apps)
			}
			isUpdating = false
			filterApps()
			updateAppEnableState()
			showList = true
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

