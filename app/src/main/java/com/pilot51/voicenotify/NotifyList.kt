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

import android.app.Notification
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NotifyList {
	private const val HISTORY_LIMIT = 20
	private val list = mutableStateListOf<NotificationInfo>()

	fun addNotification(info: NotificationInfo) {
		if (list.size == HISTORY_LIMIT) {
			list.removeAt(list.size - 1)
		}
		list.add(0, info)
	}

	fun updateInfo(info: NotificationInfo) {
		val index = list.indexOf(info).takeUnless { it == -1 } ?: return
		// Force update to list state by first setting copy
		list[index] = info.copy()
		// Set back to original to ensure future calls can find it again
		list[index] = info
	}

	@Composable
	fun NotificationLogDialog(onDismiss: () -> Unit) {
		NotificationLogDialog(
			list = list,
			onDismiss = onDismiss
		)
	}
}

@Composable
private fun NotificationLogDialog(
	list: List<NotificationInfo>,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = { },
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(android.R.string.ok))
			}
		},
		title = { Text(stringResource(R.string.notify_log)) },
		text = { ItemList(list) }
	)
}

@Composable
private fun ItemList(list: List<NotificationInfo>) {
	LazyColumn(modifier = Modifier.fillMaxWidth()) {
		itemsIndexed(list) { index, item ->
			Item(item)
			if (index < list.lastIndex) {
				Divider(
					modifier = Modifier.padding(vertical = 16.dp),
					color = Color.Gray,
					thickness = 1.dp
				)
			}
		}
	}
}

@Composable
private fun Item(item: NotificationInfo) {
	var showIgnoreDialog by remember(item) { mutableStateOf(false) }
	Column(modifier = Modifier
		.fillMaxWidth()
		.pointerInput(item) {
			detectTapGestures(onLongPress = {
				if (item.app == null) return@detectTapGestures
				showIgnoreDialog = true
			})
		}
	) {
		Text(
			text = item.time,
			modifier = Modifier.fillMaxWidth(),
			textAlign = TextAlign.Center
		)
		Text(
			text = item.app!!.label,
			modifier = Modifier.fillMaxWidth(),
			fontSize = 24.sp,
			textAlign = TextAlign.Center
		)
		val logMessage = item.logMessage
		if (logMessage.isNotEmpty()) {
			Text(
				text = logMessage,
				modifier = Modifier.fillMaxWidth(),
				fontSize = 16.sp,
				textAlign = TextAlign.Center
			)
		}
		if (item.ignoreReasons.isNotEmpty()) {
			Text(
				text = item.getIgnoreReasonsAsText(),
				modifier = Modifier.fillMaxWidth(),
				color = if (item.isInterrupted) Color.Yellow else Color.Red,
				textAlign = TextAlign.Center
			)
		}
	}
	if (showIgnoreDialog && item.app != null) {
		AlertDialog(
			onDismissRequest = {
				showIgnoreDialog = false
			},
			confirmButton = {
				val context = LocalContext.current
				Button(onClick = {
					item.app.setEnabled(!item.app.enabled, true)
					Toast.makeText(context,
						context.getString(
							if (item.app.enabled) R.string.app_is_not_ignored else R.string.app_is_ignored,
							item.app.label
						),
						Toast.LENGTH_SHORT
					).show()
					showIgnoreDialog = false
				}) {
					Text(
						text = stringResource(R.string.yes),
						fontSize = 24.sp
					)
				}
			},
			dismissButton = {
				Button(onClick = {
					showIgnoreDialog = false
				}) {
					Text(
						text = stringResource(android.R.string.cancel),
						fontSize = 24.sp
					)
				}
			},
			title = {
				Text(stringResource(
					if (item.app.enabled) R.string.ignore_app else R.string.unignore_app,
					item.app.label
				))
			}
		)
	}
}

@Preview
@Composable
private fun NotificationLogDialogPreview() {
	val previewNotification = Notification().apply {
		`when` = Long.MIN_VALUE
		extras.apply {
			putString(Notification.EXTRA_SUB_TEXT, "Subtext")
			putString(Notification.EXTRA_TITLE, "Content Title")
			putString(Notification.EXTRA_TEXT, "Content Text")
			putString(Notification.EXTRA_INFO_TEXT, "Content Info")
		}
	}
	val list = listOf(
		NotificationInfo(app = App(1, "package.name.one", "App Name 1", true), previewNotification),
		NotificationInfo(app = App(2, "package.name.two", "App Name 2", false), previewNotification)
	)
	MaterialTheme(colorScheme = darkColorScheme()) {
		NotificationLogDialog(list) {}
	}
}
