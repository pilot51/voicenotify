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
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.pilot51.voicenotify.NotifyList.NotificationLogDialog
import com.pilot51.voicenotify.PermissionHelper.RationaleDialog
import com.pilot51.voicenotify.PermissionHelper.requestPermission
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_AUDIO_FOCUS
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_IGNORE_EMPTY
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_IGNORE_GROUPS
import com.pilot51.voicenotify.PreferenceHelper.KEY_AUDIO_FOCUS
import com.pilot51.voicenotify.PreferenceHelper.KEY_IGNORE_EMPTY
import com.pilot51.voicenotify.PreferenceHelper.KEY_IGNORE_GROUPS
import com.pilot51.voicenotify.PreferenceHelper.KEY_QUIET_END
import com.pilot51.voicenotify.PreferenceHelper.KEY_QUIET_START
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

private enum class Screen(@StringRes val title: Int) {
	MAIN(R.string.app_name),
	APP_LIST(R.string.app_list),
	TTS(R.string.tts)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) {
			darkColorScheme(primary = Color(0xFF1CB7D5), primaryContainer = Color(0xFF1E4696))
		} else {
			lightColorScheme(primary = Color(0xFF2A54A5), primaryContainer = Color(0xFF64F0FF))
		},
		typography = MaterialTheme.typography.copy(
			// Increased font size for dialog buttons
			labelLarge = TextStyle(
				fontFamily = FontFamily.SansSerif,
				fontWeight = FontWeight.Medium,
				fontSize = 20.sp,
				lineHeight = 20.sp,
				letterSpacing = 0.1.sp,
			)
		),
		content = content
	)
}

@Composable
fun AppMain() {
	val navController = rememberNavController()
	val backStackEntry by navController.currentBackStackEntryAsState()
	val currentScreen = Screen.valueOf(
		backStackEntry?.destination?.route ?: Screen.MAIN.name
	)
	Scaffold(
		topBar = {
			AppBar(
				currentScreen = currentScreen,
				canNavigateBack = navController.previousBackStackEntry != null,
				navigateUp = { navController.navigateUp() }
			)
		}
	) { innerPadding ->
		NavHost(
			navController = navController,
			startDestination = Screen.MAIN.name,
			modifier = Modifier.padding(innerPadding)
		) {
			composable(route = Screen.MAIN.name) {
				MainScreen(
					onClickAppList = { navController.navigate(Screen.APP_LIST.name) },
					onClickTtsConfig = { navController.navigate(Screen.TTS.name) }
				)
			}
			composable(route = Screen.APP_LIST.name) {
				AppListScreen()
			}
			composable(route = Screen.TTS.name) {
				TtsConfigScreen()
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(
	currentScreen: Screen,
	canNavigateBack: Boolean,
	navigateUp: () -> Unit,
	modifier: Modifier = Modifier
) {
	TopAppBar(
		title = { Text(stringResource(currentScreen.title)) },
		modifier = modifier,
		navigationIcon = {
			if (canNavigateBack) {
				IconButton(onClick = navigateUp) {
					Icon(
						imageVector = Icons.Filled.ArrowBack,
						contentDescription = stringResource(R.string.back)
					)
				}
			}
		},
		actions = {
			if (currentScreen == Screen.APP_LIST) {
				AppListActions()
			}
		},
		colors = TopAppBarDefaults.mediumTopAppBarColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer
		)
	)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MainScreen(
	onClickAppList: () -> Unit,
	onClickTtsConfig: () -> Unit
) {
	val context = LocalContext.current
	val isPreview = LocalInspectionMode.current
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
		PreferenceRowLink(
			title = statusTitle,
			subtitle = statusSummary,
			onClick = {
				if (isRunning) Service.toggleSuspend()
				else context.startActivity(statusIntent)
			},
			onLongClick = { context.startActivity(statusIntent) }
		)
		PreferenceRowLink(
			titleRes = R.string.app_list,
			subtitleRes = R.string.app_list_summary,
			onClick = onClickAppList
		)
		PreferenceRowLink(
			titleRes = R.string.tts,
			subtitleRes = R.string.tts_summary,
			onClick = onClickTtsConfig
		)
		PreferenceRowCheckbox(
			titleRes = R.string.audio_focus,
			subtitleRes = R.string.audio_focus_summary,
			key = KEY_AUDIO_FOCUS,
			default = DEFAULT_AUDIO_FOCUS
		)
		PreferenceRowLink(
			titleRes = R.string.shake_to_silence,
			subtitleRes = R.string.shake_to_silence_summary,
			onClick = { showShakeToSilence = true }
		)
		PreferenceRowLink(
			titleRes = R.string.require_strings,
			subtitleRes = R.string.require_strings_summary,
			onClick = { showRequireText = true }
		)
		PreferenceRowLink(
			titleRes = R.string.ignore_strings,
			subtitleRes = R.string.ignore_strings_summary,
			onClick = { showIgnoreText = true }
		)
		PreferenceRowCheckbox(
			titleRes = R.string.ignore_empty,
			subtitleRes = R.string.ignore_empty_summary_on,
			key = KEY_IGNORE_EMPTY,
			default = DEFAULT_IGNORE_EMPTY
		)
		PreferenceRowCheckbox(
			titleRes = R.string.ignore_groups,
			subtitleRes = R.string.ignore_groups_summary_on,
			key = KEY_IGNORE_GROUPS,
			default = DEFAULT_IGNORE_GROUPS
		)
		PreferenceRowLink(
			titleRes = R.string.ignore_repeat,
			subtitleRes = R.string.ignore_repeat_summary,
			onClick = { showIgnoreRepeats = true }
		)
		PreferenceRowLink(
			titleRes = R.string.device_state,
			subtitleRes = R.string.device_state_summary,
			onClick = { showDeviceStates = true }
		)
		PreferenceRowLink(
			titleRes = R.string.quiet_start,
			subtitleRes = R.string.quiet_start_summary,
			onClick = { showQuietTimeStart = true }
		)
		PreferenceRowLink(
			titleRes = R.string.quiet_end,
			subtitleRes = R.string.quiet_end_summary,
			onClick = { showQuietTimeEnd = true }
		)
		PreferenceRowLink(
			titleRes = R.string.test,
			subtitleRes = R.string.test_summary,
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
			subtitle = stringResource(R.string.notify_log_summary, NotifyList.HISTORY_LIMIT),
			onClick = { showLog = true }
		)
		PreferenceRowLink(
			titleRes = R.string.support,
			subtitleRes = R.string.support_summary,
			onClick = { showSupport = true }
		)
	}
	if (showShakeToSilence) {
		ShakeThresholdDialog { showShakeToSilence = false }
	}
	if (showRequireText) {
		RequireTextDialog { showRequireText = false }
	}
	if (showIgnoreText) {
		IgnoreTextDialog { showIgnoreText = false }
	}
	if (showIgnoreRepeats) {
		IgnoreRepeatsDialog { showIgnoreRepeats = false }
	}
	if (showDeviceStates) {
		DeviceStatesDialog { showDeviceStates = false }
	}
	if (showQuietTimeStart) {
		QuietTimeDialog(KEY_QUIET_START) { showQuietTimeStart = false }
	}
	if (showQuietTimeEnd) {
		QuietTimeDialog(KEY_QUIET_END) { showQuietTimeEnd = false }
	}
	if (showLog) {
		NotificationLogDialog { showLog = false }
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun AppPreview() {
	AppTheme {
		AppMain()
	}
}
