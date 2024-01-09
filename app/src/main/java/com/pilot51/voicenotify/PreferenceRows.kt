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

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.pilot51.voicenotify.PreferenceHelper.prefs
import kotlin.reflect.KProperty

// Simplified and heavily modified from https://github.com/alorma/Compose-Settings

@Composable
fun PreferenceRowLink(
	@StringRes title: Int,
	@StringRes subtitle: Int,
	enabled: Boolean = true,
	onClick: () -> Unit
) = PreferenceRowLink(
	title = stringResource(title),
	subtitle = stringResource(subtitle),
	enabled = enabled,
	onClick = onClick
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreferenceRowLink(
	title: String,
	subtitle: String,
	enabled: Boolean = true,
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
			subtitle = subtitle
		)
	}
}

@Composable
fun PreferenceRowCheckbox(
	@StringRes title: Int,
	@StringRes subtitle: Int,
	key: String,
	default: Boolean
) {
	val isPreview = LocalInspectionMode.current
	var prefValue by remember {
		PreferenceBooleanState(key = if (isPreview) "isPreview" else key, defaultValue = default)
	}
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(IntrinsicSize.Min)
			.toggleable(
				value = prefValue,
				role = Role.Checkbox,
				onValueChange = { prefValue = !prefValue }
			),
		verticalAlignment = Alignment.CenterVertically
	) {
		PreferenceRowScaffold(
			title = stringResource(title),
			subtitle = stringResource(subtitle),
			action = {
				Checkbox(
					checked = prefValue,
					onCheckedChange = { prefValue = it }
				)
			}
		)
	}
}

@Composable
private fun PreferenceRowScaffold(
	enabled: Boolean = true,
	title: String,
	subtitle: String,
	action: (@Composable (Boolean) -> Unit)? = null
) {
	ListItem(
		modifier = Modifier.defaultMinSize(minHeight = 88.dp),
		headlineContent = {
			ColorWrap(enabled) {
				Text(title)
			}
		},
		supportingContent = {
			ColorWrap(enabled) {
				Text(subtitle)
			}
		},
		trailingContent = action?.run {
			{
				Row(
					modifier = Modifier.fillMaxHeight(),
					verticalAlignment = Alignment.CenterVertically
				) {
					action(enabled)
				}
			}
		}
	)
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

private class PreferenceBooleanState(
	val key: String,
	val defaultValue: Boolean
) {
	private var _value by mutableStateOf(
		if (key == "isPreview") defaultValue else prefs.getBoolean(key, defaultValue)
	)
	var value: Boolean
		set(value) {
			_value = value
			prefs.edit { putBoolean(key, value) }
		}
		get() = _value
}

@Suppress("NOTHING_TO_INLINE")
private inline operator fun PreferenceBooleanState.getValue(
	thisObj: Any?,
	property: KProperty<*>
): Boolean = value

@Suppress("NOTHING_TO_INLINE")
private inline operator fun PreferenceBooleanState.setValue(
	thisObj: Any?,
	property: KProperty<*>,
	value: Boolean
) {
	this.value = value
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SettingsMenuLinkPreview() {
	AppTheme {
		PreferenceRowLink(
			title = R.string.tts_settings,
			subtitle = R.string.tts_settings_summary_fail,
			enabled = false,
			onClick = {}
		)
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SettingsCheckboxCheckedPreview() {
	AppTheme {
		PreferenceRowCheckbox(
			title = R.string.ignore_groups,
			subtitle = R.string.ignore_groups_summary_on,
			key = "isPreview",
			default = true
		)
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SettingsCheckboxUncheckedPreview() {
	AppTheme {
		PreferenceRowCheckbox(
			title = R.string.ignore_groups,
			subtitle = R.string.ignore_groups_summary_off,
			key = "isPreview",
			default = false
		)
	}
}
