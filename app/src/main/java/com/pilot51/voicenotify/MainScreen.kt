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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.pilot51.voicenotify.NotifyList.NotificationLogDialog
import com.pilot51.voicenotify.PermissionHelper.RationaleDialog
import com.pilot51.voicenotify.PermissionHelper.requestPermission
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_AUDIO_FOCUS
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_EMPTY
import com.pilot51.voicenotify.db.Settings.Companion.DEFAULT_IGNORE_GROUPS
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
	vm: IPreferencesViewModel,
	configApp: App?,
	onClickAppList: () -> Unit,
	onClickTtsConfig: () -> Unit
) {
	val context = LocalContext.current
	val settings by vm.getSettingsState(configApp)
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	val phoneStatePermissionState = if (isPreview) null
		else rememberPermissionState(Manifest.permission.READ_PHONE_STATE)
	val postNotificationPermissionState =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPreview) {
			rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { isGranted ->
				if (isGranted) runTestNotification(context)
			}
		} else null
	var statusTitle by remember { mutableStateOf("") }
	var statusSummary by remember { mutableStateOf("") }
	val statusIntent = remember { Common.notificationListenerSettingsIntent }
	if (isPreview) {
		statusTitle = context.getString(R.string.service_running)
		statusSummary = context.getString(R.string.status_summary_notification_access_enabled)
	}
	val isRunning by (if (isPreview) MutableStateFlow(false) else Service.isRunning).collectAsState()
	val isSuspended by (if (isPreview) MutableStateFlow(false) else Service.isSuspended).collectAsState()
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
	var showLog by remember { mutableStateOf(false) }
	var showBackupRestore by remember { mutableStateOf(false) }
	var showSupport by remember { mutableStateOf(false) }
	var showReadPhoneStateRationale by remember { mutableStateOf(false) }
	var showPostNotificationRationale by remember { mutableStateOf(false) }
	val lifeCycleOwner = LocalLifecycleOwner.current
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
			initialValue = settingsCombo.audioFocus ?: DEFAULT_AUDIO_FOCUS,
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
			initialValue = settingsCombo.ignoreEmpty ?: DEFAULT_IGNORE_EMPTY,
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
			initialValue = settingsCombo.ignoreGroups ?: DEFAULT_IGNORE_GROUPS,
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
						runTestNotification(context)
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
	if (showLog) {
		NotificationLogDialog { showLog = false }
	}
	if (showBackupRestore) {
		BackupDialog { showBackupRestore = false }
	}
	if (showSupport) {
		SupportDialog { showSupport = false }
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

private const val NOTIFICATION_CHANNEL_ID = "test"

private fun runTestNotification(context: Context) {
	val vnApp = Common.findOrAddApp(context.packageName)!!
	if (!vnApp.enabled) {
		Toast.makeText(context, context.getString(R.string.test_ignored), Toast.LENGTH_LONG).show()
	}
	val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
	val intent = Intent(context, MainActivity::class.java)
	Timer().schedule(object : TimerTask() {
		override fun run() {
			val id = NOTIFICATION_CHANNEL_ID
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				var channel = notificationManager.getNotificationChannel(id)
				if (channel == null) {
					channel = NotificationChannel(id, context.getString(R.string.test), NotificationManager.IMPORTANCE_LOW)
					channel.description = context.getString(R.string.notification_channel_desc)
					notificationManager.createNotificationChannel(channel)
				}
			}
			val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			} else PendingIntent.FLAG_UPDATE_CURRENT
			val pi = PendingIntent.getActivity(context, 0, intent, flags)
			val builder = NotificationCompat.Builder(context, id)
				.setAutoCancel(true)
				.setContentIntent(pi)
				.setSmallIcon(R.drawable.ic_notification)
				.setTicker(context.getString(R.string.test_ticker))
				.setSubText(context.getString(R.string.test_subtext))
				.setContentTitle(context.getString(R.string.test_content_title))
				.setContentText(context.getString(R.string.test_content_text))
				.setContentInfo(context.getString(R.string.test_content_info))
				.setStyle(NotificationCompat.BigTextStyle()
					.setBigContentTitle(context.getString(R.string.test_big_content_title))
					.setSummaryText(context.getString(R.string.test_big_content_summary))
					.bigText(context.getString(R.string.test_big_content_text))
				)
			notificationManager.notify(0, builder.build())
		}
	}, 5000)
}

@VNPreview
@Composable
private fun MainScreenPreview() {
	AppTheme {
		MainScreen(PreferencesPreviewVM, null, {}, {})
	}
}
