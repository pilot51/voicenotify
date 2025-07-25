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

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.judemanutd.autostarter.AutoStartPermissionHelper
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.NotifyList
import com.pilot51.voicenotify.PermissionHelper
import com.pilot51.voicenotify.PermissionHelper.RationaleDialog
import com.pilot51.voicenotify.PermissionHelper.requestPermission
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.Service
import com.pilot51.voicenotify.prefs.DataStoreManager.getPrefFlow
import com.pilot51.voicenotify.prefs.DataStoreManager.setPref
import com.pilot51.voicenotify.prefs.PreferenceHelper.KEY_DISABLE_AUTOSTART_MSG
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.ui.dialog.main.BackupDialog
import com.pilot51.voicenotify.ui.dialog.main.DeviceStatesDialog
import com.pilot51.voicenotify.ui.dialog.main.IgnoreRepeatsDialog
import com.pilot51.voicenotify.ui.dialog.main.IgnoreTextDialog
import com.pilot51.voicenotify.ui.dialog.main.QuietTimeDialog
import com.pilot51.voicenotify.ui.dialog.main.QuietTimeMode
import com.pilot51.voicenotify.ui.dialog.main.RequireTextDialog
import com.pilot51.voicenotify.ui.dialog.main.ShakeThresholdDialog
import com.pilot51.voicenotify.ui.dialog.main.TestNotificationDialog
import com.pilot51.voicenotify.ui.dialog.main.log.NotificationLogDialog
import com.pilot51.voicenotify.ui.dialog.main.support.SupportDialog
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "MainScreen"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
	vm: IPreferencesViewModel,
	configApp: App?,
	onClickAppList: () -> Unit,
	onClickTtsConfig: () -> Unit
) {
	val context = LocalContext.current
	val autoStartHelper = remember { AutoStartPermissionHelper.getInstance() }
	val settings by vm.getSettingsState(configApp)
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val phoneStatePermissionState = if (isPreview) null
		else rememberPermissionState(Manifest.permission.READ_PHONE_STATE)
	var statusTitle by remember { mutableStateOf("") }
	var statusSummary by remember { mutableStateOf("") }
	val statusIntent = remember { PermissionHelper.notificationListenerSettingsIntent }
	if (isPreview) {
		statusTitle = stringResource(R.string.service_running)
		statusSummary = stringResource(R.string.status_summary_notification_access_enabled)
	}
	val isRunning by (if (isPreview) MutableStateFlow(false) else Service.isRunning).collectAsState()
	val isSuspended by (if (isPreview) MutableStateFlow(false) else Service.isSuspendedFlow).collectAsState()
	if (isSuspended && isRunning) {
		statusTitle = stringResource(R.string.service_suspended)
		statusSummary = stringResource(R.string.status_summary_suspended)
	} else {
		statusTitle = stringResource(
			if (isRunning) R.string.service_running else R.string.service_disabled
		)
		statusSummary = stringResource(
			if (NotificationManagerCompat.getEnabledListenerPackages(context)
				.contains(context.packageName)
			) {
				R.string.status_summary_notification_access_enabled
			} else {
				R.string.status_summary_notification_access_disabled
			}
		)
	}
	var showShakeToSilence by remember { mutableStateOf(false) }
	var showRequireText by remember { mutableStateOf(false) }
	var showIgnoreText by remember { mutableStateOf(false) }
	var showIgnoreRepeats by remember { mutableStateOf(false) }
	var showDeviceStates by remember { mutableStateOf(false) }
	var showQuietTimeStart by remember { mutableStateOf(false) }
	var showQuietTimeEnd by remember { mutableStateOf(false) }
	var showTestNotification by remember { mutableStateOf(false) }
	var showLog by remember { mutableStateOf(false) }
	var showBackupRestore by remember { mutableStateOf(false) }
	var showSupport by remember { mutableStateOf(false) }
	val disableAutostartMsg by getPrefFlow(KEY_DISABLE_AUTOSTART_MSG, false).collectAsState(null)
	var showAutostartDialog by remember(disableAutostartMsg?.run { true }) {
		mutableStateOf(disableAutostartMsg == false && !isRunning &&
			autoStartHelper.isAutoStartPermissionAvailable(context))
	}
	var showReadPhoneStateRationale by remember { mutableStateOf(false) }
	var showPostNotificationRationale by remember { mutableStateOf(false) }
	val lifeCycleOwner = LocalLifecycleOwner.current

	val postNotificationPermissionState =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPreview) {
			rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { isGranted ->
				if (isGranted) showTestNotification = true
			}
		} else null

	DisposableEffect(lifeCycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_CREATE) {
				phoneStatePermissionState?.requestPermission {
					showReadPhoneStateRationale = true
				}
			}
		}
		lifeCycleOwner.lifecycle.run {
			addObserver(observer)
			onDispose {
				removeObserver(observer)
			}
		}
	}
	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
	) {
		if (settings.isGlobal) {
			PreferenceRowLink(
				title = statusTitle,
				summary = statusSummary,
				onClick = {
					if (isRunning) Service.toggleSuspend()
					else context.startActivity(statusIntent)
				},
				onLongClick = { context.startActivity(statusIntent) }
			)
			PreferenceRowLink(
				titleRes = R.string.app_list,
				summaryRes = R.string.app_list_summary,
				onClick = onClickAppList
			)
		}
		PreferenceRowLink(
			titleRes = R.string.tts,
			summaryRes = R.string.tts_summary,
			onClick = onClickTtsConfig
		)
		PreferenceRowCheckbox(
			titleRes = R.string.audio_focus,
			summaryResOn = R.string.audio_focus_summary,
			value = settingsCombo.audioFocus!!,
			app = configApp,
			showRemove = !settings.isGlobal && settings.audioFocus != null,
			onRemove = {
				vm.save(settings.copy(audioFocus = null))
			}
		) {
			vm.save(settings.copy(audioFocus = it))
		}
		if (settings.isGlobal) {
			PreferenceRowLink(
				titleRes = R.string.shake_to_silence,
				summaryRes = R.string.shake_to_silence_summary,
				onClick = { showShakeToSilence = true }
			)
		}
		PreferenceRowLink(
			titleRes = R.string.require_strings,
			summaryRes = R.string.require_strings_summary,
			app = configApp,
			showRemove = !settings.isGlobal && settings.requireStrings != null,
			onRemove = {
				vm.save(settings.copy(requireStrings = null))
			},
			onClick = { showRequireText = true }
		)
		PreferenceRowLink(
			titleRes = R.string.ignore_strings,
			summaryRes = R.string.ignore_strings_summary,
			app = configApp,
			showRemove = !settings.isGlobal && settings.ignoreStrings != null,
			onRemove = {
				vm.save(settings.copy(ignoreStrings = null))
			},
			onClick = { showIgnoreText = true }
		)
		PreferenceRowCheckbox(
			titleRes = R.string.ignore_empty,
			summaryResOn = R.string.ignore_empty_summary_on,
			summaryResOff = R.string.ignore_empty_summary_off,
			value = settingsCombo.ignoreEmpty!!,
			app = configApp,
			showRemove = !settings.isGlobal && settings.ignoreEmpty != null,
			onRemove = {
				vm.save(settings.copy(ignoreEmpty = null))
			}
		) {
			vm.save(settings.copy(ignoreEmpty = it))
		}
		PreferenceRowCheckbox(
			titleRes = R.string.ignore_groups,
			summaryResOn = R.string.ignore_groups_summary_on,
			summaryResOff = R.string.ignore_groups_summary_off,
			value = settingsCombo.ignoreGroups!!,
			app = configApp,
			showRemove = !settings.isGlobal && settings.ignoreGroups != null,
			onRemove = {
				vm.save(settings.copy(ignoreGroups = null))
			}
		) {
			vm.save(settings.copy(ignoreGroups = it))
		}
		PreferenceRowLink(
			titleRes = R.string.ignore_repeat,
			summaryRes = R.string.ignore_repeat_summary,
			app = configApp,
			showRemove = !settings.isGlobal && settings.ignoreRepeat != null,
			onRemove = {
				vm.save(settings.copy(ignoreRepeat = null))
			},
			onClick = { showIgnoreRepeats = true }
		)
		PreferenceRowLink(
			titleRes = R.string.device_state,
			summaryRes = R.string.device_state_summary,
			app = configApp,
			showRemove = !settings.isGlobal && settings.run {
				speakScreenOff != null ||
					speakScreenOn != null ||
					speakHeadsetOff != null ||
					speakHeadsetOn != null ||
					speakSilentOn != null
			},
			onRemove = {
				vm.save(settings.copy(
					speakScreenOff = null,
					speakScreenOn = null,
					speakHeadsetOff = null,
					speakHeadsetOn = null,
					speakSilentOn = null
				))
			},
			onClick = { showDeviceStates = true }
		)
		PreferenceRowLink(
			titleRes = R.string.quiet_start,
			summaryRes = R.string.quiet_start_summary,
			app = configApp,
			showRemove = !settings.isGlobal && settings.quietStart != null,
			onRemove = {
				vm.save(settings.copy(quietStart = null))
			},
			onClick = { showQuietTimeStart = true }
		)
		PreferenceRowLink(
			titleRes = R.string.quiet_end,
			summaryRes = R.string.quiet_end_summary,
			app = configApp,
			showRemove = !settings.isGlobal && settings.quietEnd != null,
			onRemove = {
				vm.save(settings.copy(quietEnd = null))
			},
			onClick = { showQuietTimeEnd = true }
		)
		if (settings.isGlobal) {
			PreferenceRowLink(
				titleRes = R.string.test,
				summaryRes = R.string.test_summary,
				onClick = {
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
						postNotificationPermissionState!!.requestPermission {
							showPostNotificationRationale = true
						}
					) {
						showTestNotification = true
					}
				}
			)
			PreferenceRowLink(
				titleRes = R.string.notify_log,
				summary = stringResource(R.string.notify_log_summary, NotifyList.HISTORY_LIMIT),
				onClick = { showLog = true }
			)
			PreferenceRowLink(
				titleRes = R.string.backup_restore,
				summaryRes = R.string.backup_restore_summary,
				onClick = { showBackupRestore = true }
			)
			PreferenceRowLink(
				titleRes = R.string.support,
				summaryRes = R.string.support_summary,
				onClick = { showSupport = true }
			)
		}
	}
	if (showShakeToSilence) {
		ShakeThresholdDialog(vm) { showShakeToSilence = false }
	}
	if (showRequireText) {
		RequireTextDialog(vm) { showRequireText = false }
	}
	if (showIgnoreText) {
		IgnoreTextDialog(vm) { showIgnoreText = false }
	}
	if (showIgnoreRepeats) {
		IgnoreRepeatsDialog(vm) { showIgnoreRepeats = false }
	}
	if (showDeviceStates) {
		DeviceStatesDialog(vm) { showDeviceStates = false }
	}
	if (showQuietTimeStart) {
		QuietTimeDialog(
			vm = vm,
			mode = QuietTimeMode.START
		) { showQuietTimeStart = false }
	}
	if (showQuietTimeEnd) {
		QuietTimeDialog(
			vm = vm,
			mode = QuietTimeMode.END
		) { showQuietTimeEnd = false }
	}
	if (showTestNotification) {
		TestNotificationDialog { showTestNotification = false }
	}
	if (showLog) {
		NotificationLogDialog { showLog = false }
	}
	if (showBackupRestore) {
		BackupDialog { showBackupRestore = false }
	}
	if (showSupport) {
		SupportDialog { showSupport = false }
	}
	if (showAutostartDialog) {
		var autoOpenError by remember { mutableStateOf(false) }
		val canOpenDirect = remember(autoOpenError) {
			!autoOpenError && autoStartHelper.getAutoStartPermission(context, open = false)
				// Library opens wrong screen on Samsung (Sleeping Apps instead of Never Sleeping Apps)
				&& Build.BRAND.lowercase() != "samsung"
		}
		AlertDialog(
			onDismissRequest = { showAutostartDialog = false },
			confirmButton = {
				TextButton(
					onClick = {
						if (canOpenDirect) {
							try {
								autoStartHelper.getAutoStartPermission(context)
							} catch (e: SecurityException) {
								Log.w(TAG, e)
								autoOpenError = true
								Toast.makeText(context, R.string.autostart_message_direct_failed,
									Toast.LENGTH_SHORT).show()
								return@TextButton
							}
						} else context.startActivity(Intent(Settings.ACTION_SETTINGS))
						showAutostartDialog = false
					}
				) {
					Text(stringResource(android.R.string.ok))
				}
			},
			dismissButton = {
				TextButton(onClick = { showAutostartDialog = false }) {
					Text(stringResource(android.R.string.cancel))
				}
			},
			text = {
				Column {
					Text(
						stringResource(R.string.autostart_message) +
							stringResource(
								if (canOpenDirect) R.string.autostart_message_direct
								else R.string.autostart_message_manual,
								stringResource(android.R.string.ok)
							)
					)
					Row(
						modifier = Modifier.padding(top = 16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Checkbox(
							checked = disableAutostartMsg!!,
							onCheckedChange = {
								setPref(KEY_DISABLE_AUTOSTART_MSG, it)
							}
						)
						Text(stringResource(R.string.dont_show_again))
					}
				}
			}
		)
	}
	if (showReadPhoneStateRationale) {
		RationaleDialog(
			permissionState = phoneStatePermissionState!!,
			rationaleMsgId = R.string.permission_rationale_read_phone_state
		) { showReadPhoneStateRationale = false }
	}
	if (showPostNotificationRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		RationaleDialog(
			permissionState = postNotificationPermissionState!!,
			rationaleMsgId = R.string.permission_rationale_post_notifications
		) {
			showPostNotificationRationale = false
		}
	}
}

@VNPreview
@Composable
private fun MainScreenPreview() {
	AppTheme {
		MainScreen(PreferencesPreviewVM, null, {}, {})
	}
}
