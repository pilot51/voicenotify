/*
 * Copyright 2011-2023 Mark Injerd
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

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.pilot51.voicenotify.PreferenceHelper.KEY_IGNORE_GROUPS
import com.pilot51.voicenotify.PreferenceHelper.prefs
import kotlin.reflect.KProperty

// Simplified and heavily modified from https://github.com/alorma/Compose-Settings

@Composable
fun PreferenceRowLink(
	@StringRes title: Int,
	@StringRes subtitle: Int,
	onClick: () -> Unit
) {
	PreferenceRowLink(
		title = stringResource(title),
		subtitle = stringResource(subtitle),
		onClick = onClick
	)
}

@Composable
fun PreferenceRowLink(
	enabled: Boolean = true,
	title: String,
	subtitle: String,
	onClick: () -> Unit
) {
	Surface {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable(
					enabled = enabled,
					onClick = onClick
				),
			verticalAlignment = Alignment.CenterVertically
		) {
			PreferenceRowScaffold(
				title = title,
				enabled = enabled,
				subtitle = subtitle,
				actionDivider = true
			)
		}
	}
}

@Composable
fun PreferenceRowCheckbox(
	@StringRes title: Int,
	@StringRes subtitle: Int,
	key: String,
	default: Boolean
) {
	var prefValue by remember {
		PreferenceBooleanState(key = key, defaultValue = default)
	}
	Row(
		modifier = Modifier
			.fillMaxWidth()
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
	action: (@Composable (Boolean) -> Unit)? = null,
	actionDivider: Boolean = false
) {
	ListItem(
		modifier = Modifier
			.height(IntrinsicSize.Min)
			.defaultMinSize(minHeight = 88.dp),
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
					if (actionDivider) {
						Divider(
							color = DividerDefaults.color.let {
								if (enabled) it else it.copy(alpha = 0.6f)
							},
							modifier = Modifier
								.padding(vertical = 4.dp)
								.fillMaxHeight()
								.width(1.dp),
						)
						Spacer(modifier = Modifier.width(2.dp))
					}
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

class PreferenceBooleanState(
	val key: String,
	val defaultValue: Boolean
) {
	private var _value by mutableStateOf(prefs.getBoolean(key, defaultValue))
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

@Preview
@Composable
private fun SettingsMenuLinkPreview() {
	MaterialTheme(colorScheme = darkColorScheme()) {
		PreferenceRowLink(
			title = R.string.service_running,
			subtitle = R.string.status_summary_notification_access_enabled,
			onClick = {}
		)
	}
}

@Preview
@Composable
private fun SettingsCheckboxCheckedPreview() {
	MaterialTheme(colorScheme = darkColorScheme()) {
		PreferenceRowCheckbox(
			title = R.string.ignore_groups,
			subtitle = R.string.ignore_groups_summary_on,
			key = KEY_IGNORE_GROUPS,
			default = true
		)
	}
}

@Preview
@Composable
private fun SettingsCheckboxUncheckedPreview() {
	MaterialTheme(colorScheme = darkColorScheme()) {
		PreferenceRowCheckbox(
			title = R.string.ignore_groups,
			subtitle = R.string.ignore_groups_summary_off,
			key = KEY_IGNORE_GROUPS,
			default = false
		)
	}
}
