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

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.db.Settings
import com.pilot51.voicenotify.ui.IPreferencesViewModel
import com.pilot51.voicenotify.ui.PreferencesPreviewVM
import com.pilot51.voicenotify.ui.VNPreview

enum class QuietTimeMode {
	START, END
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietTimeDialog(
	vm: IPreferencesViewModel,
	mode: QuietTimeMode,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val quietTime = when (mode) {
		QuietTimeMode.START -> settingsCombo.quietStart
		QuietTimeMode.END -> settingsCombo.quietEnd
	} ?: Settings.DEFAULT_QUIET_TIME
	val timePickerState = rememberTimePickerState(
		initialHour = quietTime / 60,
		initialMinute = quietTime % 60
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					val time = timePickerState.hour * 60 + timePickerState.minute
					vm.save(
						when (mode) {
							QuietTimeMode.START -> settings.copy(quietStart = time)
							QuietTimeMode.END -> settings.copy(quietEnd = time)
						}
					)
					onDismiss()
				}
			) {
				Text(stringResource(android.R.string.ok))
			}
		},
		dismissButton = {
			TextButton(onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = {
			Text(
				stringResource(
					if (mode == QuietTimeMode.START) R.string.quiet_start else R.string.quiet_end
				)
			)
		},
		text = {
			TimePicker(timePickerState)
		}
	)
}

@VNPreview
@Composable
private fun QuietTimeDialogPreview() {
	AppTheme {
		QuietTimeDialog(PreferencesPreviewVM, QuietTimeMode.START) {}
	}
}
