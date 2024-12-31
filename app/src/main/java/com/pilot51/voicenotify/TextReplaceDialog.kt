/*
 * Copyright 2011-2024 Mark Injerd
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
package com.pilot51.voicenotify

import android.util.Pair
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.PreferencesViewModel.Companion.isDuplicate
import com.pilot51.voicenotify.PreferencesViewModel.Companion.updateListItem

/**
 * Dialog that provides a dynamic list with two
 * text fields in each row for defining text replacement.
 */
@Composable
fun TextReplaceDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val context = LocalContext.current
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val savedList = vm.getTtsTextReplace(settingsCombo)
	val replaceList = remember(savedList) {
		mutableStateListOf<Pair<String, String>?>().apply {
			addAll(savedList)
			add(null)
		}
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					val isValid = replaceList.all { pair ->
						pair?.first?.let { validateRegexOption(it) } ?: true
					}
					if (isValid) {
						vm.saveTtsTextReplace(settings, replaceList)
						onDismiss()
					} else {
						Toast.makeText(context, R.string.invalid_regex, Toast.LENGTH_LONG).show()
					}
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
		title = { Text(stringResource(R.string.tts_text_replace)) },
		text = {
			TextReplaceList(
				replaceList = replaceList
			)
		}
	)
}

@Composable
private fun TextReplaceList(replaceList: MutableList<Pair<String, String>?>) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = stringResource(R.string.tts_text_replace_dialog) + stringResource(R.string.regex_message),
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 20.dp),
			color = MaterialTheme.colorScheme.secondary,
			style = MaterialTheme.typography.bodyMedium
		)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Text(
				text = stringResource(R.string.text_to_replace),
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				textAlign = TextAlign.Center
			)
			Text(
				text = stringResource(R.string.replacement_text),
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f),
				textAlign = TextAlign.Center
			)
			Spacer(modifier = Modifier.width(48.dp))
		}
		LazyColumn(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			itemsIndexed(replaceList) { index, item ->
				TextReplaceListItem(
					pair = item,
					index = index,
					replaceList = replaceList
				)
			}
		}
	}
}

@Composable
private fun TextReplaceListItem(
	pair: Pair<String, String>?,
	index: Int,
	replaceList: MutableList<Pair<String, String>?>
) {
	val focusManager = LocalFocusManager.current
	var editFrom by remember(pair) { mutableStateOf(pair?.first ?: "") }
	var editTo by remember(pair) { mutableStateOf(pair?.second ?: "") }
	var isError by remember(replaceList) { mutableStateOf(isDuplicate(index, editFrom, replaceList)) }
	/**
	 * Used to prevent onFocusChange of the EditTexts from erroneously updating
	 * data with old row information after data has changed from onClick of the remove button.
	 */
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(10.dp)
	) {
		TextField(
			value = editFrom,
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
			isError = isError,
			supportingText = if (isError) {{
				Text(
					modifier = Modifier.fillMaxWidth(),
					text = stringResource(R.string.text_replace_error_duplicate),
					color = MaterialTheme.colorScheme.error
				)
			}} else null,
			trailingIcon = if (isError) {{
				Icon(
					imageVector = Icons.Filled.Error,
					contentDescription = stringResource(R.string.error),
					tint = MaterialTheme.colorScheme.error
				)
			}} else null,
			onValueChange = {
				editFrom = it
				updateListItem(index, editFrom, editTo, replaceList)
				isError = isDuplicate(index, editFrom, replaceList)
			}
		)
		TextField(
			value = editTo,
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = {
				if (index != replaceList.size - 1) {
					focusManager.moveFocus(FocusDirection.Next)
				}
			}),
			onValueChange = {
				editTo = it
				updateListItem(index, editFrom, editTo, replaceList)
			}
		)
		IconButton(
			modifier = Modifier.align(Alignment.CenterVertically),
			enabled = pair != null,
			colors = IconButtonDefaults.iconButtonColors(disabledContentColor = Color.Transparent),
			onClick = {
				editFrom = ""
				editTo = ""
				replaceList.removeAt(index)
			}
		) {
			Icon(
				painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
				contentDescription = stringResource(R.string.remove)
			)
		}
	}
}

@VNPreview
@Composable
private fun TextReplaceListPreview() {
	AppTheme {
		TextReplaceDialog(PreferencesPreviewVM) {}
	}
}
