package com.pilot51.voicenotify.ui.dialog.main.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.prefs.PreferenceHelper
import com.pilot51.voicenotify.prefs.PreferenceHelper.LogIgnoredSetting
import com.pilot51.voicenotify.prefs.PreferenceHelper.LogIgnoredValue
import com.pilot51.voicenotify.prefs.PreferenceHelper.logIgnoredAppsStateFlow
import com.pilot51.voicenotify.prefs.PreferenceHelper.logIgnoredStateFlow
import com.pilot51.voicenotify.runIf
import com.pilot51.voicenotify.ui.VNPreview
import com.pilot51.voicenotify.ui.disabledAlpha
import com.pilot51.voicenotify.ui.isPreview

@Composable
fun NotificationLogConfigDialog(onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { },
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = {
			Text(
				text = stringResource(R.string.notify_log_settings),
				modifier = Modifier.fillMaxWidth(),
				fontWeight = FontWeight.Medium,
				textAlign = TextAlign.Center
			)
		},
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				LogIgnoredDropdown(LogIgnoredSetting.NOTIFICATIONS)
				LogIgnoredDropdown(LogIgnoredSetting.APPS)
				Text(
					text = AnnotatedString.fromHtml(stringResource(R.string.log_ignored_desc)),
					modifier = Modifier.padding(top = 16.dp),
					fontSize = 16.sp
				)
			}
		}
	)
}

@Composable
private fun LogIgnoredDropdown(setting: LogIgnoredSetting) {
	val logIgnored by if (isPreview) {
		remember { mutableStateOf(LogIgnoredValue.SHOW) }
	} else {
		logIgnoredStateFlow.collectAsState()
	}
	val logIgnoredApps by if (isPreview) {
		remember { mutableStateOf(LogIgnoredValue.SHOW) }
	} else {
		logIgnoredAppsStateFlow.collectAsState()
	}
	if (logIgnored == null || logIgnoredApps == null) return
	val currentValue = when (setting) {
		LogIgnoredSetting.NOTIFICATIONS -> logIgnored!!
		LogIgnoredSetting.APPS -> logIgnoredApps!!
	}
	val enabled = when (setting) {
		LogIgnoredSetting.NOTIFICATIONS -> true
		LogIgnoredSetting.APPS -> logIgnored != LogIgnoredValue.NO_LOG
	}
	var expanded by remember { mutableStateOf(false) }
	Row(verticalAlignment = Alignment.CenterVertically) {
		Text(
			text = stringResource(currentValue.textRes, stringResource(setting.textRes)),
			modifier = if (enabled) Modifier.clickable { expanded = !expanded } else Modifier,
			color = colorScheme.onSurfaceVariant.runIf(!enabled) { copy(alpha = disabledAlpha) },
			fontSize = 18.sp,
			fontWeight = FontWeight.Bold
		)
		IconButton(
			enabled = enabled,
			onClick = { expanded = !expanded }
		) {
			Icon(
				imageVector = Icons.Default.ArrowDropDown,
				contentDescription = stringResource(currentValue.textRes, stringResource(setting.textRes))
			)
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			LogIgnoredValue.dropdownList.forEach {
				DropdownMenuItem(
					text = {
						Text(stringResource(it.textRes, stringResource(setting.textRes)))
					},
					enabled = enabled && (setting == LogIgnoredSetting.NOTIFICATIONS || it <= logIgnored!!),
					onClick = {
						PreferenceHelper.setLogIgnored(setting, it)
						expanded = false
					}
				)
			}
		}
	}
}

@VNPreview
@Composable
private fun NotificationLogConfigDialogPreview() {
	AppTheme {
		NotificationLogConfigDialog {}
	}
}
