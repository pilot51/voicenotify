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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.text.isDigitsOnly
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_IGNORE_REPEAT
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_MAX_LENGTH
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_QUIET_TIME
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SPEAK_HEADSET_OFF
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SPEAK_HEADSET_ON
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SPEAK_SCREEN_OFF
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SPEAK_SCREEN_ON
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SPEAK_SILENT_ON
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_TTS_STREAM
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_TTS_STRING
import com.pilot51.voicenotify.PreferenceHelper.KEY_IGNORE_REPEAT
import com.pilot51.voicenotify.PreferenceHelper.KEY_IGNORE_STRINGS
import com.pilot51.voicenotify.PreferenceHelper.KEY_MAX_LENGTH
import com.pilot51.voicenotify.PreferenceHelper.KEY_QUIET_START
import com.pilot51.voicenotify.PreferenceHelper.KEY_REQUIRE_STRINGS
import com.pilot51.voicenotify.PreferenceHelper.KEY_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.KEY_SPEAK_HEADSET_OFF
import com.pilot51.voicenotify.PreferenceHelper.KEY_SPEAK_HEADSET_ON
import com.pilot51.voicenotify.PreferenceHelper.KEY_SPEAK_SCREEN_OFF
import com.pilot51.voicenotify.PreferenceHelper.KEY_SPEAK_SCREEN_ON
import com.pilot51.voicenotify.PreferenceHelper.KEY_SPEAK_SILENT_ON
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_DELAY
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_REPEAT
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_STREAM
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_STRING
import com.pilot51.voicenotify.PreferenceHelper.prefs

private fun openBrowser(context: Context, url: String) {
	try {
		context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
	} catch (e: ActivityNotFoundException) {
		e.printStackTrace()
		Toast.makeText(context, R.string.error_browser, Toast.LENGTH_LONG).show()
	}
}

@Composable
fun ShakeThresholdDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.shake_to_silence,
		message = stringResource(R.string.shake_to_silence_dialog_msg, DEFAULT_SHAKE_THRESHOLD),
		initialText = prefs.getString(KEY_SHAKE_THRESHOLD, null) ?: DEFAULT_SHAKE_THRESHOLD.toString(),
		keyboardType = KeyboardType.Decimal,
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_SHAKE_THRESHOLD, it).apply()
	}
}

@Composable
fun RequireTextDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.require_strings,
		messageRes = R.string.require_ignore_strings_dialog_msg,
		initialText = prefs.getString(KEY_REQUIRE_STRINGS, null) ?: "",
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_REQUIRE_STRINGS, it).apply()
	}
}

@Composable
fun IgnoreTextDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.ignore_strings,
		messageRes = R.string.require_ignore_strings_dialog_msg,
		initialText = prefs.getString(KEY_IGNORE_STRINGS, null) ?: "",
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_IGNORE_STRINGS, it).apply()
	}
}

@Composable
fun IgnoreRepeatsDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.ignore_repeat,
		messageRes = R.string.ignore_repeat_dialog_msg,
		initialText = prefs.getString(KEY_IGNORE_REPEAT, null) ?: DEFAULT_IGNORE_REPEAT.toString(),
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_IGNORE_REPEAT, it).apply()
	}
}

@Composable
fun TtsMessageDialog(onDismiss: () -> Unit) {
	val text = prefs.getString(KEY_TTS_STRING, null) ?: DEFAULT_TTS_STRING
	TextEditDialog(
		titleRes = R.string.tts_message,
		message = stringResource(R.string.tts_message_dialog, DEFAULT_TTS_STRING),
		initialText = text,
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_TTS_STRING, it).apply()
	}
}

@Composable
fun TtsMaxLengthDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.max_length,
		messageRes = R.string.max_length_dialog_msg,
		initialText = prefs.getString(KEY_MAX_LENGTH, null) ?: DEFAULT_MAX_LENGTH.toString(),
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_MAX_LENGTH, it).apply()
	}
}

@Composable
fun TtsDelayDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.tts_delay,
		messageRes = R.string.tts_delay_dialog_msg,
		initialText = prefs.getString(KEY_TTS_DELAY, null) ?: "",
		keyboardType = KeyboardType.Decimal,
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_TTS_DELAY, it).apply()
	}
}

@Composable
fun TtsRepeatDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.tts_repeat,
		messageRes = R.string.tts_repeat_dialog_msg,
		initialText = prefs.getString(KEY_TTS_REPEAT, null) ?: "",
		keyboardType = KeyboardType.Decimal,
		onDismiss = onDismiss
	) {
		prefs.edit().putString(KEY_TTS_REPEAT, it).apply()
	}
}

/**
 * A dialog with a text field.
 * [title] and [message] are required if their respective [titleRes] or [messageRes] isn't set.
 */
