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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.db
import com.pilot51.voicenotify.prefs.db.AppDatabase.Companion.settingsDaoFlow
import com.pilot51.voicenotify.prefs.db.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class AppListViewModel(application: Application) : AndroidViewModel(application) {
	val searchQuery = MutableStateFlow<String?>(null)
	val filteredApps = combine(AppRepository.appsFlow, searchQuery) { apps, search ->
		if (search.isNullOrEmpty()) apps else {
			val query = search.lowercase()
			apps.filter {
				it.label.lowercase().contains(query)
					|| it.packageName.lowercase().contains(query)
			}
		}
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5.seconds),
		initialValue = emptyList()
	)
	@OptIn(ExperimentalCoroutinesApi::class)
	val packagesWithOverride @Composable get() =
		settingsDaoFlow.flatMapLatest { it.packagesWithOverride() }.collectAsState(listOf())
	val isLoading = AppRepository.isUpdating

	init {
		updateAppsList()
	}

	private fun updateAppsList() {
		AppRepository.updateAppsList()
	}

	fun removeOverrides(app: App) {
		viewModelScope.launch(Dispatchers.IO) {
			db.settingsDao.deleteByPackage(app.packageName)
		}
	}

	fun filterApps(search: String?) {
		searchQuery.value = search
	}

	fun massIgnore(enable: Boolean) {
		AppRepository.massIgnore(enable)
	}

	fun toggleIgnore(app: App) {
		AppRepository.toggleIgnore(app)
	}
}
