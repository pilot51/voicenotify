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
package com.pilot51.voicenotify.ui.dialog.main.support

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.PreferencesViewModel
import com.pilot51.voicenotify.ui.VNPreview
import com.pilot51.voicenotify.ui.debugLogPath

@Composable
fun EmailDialog(onDismiss: () -> Unit) {
	val context = LocalContext.current
	val isLogChecked = remember { mutableStateOf(false) }
	val isSettingsChecked = remember { mutableStateOf(false) }
	var showViewLogDialog by remember { mutableStateOf(false) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					PreferencesViewModel.sendEmail(context, isLogChecked.value, isSettingsChecked.value)
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
		title = { Text(stringResource(R.string.support_email_dialog_title)) },
		text = {
			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
				Text(
					text = stringResource(R.string.support_email_dialog_message),
					modifier = Modifier.fillMaxWidth(),
					fontSize = 20.sp
				)
				Row(
					modifier = Modifier.padding(top = 16.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					TextCheckbox(
						textRes = R.string.debug_log,
						checkedState = isLogChecked,
						modifier = Modifier.weight(1f)
					)
					TextButton(
						onClick = { showViewLogDialog = true }
					) {
						Text(
							text = stringResource(R.string.view),
							fontSize = 20.sp
						)
					}
				}
				Text(
					text = stringResource(
						R.string.support_email_dialog_message_log,
						debugLogPath
					),
					modifier = Modifier.fillMaxWidth(),
					fontSize = 20.sp
				)
				TextCheckbox(
					textRes = R.string.settings,
					checkedState = isSettingsChecked,
					modifier = Modifier.padding(top = 16.dp)
				)
				Text(
					text = stringResource(R.string.support_email_dialog_message_settings),
					modifier = Modifier.fillMaxWidth(),
					fontSize = 20.sp
				)
			}
		}
	)
	if (showViewLogDialog) {
		val logLines = remember { mutableStateListOf<String>() }
		val scope = rememberCoroutineScope()
		var isReading by remember { mutableStateOf(true) }
		LaunchedEffect(Unit) {
			PreferencesViewModel.readDebugLog(
				scope = scope,
				onReadLine = { logLines.add(it) },
				onDone = { isReading = false }
			)
		}
		AlertDialog(
			onDismissRequest = { showViewLogDialog = false },
			confirmButton = { },
			dismissButton = {
				TextButton(onClick = { showViewLogDialog = false }) {
					Text(stringResource(android.R.string.ok))
				}
			},
			title = { Text(stringResource(R.string.debug_log)) },
			text = {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center
				) {
					if (isReading) {
						CircularProgressIndicator()
					} else if (logLines.isEmpty()) {
						Text(
							text = stringResource(R.string.view_debug_log_failed),
							fontSize = 20.sp
						)
					} else {
						val listState = rememberLazyListState(logLines.lastIndex)
						LazyColumn(
							modifier = Modifier.fillMaxSize(),
							state = listState
						) {
							items(logLines) { Text(it) }
						}
					}
				}
			}
		)
	}
}

@Composable
private fun TextCheckbox(
	@StringRes textRes: Int,
	checkedState: MutableState<Boolean>,
	modifier: Modifier = Modifier
) {
	var isChecked by checkedState
	Row(
		modifier = modifier
			.toggleable(
				value = isChecked,
				onValueChange = { isChecked = it },
				role = Role.Checkbox
			)
			.heightIn(min = 56.dp)
			.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Checkbox(
			checked = isChecked,
			onCheckedChange = null
		)
		Text(
			text = stringResource(textRes),
			fontSize = 22.sp,
			fontWeight = FontWeight.Bold
		)
	}
}

@VNPreview
@Composable
private fun EmailDialogPreview() {
	AppTheme {
		EmailDialog {}
	}
}
