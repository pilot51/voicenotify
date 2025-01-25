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
package com.pilot51.voicenotify.ui.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.R

@Composable
fun ConfirmDialog(
	text: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = {
				onConfirm()
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
		text = {
			Text(
				text = text,
				modifier = Modifier.fillMaxWidth(),
				fontSize = 20.sp,
				textAlign = TextAlign.Center
			)
		}
	)
}
