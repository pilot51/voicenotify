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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.BuildConfig
import com.pilot51.voicenotify.PreferenceHelper
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.VNPreview

@Composable
fun BackupDialog(onDismiss: () -> Unit) {
	val exportBackupLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.CreateDocument("application/zip")
	) {
		it?.let { PreferenceHelper.exportBackup(it) }
		onDismiss()
	}
	val importBackupLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) {
		it?.let { PreferenceHelper.importBackup(it) }
		onDismiss()
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = { Text(stringResource(R.string.backup_restore)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
				Text(stringResource(R.string.backup_restore_message))
				TextButton(onClick = {
					val version = BuildConfig.VERSION_NAME
						.replace(" ", "-").replace(Regex("[\\[\\]]"), "")
					exportBackupLauncher.launch("voice_notify_${version}_backup.zip")
				}) {
					Text(stringResource(R.string.backup_settings))
				}
				TextButton(onClick = {
					importBackupLauncher.launch(arrayOf("application/zip"))
				}) {
					Text(stringResource(R.string.restore_settings))
				}
			}
		}
	)
}

@VNPreview
@Composable
private fun BackupDialogPreview() {
	AppTheme {
		BackupDialog {}
	}
}
