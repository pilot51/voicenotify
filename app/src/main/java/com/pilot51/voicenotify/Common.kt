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
package com.pilot51.voicenotify

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppDatabase.Companion.db
import com.pilot51.voicenotify.ui.AppListViewModel.Companion.appDefaultEnable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

object Common {
	private val TAG = Common::class.simpleName

	val notificationListenerSettingsIntent get() = Intent(
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
			Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
		} else "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
	).apply {
		// Highlight Voice Notify when settings opens
		val serviceId = ComponentName(appContext, Service::class.java).flattenToShortString()
		val args = Bundle().apply { putString(":settings:fragment_args_key", serviceId) }
		putExtra(":settings:show_fragment_args", args)
	}

	val apps = mutableStateListOf<App>()
	val syncAppsMutex = Mutex()

	/**
	 * @param pkg Package name used to find [App] in current list or create a new one from system.
	 * @return Found or created [App], otherwise null if app not found on system.
	 */
	suspend fun findOrAddApp(pkg: String): App? {
		syncAppsMutex.withLock {
			if (apps.isEmpty()) {
				apps.addAll(db.appDao.getAll())
			}
			apps.find { it.packageName == pkg }?.let {
				return it
			}
			val appLabel = try {
				withTimeoutInterruptible(2.seconds) {
					val packMan = appContext.packageManager
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
						packMan.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
					} else {
						packMan.getApplicationInfo(pkg, 0)
					}.run {
						loadLabel(packMan).toString()
					}
				}
			} catch (e: NameNotFoundException) {
				Log.w(TAG, "App not found for package $pkg")
				return null
			} catch (e: TimeoutCancellationException) {
				Log.e(TAG, "Timed out fetching app info/label for package $pkg")
				return null
			}
			return App(
				packageName = pkg,
				label = appLabel,
				isEnabled = appDefaultEnable
			).apply {
				apps.add(updateDb())
			}
		}
	}
}
