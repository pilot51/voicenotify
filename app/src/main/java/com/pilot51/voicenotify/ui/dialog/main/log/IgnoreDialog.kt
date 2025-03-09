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
package com.pilot51.voicenotify.ui.dialog.main.log

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.AppRepository
import com.pilot51.voicenotify.ui.VNPreview

@Composable
fun IgnoreDialog(
	app: App,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = {
				AppRepository.toggleIgnore(app)
				onDismiss()
			}) {
				Text(stringResource(R.string.yes))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = {
			Text(
				text = stringResource(
					if (app.isEnabled) R.string.ignore_app else R.string.unignore_app,
					app.label
				),
				modifier = Modifier.fillMaxWidth(),
				textAlign = TextAlign.Center
			)
		}
	)
}

@VNPreview
@Composable
private fun IgnoreDialogPreview() {
	val app = App("package.name.one", "App Name 1", true)
	AppTheme {
		IgnoreDialog(app) {}
	}
}
