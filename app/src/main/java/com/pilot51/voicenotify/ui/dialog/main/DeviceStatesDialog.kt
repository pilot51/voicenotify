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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.db.Settings
import com.pilot51.voicenotify.ui.IPreferencesViewModel
import com.pilot51.voicenotify.ui.PreferencesPreviewVM
import com.pilot51.voicenotify.ui.VNPreview

@Composable
fun DeviceStatesDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val items = stringArrayResource(R.array.device_states)
	val values = remember(settings) {
		mutableStateListOf(
			settingsCombo.speakScreenOff ?: Settings.DEFAULT_SPEAK_SCREEN_OFF,
			settingsCombo.speakScreenOn ?: Settings.DEFAULT_SPEAK_SCREEN_ON,
			settingsCombo.speakHeadsetOff ?: Settings.DEFAULT_SPEAK_HEADSET_OFF,
			settingsCombo.speakHeadsetOn ?: Settings.DEFAULT_SPEAK_HEADSET_ON,
			settingsCombo.speakSilentOn ?: Settings.DEFAULT_SPEAK_SILENT_ON
		)
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					vm.save(
						settings.copy(
							speakScreenOff = values[0],
							speakScreenOn = values[1],
							speakHeadsetOff = values[2],
							speakHeadsetOn = values[3],
							speakSilentOn = values[4]
						)
					)
					onDismiss()
				}
			) {
				Text(stringResource(android.R.string.ok))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = { Text(stringResource(R.string.device_state_dialog_title)) },
		text = {
			LazyColumn {
				itemsIndexed(items) { index, item ->
					val isChecked = values[index]
					Row(
						modifier = Modifier
							.toggleable(
								value = isChecked,
								onValueChange = { values[index] = it },
								role = Role.Checkbox
							)
							.fillMaxWidth()
							.heightIn(min = 56.dp)
							.wrapContentHeight(align = Alignment.CenterVertically)
							.padding(horizontal = 16.dp),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Checkbox(
							checked = isChecked,
							onCheckedChange = null
						)
						Text(
							text = item,
							fontSize = 16.sp
						)
					}
				}
			}
		}
	)
}

@VNPreview
@Composable
private fun DeviceStatesDialogPreview() {
	AppTheme {
		DeviceStatesDialog(PreferencesPreviewVM) {}
	}
}
