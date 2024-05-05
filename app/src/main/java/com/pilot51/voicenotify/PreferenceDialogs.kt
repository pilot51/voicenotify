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
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.KEY_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.getPrefState
import com.pilot51.voicenotify.PreferenceHelper.globalSettingsState
import com.pilot51.voicenotify.PreferenceHelper.save
import com.pilot51.voicenotify.PreferenceHelper.setPref
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_REPEAT
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_MAX_LENGTH
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_QUIET_TIME
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_HEADSET_OFF
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_HEADSET_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SCREEN_OFF
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SCREEN_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_SPEAK_SILENT_ON
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_TTS_STREAM
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_TTS_STRING

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
	val value by getPrefState(KEY_SHAKE_THRESHOLD, DEFAULT_SHAKE_THRESHOLD)
	TextEditDialog(
		titleRes = R.string.shake_to_silence,
		message = stringResource(R.string.shake_to_silence_dialog_msg, DEFAULT_SHAKE_THRESHOLD),
		initialText = value.toString(),
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		setPref(KEY_SHAKE_THRESHOLD, it.toIntOrNull())
	}
}

@Composable
fun RequireTextDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	TextEditDialog(
		titleRes = R.string.require_strings,
		messageRes = R.string.require_ignore_strings_dialog_msg,
		initialText = settings.requireStrings ?: "",
		onDismiss = onDismiss
	) {
		settings.run {
			requireStrings = it.ifEmpty { null }
			save()
		}
	}
}

@Composable
fun IgnoreTextDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	TextEditDialog(
		titleRes = R.string.ignore_strings,
		messageRes = R.string.require_ignore_strings_dialog_msg,
		initialText = settings.ignoreStrings ?: "",
		onDismiss = onDismiss
	) {
		settings.run {
			ignoreStrings = it.ifEmpty { null }
			save()
		}
	}
}

@Composable
fun IgnoreRepeatsDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	TextEditDialog(
		titleRes = R.string.ignore_repeat,
		messageRes = R.string.ignore_repeat_dialog_msg,
		initialText = (settings.ignoreRepeat ?: DEFAULT_IGNORE_REPEAT).toString(),
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		settings.run {
			ignoreRepeat = it.toIntOrNull()
			save()
		}
	}
}

@Composable
fun TtsMessageDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	val text = settings.ttsString ?: DEFAULT_TTS_STRING
	TextEditDialog(
		titleRes = R.string.tts_message,
		message = stringResource(R.string.tts_message_dialog, DEFAULT_TTS_STRING),
		initialText = text,
		onDismiss = onDismiss
	) {
		settings.run {
			ttsString = it.ifEmpty { null }
			save()
		}
	}
}

@Composable
fun TtsMaxLengthDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	TextEditDialog(
		titleRes = R.string.max_length,
		messageRes = R.string.max_length_dialog_msg,
		initialText = (settings.ttsMaxLength ?: DEFAULT_MAX_LENGTH).toString(),
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		settings.run {
			ttsMaxLength = it.toIntOrNull()
			save()
		}
	}
}

@Composable
fun TtsDelayDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	TextEditDialog(
		titleRes = R.string.tts_delay,
		messageRes = R.string.tts_delay_dialog_msg,
		initialText = settings.ttsDelay?.toString() ?: "",
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		settings.run {
			ttsDelay = it.toIntOrNull()
			save()
		}
	}
}

@Composable
fun TtsRepeatDialog(onDismiss: () -> Unit) {
	val settings by globalSettingsState
	TextEditDialog(
		titleRes = R.string.tts_repeat,
		messageRes = R.string.tts_repeat_dialog_msg,
		initialText = settings.ttsRepeat?.toString() ?: "",
		keyboardType = KeyboardType.Decimal,
		onDismiss = onDismiss
	) {
		settings.run {
			ttsRepeat = it.toDoubleOrNull()
			save()
		}
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
	var textValue by remember(initialText) { mutableStateOf(initialText) }
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
	val settings by globalSettingsState
	val savedValue = remember(settings) {
		settings.ttsStream ?: DEFAULT_TTS_STREAM
	}
	var value by remember(settings) { mutableIntStateOf(savedValue) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					if (value != savedValue) {
						settings.run {
							ttsStream = value
							save()
						}
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
	val settings by globalSettingsState
	val items = stringArrayResource(R.array.device_states)
	val values = remember(settings) {
		mutableStateListOf(
			settings.speakScreenOff ?: DEFAULT_SPEAK_SCREEN_OFF,
			settings.speakScreenOn ?: DEFAULT_SPEAK_SCREEN_ON,
			settings.speakHeadsetOff ?: DEFAULT_SPEAK_HEADSET_OFF,
			settings.speakHeadsetOn ?: DEFAULT_SPEAK_HEADSET_ON,
			settings.speakSilentOn ?: DEFAULT_SPEAK_SILENT_ON
		)
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					settings.run {
						speakScreenOff = values[0]
						speakScreenOn = values[1]
						speakHeadsetOff = values[2]
						speakHeadsetOn = values[3]
						speakSilentOn = values[4]
						save()
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

enum class QuietTimeMode {
	START, END
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietTimeDialog(
	mode: QuietTimeMode,
	onDismiss: () -> Unit
) {
	val settings by globalSettingsState
	val quietTime = when (mode) {
		QuietTimeMode.START -> settings.quietStart
		QuietTimeMode.END -> settings.quietEnd
	} ?: DEFAULT_QUIET_TIME
	val timePickerState = rememberTimePickerState(
		initialHour = quietTime / 60,
		initialMinute = quietTime % 60,
		key = settings
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					val time = timePickerState.hour * 60 + timePickerState.minute
					when (mode) {
						QuietTimeMode.START -> settings.quietStart = time
						QuietTimeMode.END -> settings.quietEnd = time
					}
					settings.save()
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
				if (mode == QuietTimeMode.START) R.string.quiet_start else R.string.quiet_end
			))
		},
		text = {
			TimePicker(timePickerState)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberTimePickerState(
	initialHour: Int = 0,
	initialMinute: Int = 0,
	is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
	key: Any
): TimePickerState = rememberSaveable(
	saver = TimePickerState.Saver(),
	inputs = arrayOf(key)
) {
	TimePickerState(
		initialHour = initialHour,
		initialMinute = initialMinute,
		is24Hour = is24Hour,
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

@VNPreview
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

@VNPreview
@Composable
private fun TtsStreamDialogPreview() {
	AppTheme {
		TtsStreamDialog {}
	}
}

@VNPreview
@Composable
private fun DeviceStatesDialogPreview() {
	AppTheme {
		DeviceStatesDialog {}
	}
}

@VNPreview
@Composable
private fun QuietTimeDialogPreview() {
	AppTheme {
		QuietTimeDialog(QuietTimeMode.START) {}
	}
}

@VNPreview
@Composable
private fun SupportDialogPreview() {
	AppTheme {
		SupportDialog {}
	}
}
