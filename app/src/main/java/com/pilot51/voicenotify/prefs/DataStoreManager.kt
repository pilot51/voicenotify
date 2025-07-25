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
package com.pilot51.voicenotify.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pilot51.voicenotify.VNApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
object DataStoreManager {
	const val DS_NAME = "prefs"
	private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _dataStoreFlow =
		MutableStateFlow<DataStore<Preferences>?>(VNApplication.Companion.appContext.createDataStore())
	private val dataStoreFlow = _dataStoreFlow.filterNotNull()
	val dataStore get() = _dataStoreFlow.value ?: runBlocking(Dispatchers.IO) {
		withTimeoutOrNull(1.seconds) { dataStoreFlow.first() }
	}!!
	val dataFlow = dataStoreFlow.flatMapLatest { it.data }

	fun openDataStore() {
		_dataStoreFlow.value = VNApplication.Companion.appContext.createDataStore()
	}

	fun closeDataStore() {
		_dataStoreFlow.value = null
		scope.cancel()
	}

	private fun Context.createDataStore(): DataStore<Preferences> {
		if (!scope.isActive) {
			scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		}
		return PreferenceDataStoreFactory.create(
			scope = scope,
			produceFile = { preferencesDataStoreFile(DS_NAME) }
		)
	}

	fun <T> getPrefFlow(key: Preferences.Key<T>, default: T) =
		dataFlow.mapLatest { it[key] ?: default }

	fun <T> getPrefStateFlow(key: Preferences.Key<T>, default: T) =
		getPrefFlow(key, default).stateIn(
			scope = CoroutineScope(Dispatchers.IO),
			started = SharingStarted.Companion.Eagerly,
			initialValue = default
		)

	/** Sets the value of the preference for [key], or removes it if [newValue] is `null`. */
	fun <T> setPref(key: Preferences.Key<T>, newValue: T?) {
		CoroutineScope(Dispatchers.IO).launch {
			dataStore.edit { pref ->
				newValue?.let { pref[key] = it } ?: pref.remove(key)
			}
		}
	}
}