@Composable
private fun TextEditDialog(
	@StringRes titleRes: Int = 0,
	@StringRes messageRes: Int = 0,
	title: String = titleRes.takeUnless { it == 0 }?.let { stringResource(it) }!!,
	message: String = messageRes.takeUnless { it == 0 }?.let { stringResource(it) }!!,
	initialText: String,
	keyboardType: KeyboardType = KeyboardOptions.Default.keyboardType,
	onDismiss: () -> Unit,
	onSave: (text: String) -> Unit
) {
	var textValue by remember { mutableStateOf(initialText) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					onSave(textValue)
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

@Composable
fun TtsStreamDialog(onDismiss: () -> Unit) {
	val streamNames = stringArrayResource(R.array.stream_name)
	val streamValues = arrayOf(
		AudioManager.STREAM_MUSIC,
		AudioManager.STREAM_NOTIFICATION,
		AudioManager.STREAM_VOICE_CALL,
		AudioManager.STREAM_RING,
		AudioManager.STREAM_ALARM
	)
	val isPreview = LocalInspectionMode.current
	val savedValue = remember {
		if (isPreview) DEFAULT_TTS_STREAM
		else prefs.getString(KEY_TTS_STREAM, null)?.toIntOrNull() ?: DEFAULT_TTS_STREAM
	}
	var value by remember { mutableIntStateOf(savedValue) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					if (value != savedValue) {
						prefs.edit().putString(KEY_TTS_STREAM, value.toString()).apply()
					}
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
		title = { Text(stringResource(R.string.tts_stream)) },
		text = {
			LazyColumn {
				itemsIndexed(streamNames) { index, item ->
					val isCurrent = value == streamValues[index]
					Row(
						modifier = Modifier
							.toggleable(
								value = isCurrent,
								onValueChange = {
									if (!it) return@toggleable
									value = streamValues[index]
								},
								role = Role.RadioButton
							)
							.fillMaxWidth()
							.heightIn(min = 56.dp)
							.wrapContentHeight(align = Alignment.CenterVertically)
							.padding(horizontal = 16.dp),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						RadioButton(
							selected = isCurrent,
							onClick = null
						)
						Text(
							text = item,
							fontSize = 16.sp
						)
					}
				}
			}
		}
	)
}

@Composable
fun DeviceStatesDialog(onDismiss: () -> Unit) {
	val isPreview = LocalInspectionMode.current
	val items = stringArrayResource(R.array.device_states)
	val keys = remember { arrayOf(
		KEY_SPEAK_SCREEN_OFF,
		KEY_SPEAK_SCREEN_ON,
		KEY_SPEAK_HEADSET_OFF,
		KEY_SPEAK_HEADSET_ON,
		KEY_SPEAK_SILENT_ON
	) }
	val savedValues = remember { if (isPreview) {
		booleanArrayOf(true, true, true, true, false)
	} else {
		prefs.run {
			booleanArrayOf(
				getBoolean(KEY_SPEAK_SCREEN_OFF, DEFAULT_SPEAK_SCREEN_OFF),
				getBoolean(KEY_SPEAK_SCREEN_ON, DEFAULT_SPEAK_SCREEN_ON),
				getBoolean(KEY_SPEAK_HEADSET_OFF, DEFAULT_SPEAK_HEADSET_OFF),
				getBoolean(KEY_SPEAK_HEADSET_ON, DEFAULT_SPEAK_HEADSET_ON),
				getBoolean(KEY_SPEAK_SILENT_ON, DEFAULT_SPEAK_SILENT_ON)
			)
		}
	}.toTypedArray() }
	val values = remember { mutableStateListOf(*savedValues) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					prefs.edit {
						var changed = false
						values.forEachIndexed { index, value ->
							if (savedValues[index] != value) {
								putBoolean(keys[index], value)
								changed = true
							}
						}
						if (changed) apply()
					}
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
		title = { Text(stringResource(R.string.device_state_dialog_title)) },
		text = {
			LazyColumn {
				itemsIndexed(items) { index, item ->
					val isChecked = values[index]
					Row(
						modifier = Modifier
							.toggleable(
								value = isChecked,
								onValueChange = { values[index] = it },
								role = Role.Checkbox
							)
							.fillMaxWidth()
							.heightIn(min = 56.dp)
							.wrapContentHeight(align = Alignment.CenterVertically)
							.padding(horizontal = 16.dp),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Checkbox(
							checked = isChecked,
							onCheckedChange = null
						)
						Text(
							text = item,
							fontSize = 16.sp
						)
					}
				}
			}
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietTimeDialog(
	prefKey: String,
	onDismiss: () -> Unit
) {
	val isPreview = LocalInspectionMode.current
	val quietTime = if (isPreview) DEFAULT_QUIET_TIME else prefs.getInt(prefKey, DEFAULT_QUIET_TIME)
	val timePickerState = rememberTimePickerState(
		initialHour = quietTime / 60,
		initialMinute = quietTime % 60,
		is24Hour = false
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					prefs.edit().putInt(prefKey,
						timePickerState.hour * 60 + timePickerState.minute
					).apply()
					onDismiss()
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
		title = {
			Text(stringResource(
				if (prefKey == KEY_QUIET_START) R.string.quiet_start else R.string.quiet_end
			))
		},
		text = {
			TimePicker(timePickerState)
		}
	)
}

private const val DEV_EMAIL = "pilota51@gmail.com"

@Composable
fun SupportDialog(onDismiss: () -> Unit) {
	val context = LocalContext.current
	var showPrivacy by remember { mutableStateOf(false) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = { Text(stringResource(R.string.support)) },
		text = {
			Column {
				LazyColumn {
					supportItem(title = R.string.support_rate) {
						val iMarket = Intent(
							Intent.ACTION_VIEW,
							Uri.parse("market://details?id=com.pilot51.voicenotify")
						).apply {
							addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						}
						try {
							context.startActivity(iMarket)
						} catch (e: ActivityNotFoundException) {
							e.printStackTrace()
							Toast.makeText(context, R.string.error_market, Toast.LENGTH_LONG).show()
						}
					}
					supportItem(
						title = R.string.support_email,
						subtext = R.string.support_email_subtext
					) {
						val iEmail = Intent(Intent.ACTION_SEND).apply {
							type = "plain/text"
							putExtra(Intent.EXTRA_EMAIL, arrayOf(DEV_EMAIL))
							putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject))
							putExtra(Intent.EXTRA_TEXT, context.getString(
								R.string.email_body,
								BuildConfig.VERSION_NAME,
								Build.VERSION.RELEASE,
								Build.ID,
								"${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}"
							))
						}
						try {
							context.startActivity(iEmail)
						} catch (e: ActivityNotFoundException) {
							e.printStackTrace()
							Toast.makeText(context, R.string.error_email, Toast.LENGTH_LONG).show()
						}
					}
					supportItem(
						title = R.string.support_discord,
						subtext = R.string.support_chat_subtext
					) {
						openBrowser(context, "https://discord.gg/W6XxGT8WG3")
					}
					supportItem(
						title = R.string.support_matrix,
						subtext = R.string.support_chat_subtext
					) {
						openBrowser(context, "https://matrix.to/#/#voicenotify:p51.me")
					}
					supportItem(title = R.string.support_translations) {
						openBrowser(context, "https://hosted.weblate.org/projects/voice-notify")
					}
					supportItem(
						title = R.string.support_github,
						subtext = R.string.support_github_subtext
					) {
						openBrowser(context, "https://github.com/pilot51/voicenotify")
					}
					supportItem(title = R.string.support_privacy) {
						showPrivacy = true
					}
				}
				Text(
					text = BuildConfig.VERSION_NAME,
					modifier = Modifier
						.align(Alignment.CenterHorizontally)
						.padding(top = 10.dp)
					,
					fontSize = 12.sp
				)
			}
		}
	)
	if (showPrivacy) {
		AlertDialog(
			onDismissRequest = { showPrivacy = false },
			confirmButton = { },
			dismissButton = {
				TextButton(onClick = { showPrivacy = false }) {
					Text(stringResource(android.R.string.ok))
				}
			},
			title = { Text(stringResource(R.string.support_privacy)) },
			text = {
				Text(
					text = stringResource(R.string.support_privacy_message),
					modifier = Modifier.verticalScroll(rememberScrollState())
				)
			}
		)
	}
}

private fun LazyListScope.supportItem(
	@StringRes title: Int,
	@StringRes subtext: Int? = null,
	onClick: () -> Unit
) {
	item {
		Column(modifier = Modifier
			.clickable { onClick() }
			.fillMaxWidth()
			.heightIn(min = 56.dp)
			.wrapContentHeight(align = Alignment.CenterVertically)
			.padding(horizontal = 16.dp, vertical = 6.dp)
		) {
			Text(
				text = stringResource(title),
				fontSize = 16.sp
			)
			subtext?.let {
				Text(
					text = stringResource(it),
					fontSize = 12.sp
				)
			}
		}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TextEditDialogPreview() {
	AppTheme {
		TextEditDialog(
			titleRes = R.string.ignore_strings,
			messageRes = R.string.require_ignore_strings_dialog_msg,
			initialText = "text value",
			onDismiss = {}
		) {}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TtsStreamDialogPreview() {
	AppTheme {
		TtsStreamDialog {}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun DeviceStatesDialogPreview() {
	AppTheme {
		DeviceStatesDialog {}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun QuietTimeDialogPreview() {
	AppTheme {
		QuietTimeDialog(KEY_QUIET_START) {}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SupportDialogPreview() {
	AppTheme {
		SupportDialog {}
	}
}
