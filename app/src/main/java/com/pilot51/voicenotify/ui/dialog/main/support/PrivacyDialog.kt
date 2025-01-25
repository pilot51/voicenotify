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

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.Constants
import com.pilot51.voicenotify.NotifyList
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.VNPreview
import com.pilot51.voicenotify.ui.debugLogPath

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { },
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = { Text(stringResource(R.string.support_privacy)) },
		text = {
			Text(
				text = stringResource(
					R.string.support_privacy_message,
					NotifyList.HISTORY_LIMIT, debugLogPath, Constants.DEV_EMAIL
				),
				modifier = Modifier.verticalScroll(rememberScrollState())
			)
		}
	)
}

@VNPreview
@Composable
private fun PrivacyDialogPreview() {
	AppTheme {
		PrivacyDialog {}
	}
}
