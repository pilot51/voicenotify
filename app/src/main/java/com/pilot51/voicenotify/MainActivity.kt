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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_STREAM
import com.pilot51.voicenotify.PreferenceHelper.prefs

class MainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Common.setVolumeStream(this)
		setContent {
			AppTheme {
				AppMain()
			}
		}
	}

	override fun onResume() {
		super.onResume()
		prefs.registerOnSharedPreferenceChangeListener(this)
	}

	override fun onPause() {
		prefs.unregisterOnSharedPreferenceChangeListener(this)
		super.onPause()
	}

	override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
		if (key == KEY_TTS_STREAM) {
			Common.setVolumeStream(this)
		}
	}
}
