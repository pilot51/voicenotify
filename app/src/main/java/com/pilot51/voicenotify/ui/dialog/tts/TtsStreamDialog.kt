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
package com.pilot51.voicenotify.ui.dialog.tts

import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
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
fun TtsStreamDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val streamNames = stringArrayResource(R.array.stream_name)
	val streamValues = arrayOf(
		AudioManager.STREAM_MUSIC,
		AudioManager.STREAM_NOTIFICATION,
		AudioManager.STREAM_VOICE_CALL,
		AudioManager.STREAM_RING,
		AudioManager.STREAM_ALARM
	)
	val savedValue = remember(settings) { settings.ttsStream }
	var value by remember(settings) {
		mutableIntStateOf(settingsCombo.ttsStream ?: Settings.DEFAULT_TTS_STREAM)
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					if (value != savedValue) {
						vm.save(settings.copy(ttsStream = value))
					}
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
		title = { Text(stringResource(R.string.tts_stream)) },
		text = {
			LazyColumn {
				itemsIndexed(streamNames) { index, item ->
					val isCurrent = value == streamValues[index]
					Row(
						modifier = Modifier
							.toggleable(
								value = isCurrent,
								onValueChange = {
									if (!it) return@toggleable
									value = streamValues[index]
								},
								role = Role.RadioButton
							)
							.fillMaxWidth()
							.heightIn(min = 56.dp)
							.wrapContentHeight(align = Alignment.CenterVertically)
							.padding(horizontal = 16.dp),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						RadioButton(
							selected = isCurrent,
							onClick = null
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
private fun TtsStreamDialogPreview() {
	AppTheme {
		TtsStreamDialog(PreferencesPreviewVM) {}
	}
}
