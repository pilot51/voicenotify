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

import androidx.compose.runtime.mutableStateListOf
import com.pilot51.voicenotify.prefs.PreferenceHelper
import com.pilot51.voicenotify.prefs.PreferenceHelper.LogIgnoredValue.NO_LOG
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object NotifyList {
	const val HISTORY_LIMIT = 100
	val notifyListMutex = Mutex()
	private val _notifyList = mutableStateListOf<NotificationInfo>()
	val notifyList: List<NotificationInfo> = _notifyList
	private val logIgnored = PreferenceHelper.logIgnoredStateFlow
	private val logIgnoredApps = PreferenceHelper.logIgnoredAppsStateFlow

	suspend fun addNotification(info: NotificationInfo) {
		if (logIgnored.value == NO_LOG ||
			(logIgnoredApps.value == NO_LOG && info.ignoreReasons.contains(IgnoreReason.APP))
		) return
		notifyListMutex.withLock {
			if (notifyList.size == HISTORY_LIMIT) {
				_notifyList.removeAt(notifyList.lastIndex)
			}
			_notifyList.add(0, info)
		}
	}

	fun updateInfo(info: NotificationInfo) {
		notifyListMutex.launchWithLock {
			val index = notifyList.indexOf(info).takeUnless { it == -1 } ?: return@launchWithLock
			// Force update to list state by first setting copy
			_notifyList[index] = info.copy()
			// Set back to original to ensure future calls can find it again
			_notifyList[index] = info
		}
	}
}
