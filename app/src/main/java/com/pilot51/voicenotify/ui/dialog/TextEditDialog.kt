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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.VNPreview

/**
 * A dialog with a text field.
 * [title] and [message] are required if their respective [titleRes] or [messageRes] isn't set.
 */
@Composable
fun TextEditDialog(
	@StringRes titleRes: Int = 0,
	@StringRes messageRes: Int = 0,
	title: String = titleRes.takeUnless { it == 0 }?.let { stringResource(it) }!!,
	message: String = messageRes.takeUnless { it == 0 }?.let { stringResource(it) }!!,
	initialText: String,
	keyboardType: KeyboardType = KeyboardOptions.Default.keyboardType,
	onDismiss: () -> Unit,
	onSave: (text: String) -> Boolean
) {
	var textValue by remember(initialText) { mutableStateOf(initialText) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					if (onSave(textValue)) {
						onDismiss()
					}
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
		title = { Text(title) },
		text = {
			val decimalPattern = remember { Regex("^\\d*\\.?\\d*\$") }
			Column(
				verticalArrangement = Arrangement.spacedBy(20.dp)
			) {
				Text(
					text = message,
					modifier = Modifier
						.weight(1f, false)
						.verticalScroll(rememberScrollState())
				)
				TextField(
					value = textValue,
					onValueChange = {
						val isValid = when (keyboardType) {
							KeyboardType.Number -> it.isDigitsOnly()
							KeyboardType.Decimal -> it.matches(decimalPattern)
							else -> true
						}
						if (isValid) textValue = it
					},
					keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
				)
			}
		}
	)
}

@VNPreview
@Composable
private fun TextEditDialogPreview() {
	AppTheme {
		TextEditDialog(
			titleRes = R.string.ignore_strings,
			messageRes = R.string.require_ignore_strings_dialog_msg,
			initialText = "text value",
			onDismiss = {},
			onSave = { true }
		)
	}
}
