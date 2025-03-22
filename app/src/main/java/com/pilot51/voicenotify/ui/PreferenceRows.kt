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
package com.pilot51.voicenotify.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.ui.dialog.ConfirmDialog

// Simplified and heavily modified from https://github.com/alorma/Compose-Settings

/**
 * A preference row with click callbacks.
 * [title] and [summary] are required if their respective [titleRes] or [summaryRes] isn't set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreferenceRowLink(
	@StringRes titleRes: Int = 0,
	@StringRes summaryRes: Int = 0,
	title: String = titleRes.takeUnless { it == 0 }?.let { stringResource(it) }!!,
	summary: String = summaryRes.takeUnless { it == 0 }?.let { stringResource(it) }!!,
	enabled: Boolean = true,
	app: App? = null,
	showRemove: Boolean = false,
	onRemove: (() -> Unit)? = null,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)? = null
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(IntrinsicSize.Min)
			.combinedClickable(
				enabled = enabled,
				onClick = onClick,
				onLongClick = onLongClick
			),
		verticalAlignment = Alignment.CenterVertically
	) {
		PreferenceRowScaffold(
			title = title,
			enabled = enabled,
			summary = summary,
			app = app,
			showRemove = showRemove,
			onRemove = onRemove,
		)
	}
}

/**
 * A preference row with a checkbox.
 * If [summaryResOff] is not provided, [summaryResOn] is used for both states.
 */
@Composable
fun PreferenceRowCheckbox(
	@StringRes titleRes: Int,
	@StringRes summaryResOn: Int,
	@StringRes summaryResOff: Int = summaryResOn,
	value: Boolean,
	app: App? = null,
	showRemove: Boolean = false,
	onRemove: (() -> Unit)? = null,
	onChange: (Boolean) -> Unit
) {
	val summaryRes = if (value) summaryResOn else summaryResOff
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(IntrinsicSize.Min)
			.toggleable(
				value = value,
				role = Role.Checkbox,
				onValueChange = {
					onChange(it)
				}
			),
		verticalAlignment = Alignment.CenterVertically
	) {
		PreferenceRowScaffold(
			title = stringResource(titleRes),
			summary = stringResource(summaryRes),
			app = app,
			showRemove = showRemove,
			onRemove = onRemove,
			action = {
				Checkbox(
					checked = value,
					onCheckedChange = {
						onChange(it)
					}
				)
			}
		)
	}
}

@Composable
private fun PreferenceRowScaffold(
	enabled: Boolean = true,
	title: String,
	summary: String,
	app: App? = null,
	showRemove: Boolean = false,
	onRemove: (() -> Unit)? = null,
	action: (@Composable (Boolean) -> Unit)? = null
) {
	var showRemoveDialog by remember { mutableStateOf(false) }
	ListItem(
		modifier = Modifier.defaultMinSize(minHeight = 88.dp),
		headlineContent = {
			ColorWrap(enabled) {
				Text(title)
			}
		},
		supportingContent = {
			ColorWrap(enabled) {
				Text(summary)
			}
		},
		trailingContent = {
			Row(
				modifier = Modifier.fillMaxHeight(),
				verticalAlignment = Alignment.CenterVertically
			) {
				if (showRemove) {
					IconButton(onClick = { showRemoveDialog = true }) {
						Icon(
							imageVector = Icons.Outlined.Cancel,
							contentDescription = stringResource(R.string.remove_override)
						)
					}
				}
				action?.invoke(enabled)
			}
		}
	)
	if (showRemoveDialog && app != null) {
		ConfirmDialog(
			text = stringResource(R.string.remove_override_confirm, title, app.label),
			onConfirm = onRemove!!,
			onDismiss = { showRemoveDialog = false }
		)
	}
}

@Composable
private fun ColorWrap(
	enabled: Boolean,
	content: @Composable () -> Unit
) {
	val color = LocalContentColor.current.let {
		if (enabled) it else it.copy(alpha = 0.6f)
	}
	CompositionLocalProvider(LocalContentColor provides color) {
		content()
	}
}

@VNPreview
@Composable
private fun PreferenceRowLinkPreview(
	@PreviewParameter(BooleanProvider::class) showRemove: Boolean
) {
	AppTheme {
		PreferenceRowLink(
			titleRes = R.string.tts_settings,
			summaryRes = R.string.tts_settings_summary_fail,
			enabled = false,
			showRemove = showRemove,
			onClick = {}
		)
	}
}

@VNPreview
@Composable
private fun PreferenceRowCheckboxPreview(
	@PreviewParameter(BooleanProvider::class) value: Boolean
) {
	AppTheme {
		PreferenceRowCheckbox(
			titleRes = R.string.ignore_groups,
			summaryResOn = R.string.ignore_groups_summary_on,
			summaryResOff = R.string.ignore_groups_summary_off,
			value = value,
			showRemove = value,
			onChange = {}
		)
	}
}
