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
package com.pilot51.voicenotify.ui.dialog.main.log

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.IgnoreReason
import com.pilot51.voicenotify.NotificationInfo
import com.pilot51.voicenotify.NotifyList
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.prefs.PreferenceHelper
import com.pilot51.voicenotify.prefs.PreferenceHelper.LogIgnoredValue
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.prefs.db.Settings
import com.pilot51.voicenotify.ui.VNPreview
import com.pilot51.voicenotify.ui.previewNotification

@Composable
fun NotificationLogDialog(
	list: List<NotificationInfo> = NotifyList.notifyList,
	onDismiss: () -> Unit
) {
	var showConfigDialog by remember { mutableStateOf(false) }
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { },
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = {
			Box(
				modifier = Modifier.height(IntrinsicSize.Min),
				contentAlignment = Alignment.Center
			) {
				IconButton(
					modifier = Modifier.align(Alignment.TopEnd),
					onClick = { showConfigDialog = true }
				) {
					Image(
						imageVector = Icons.Default.Settings,
						contentScale = ContentScale.Fit,
						contentDescription = stringResource(R.string.notify_log_settings),
						colorFilter = ColorFilter.tint(Color.Gray)
					)
				}
				Text(
					text = stringResource(R.string.notify_log),
					modifier = Modifier.fillMaxWidth(),
					fontWeight = FontWeight.Medium,
					textAlign = TextAlign.Center
				)
			}
		},
		text = {
			val logIgnored by PreferenceHelper.logIgnoredStateFlow.collectAsState()
			val logIgnoredApps by PreferenceHelper.logIgnoredAppsStateFlow.collectAsState()
			ItemList(
				when {
					logIgnored != LogIgnoredValue.SHOW ->
						list.filter { it.ignoreReasons.isEmpty() || it.isInterrupted }
					logIgnoredApps != LogIgnoredValue.SHOW ->
						list.filterNot { it.ignoreReasons.contains(IgnoreReason.APP) }
					else -> list
				}
			)
		}
	)
	if (showConfigDialog) {
		NotificationLogConfigDialog { showConfigDialog = false }
	}
}

@Composable
private fun ItemList(list: List<NotificationInfo>) {
	var detailDialogInfo by remember { mutableStateOf<NotificationInfo?>(null) }
	var ignoreDialogApp by remember { mutableStateOf<App?>(null) }
	LazyColumn(modifier = Modifier.fillMaxWidth()) {
		itemsIndexed(list) { index, item ->
			Item(
				item = item,
				onShowDetail = { detailDialogInfo = item },
				onShowIgnore = { ignoreDialogApp = item.app }
			)
			if (index < list.lastIndex) {
				HorizontalDivider(
					modifier = Modifier.padding(vertical = 16.dp),
					color = Color.Gray,
					thickness = 1.dp
				)
			}
		}
	}
	detailDialogInfo?.let {
		NotificationDetailDialog(it) { detailDialogInfo = null }
	}
	ignoreDialogApp?.let {
		IgnoreDialog(it) { ignoreDialogApp = null }
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Item(
	item: NotificationInfo,
	onShowDetail: () -> Unit,
	onShowIgnore: () -> Unit
) {
	val logMessage = remember(item) {
		StringBuilder().apply {
			arrayOf(item.contentTitle, item.contentText).forEach {
				if (!it.isNullOrEmpty()) {
					if (isNotEmpty()) appendLine()
					append(it)
				}
			}
		}.toString()
	}
	Column(modifier = Modifier
		.fillMaxWidth()
		.combinedClickable(
			onClick = onShowDetail,
			onLongClick = item.app?.run { onShowIgnore }
		)
	) {
		Text(
			text = item.time,
			modifier = Modifier.fillMaxWidth(),
			textAlign = TextAlign.Center
		)
		Text(
			text = item.app?.label.toString(),
			modifier = Modifier.fillMaxWidth(),
			fontSize = 24.sp,
			textAlign = TextAlign.Center
		)
		if (logMessage.isNotEmpty()) {
			Text(
				text = logMessage,
				modifier = Modifier.fillMaxWidth(),
				fontSize = 16.sp,
				textAlign = TextAlign.Center
			)
		}
		val ignoreReasons by item.ignoreReasonsTextFlow.collectAsState("")
		if (ignoreReasons.isNotEmpty()) {
			val interruptedColor = if (isSystemInDarkTheme()) Color.Yellow else Color(0xFFBBBB00)
			Text(
				text = ignoreReasons,
				modifier = Modifier.fillMaxWidth(),
				color = if (item.isInterrupted) interruptedColor else Color.Red,
				fontWeight = FontWeight.Bold,
				textAlign = TextAlign.Center
			)
		}
	}
}

@VNPreview
@Composable
private fun NotificationLogDialogPreview() {
	val list = listOf(
		NotificationInfo(
			app = App("package.name.one", "App Name 1", true),
			sbn = previewNotification,
			Settings()
		),
		NotificationInfo(
			app = App("package.name.two", "App Name 2", false),
			sbn = previewNotification,
			Settings()
		)
	)
	AppTheme {
		NotificationLogDialog(list) {}
	}
}
