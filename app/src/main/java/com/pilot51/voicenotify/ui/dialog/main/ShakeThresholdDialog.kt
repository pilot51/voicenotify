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
package com.pilot51.voicenotify.ui.dialog.main

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.pilot51.voicenotify.PreferenceHelper
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.Shake
import com.pilot51.voicenotify.ui.IPreferencesViewModel
import com.pilot51.voicenotify.ui.dialog.TextEditDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun ShakeThresholdDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val value by vm.getShakeThreshold()
	var realtimeShake by remember { mutableIntStateOf(0) }
	var peak by remember { mutableIntStateOf(0) }
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	DisposableEffect(value) {
		val shake = Shake(context)
		scope.launch {
			var peakTimeoutJob: Job? = null
			shake.enable()
			shake.jerk.collect {
				realtimeShake = it.toInt()
				if (realtimeShake > peak) {
					peakTimeoutJob?.cancel()
					peak = realtimeShake
					peakTimeoutJob = launch {
						delay(5.seconds)
						peak = 0
					}
				}
			}
		}
		onDispose {
			shake.disable()
		}
	}
	TextEditDialog(
		titleRes = R.string.shake_to_silence,
		message = stringResource(
			R.string.shake_to_silence_dialog_msg,
			PreferenceHelper.DEFAULT_SHAKE_THRESHOLD,
			realtimeShake,
			peak
		),
		initialText = value.takeIf { it > 0 }?.toString() ?: "",
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) { str ->
		vm.setShakeThreshold(str.toIntOrNull()?.takeIf { it > 0 })
		true
	}
}
