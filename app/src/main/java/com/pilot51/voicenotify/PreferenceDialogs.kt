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
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SHAKE_THRESHOLD
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private fun openBrowser(context: Context, url: String) {
	try {
		context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
	} catch (e: ActivityNotFoundException) {
		e.printStackTrace()
		Toast.makeText(context, R.string.error_browser, Toast.LENGTH_LONG).show()
	}
}

@Composable
fun ShakeThresholdDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val value by vm.getShakeThreshold()
	var realtimeShake by remember { mutableIntStateOf(0) }
	var peak by remember { mutableIntStateOf(0) }
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	DisposableEffect(value) {
		val shake = Shake(context)
		scope.launch {
			var peakTimeoutJob: Job? = null
			shake.enable()
			shake.jerk.collect {
				realtimeShake = it.toInt()
				if (realtimeShake > peak) {
					peakTimeoutJob?.cancel()
					peak = realtimeShake
					peakTimeoutJob = launch {
						delay(5.seconds)
						peak = 0
					}
				}
			}
		}
		onDispose {
			shake.disable()
		}
	}
	TextEditDialog(
		titleRes = R.string.shake_to_silence,
		message = stringResource(
			R.string.shake_to_silence_dialog_msg,
			DEFAULT_SHAKE_THRESHOLD,
			realtimeShake,
			peak
		),
		initialText = value.takeIf { it > 0 }?.toString() ?: "",
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) { str ->
		vm.setShakeThreshold(str.toIntOrNull()?.takeIf { it > 0 })
	}
}

@Composable
fun RequireTextDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.require_strings,
		messageRes = R.string.require_ignore_strings_dialog_msg,
		initialText = settingsCombo.requireStrings ?: "",
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(requireStrings = it.takeUnless { it.isEmpty() && settings.isGlobal }))
	}
}

@Composable
fun IgnoreTextDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.ignore_strings,
		messageRes = R.string.require_ignore_strings_dialog_msg,
		initialText = settingsCombo.ignoreStrings ?: "",
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ignoreStrings = it.takeUnless { it.isEmpty() && settings.isGlobal }))
	}
}

@Composable
fun IgnoreRepeatsDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.ignore_repeat,
		messageRes = R.string.ignore_repeat_dialog_msg,
		initialText = (settingsCombo.ignoreRepeat ?: DEFAULT_IGNORE_REPEAT)
			.takeIf { it != -1 }?.toString() ?: "",
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ignoreRepeat = it.toIntOrNull() ?: (-1).takeIf { !settings.isGlobal }))
	}
}

@Composable
fun TtsMessageDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.tts_message,
		message = stringResource(R.string.tts_message_dialog, DEFAULT_TTS_STRING),
		initialText = settingsCombo.ttsString ?: DEFAULT_TTS_STRING,
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ttsString = it.ifEmpty { null }))
	}
}

@Composable
fun TtsMaxLengthDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.max_length,
		message = stringResource(R.string.max_length_dialog_msg, TextToSpeech.getMaxSpeechInputLength()),
		initialText = (settingsCombo.ttsMaxLength ?: DEFAULT_MAX_LENGTH).toString(),
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ttsMaxLength = it.toIntOrNull()))
	}
}

@Composable
fun TtsDelayDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.tts_delay,
		messageRes = R.string.tts_delay_dialog_msg,
		initialText = settingsCombo.ttsDelay.takeIf { it != 0 }?.toString() ?: "",
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ttsDelay = it.toIntOrNull() ?: 0.takeIf { !settings.isGlobal }))
	}
}

@Composable
fun TtsRepeatDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.tts_repeat,
		messageRes = R.string.tts_repeat_dialog_msg,
		initialText = settingsCombo.ttsRepeat.takeIf { it != 0.0 }?.toString() ?: "",
		keyboardType = KeyboardType.Decimal,
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ttsRepeat = it.toDoubleOrNull() ?: 0.0.takeIf { !settings.isGlobal }))
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
fun TtsStreamDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val streamNames = stringArrayResource(R.array.stream_name)
	val streamValues = arrayOf(
		AudioManager.STREAM_MUSIC,
		AudioManager.STREAM_NOTIFICATION,
		AudioManager.STREAM_VOICE_CALL,
		AudioManager.STREAM_RING,
		AudioManager.STREAM_ALARM
	)
	val savedValue = remember(settings) { settings.ttsStream }
	var value by remember(settings) {
		mutableIntStateOf(settingsCombo.ttsStream ?: DEFAULT_TTS_STREAM)
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					if (value != savedValue) {
						vm.save(settings.copy(ttsStream = value))
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
fun DeviceStatesDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val items = stringArrayResource(R.array.device_states)
	val values = remember(settings) {
		mutableStateListOf(
			settingsCombo.speakScreenOff ?: DEFAULT_SPEAK_SCREEN_OFF,
			settingsCombo.speakScreenOn ?: DEFAULT_SPEAK_SCREEN_ON,
			settingsCombo.speakHeadsetOff ?: DEFAULT_SPEAK_HEADSET_OFF,
			settingsCombo.speakHeadsetOn ?: DEFAULT_SPEAK_HEADSET_ON,
			settingsCombo.speakSilentOn ?: DEFAULT_SPEAK_SILENT_ON
		)
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					vm.save(settings.copy(
						speakScreenOff = values[0],
						speakScreenOn = values[1],
						speakHeadsetOff = values[2],
						speakHeadsetOn = values[3],
						speakSilentOn = values[4]
					))
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
	vm: IPreferencesViewModel,
	mode: QuietTimeMode,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val quietTime = when (mode) {
		QuietTimeMode.START -> settingsCombo.quietStart
		QuietTimeMode.END -> settingsCombo.quietEnd
	} ?: DEFAULT_QUIET_TIME
	val timePickerState = rememberTimePickerState(
		initialHour = quietTime / 60,
		initialMinute = quietTime % 60
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(
				onClick = {
					val time = timePickerState.hour * 60 + timePickerState.minute
					vm.save(when (mode) {
						QuietTimeMode.START -> settings.copy(quietStart = time)
						QuietTimeMode.END -> settings.copy(quietEnd = time)
					})
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

@Composable
fun BackupDialog(onDismiss: () -> Unit) {
	val exportBackupLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.CreateDocument("application/zip")
	) {
		it?.let { PreferenceHelper.exportBackup(it) }
		onDismiss()
	}
	val importBackupLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) {
		it?.let { PreferenceHelper.importBackup(it) }
		onDismiss()
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.cancel))
			}
		},
		title = { Text(stringResource(R.string.backup_restore)) },
		text = {
			LazyColumn {
				supportItem(title = R.string.backup_settings) {
					val version = BuildConfig.VERSION_NAME
						.replace(" ", "-").replace(Regex("[\\[\\]]"), "")
					exportBackupLauncher.launch("voice_notify_${version}_backup.zip")
				}
				supportItem(title = R.string.restore_settings) {
					importBackupLauncher.launch(arrayOf("application/zip"))
				}
			}
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
		TtsStreamDialog(PreferencesPreviewVM) {}
	}
}

@VNPreview
@Composable
private fun DeviceStatesDialogPreview() {
	AppTheme {
		DeviceStatesDialog(PreferencesPreviewVM) {}
	}
}

@VNPreview
@Composable
private fun QuietTimeDialogPreview() {
	AppTheme {
		QuietTimeDialog(PreferencesPreviewVM, QuietTimeMode.START) {}
	}
}

@VNPreview
@Composable
private fun SupportDialogPreview() {
	AppTheme {
		SupportDialog {}
	}
}
