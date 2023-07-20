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

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Pair
import androidx.compose.runtime.mutableStateListOf
import com.pilot51.voicenotify.AppListViewModel.Companion.appDefaultEnable
import com.pilot51.voicenotify.PreferenceHelper.getSelectedAudioStream
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object Common {
	/** Sets the volume control stream defined in preferences. */
	fun setVolumeStream(activity: Activity) {
		activity.volumeControlStream = getSelectedAudioStream()
	}

	val notificationListenerSettingsIntent: Intent by lazy {
		Intent(
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
				Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
			} else "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
		)
	}

	val apps = mutableStateListOf<App>()
	val syncAppsMutex = Mutex()

	/**
	 * @param pkg Package name used to find [App] in current list or create a new one from system.
	 * @return Found or created [App], otherwise null if app not found on system.
	 */
	fun findOrAddApp(pkg: String): App? {
		return runBlocking(Dispatchers.IO) {
			syncAppsMutex.withLock {
				if (apps.isEmpty()) {
					apps.addAll(AppDatabase.db.appDao.getAll())
				}
				for (app in apps) {
					if (app.packageName == pkg) {
						return@runBlocking app
					}
				}
				return@runBlocking try {
					val packMan = appContext.packageManager
					val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
						packMan.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
					} else {
						packMan.getApplicationInfo(pkg, 0)
					}
					val app = App(
						packageName = pkg,
						label = appInfo.loadLabel(packMan).toString(),
						isEnabled = appDefaultEnable
					)
					apps.add(app.updateDb())
					app
				} catch (e: PackageManager.NameNotFoundException) {
					e.printStackTrace()
					null
				}
			}
		}
	}

	fun convertTextReplaceListToString(list: List<Pair<String, String>?>): String {
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
		return saveString.toString()
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
