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

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.NotificationInfo
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.prefs.db.AppRepository
import com.pilot51.voicenotify.prefs.db.Settings
import com.pilot51.voicenotify.ui.VNPreview
import com.pilot51.voicenotify.ui.previewNotification

@Composable
fun NotificationDetailDialog(
	item: NotificationInfo,
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
		title = {
			Text(
				text = stringResource(R.string.notification_details),
				modifier = Modifier.fillMaxWidth(),
				fontWeight = FontWeight.Medium,
				textAlign = TextAlign.Center
			)
		},
		text = {
			Column(
				modifier = Modifier.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(item.dateTime)
				item.app?.also { app ->
					val isEnabled by AppRepository.isEnabledFlow(app).collectAsState(app.isEnabled)
					Text(text = app.label, fontSize = 24.sp)
					Text(app.packageName)
					Row(
						modifier = Modifier
							.toggleable(
								value = isEnabled,
								role = Role.Checkbox
							) {
								AppRepository.toggleIgnore(app)
							}
							.padding(10.dp),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Checkbox(checked = isEnabled, onCheckedChange = null)
						Text(stringResource(R.string.enable))
					}
				}
				Column(
					modifier = Modifier.verticalScroll(rememberScrollState()),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(10.dp)
				) {
					item.ticker?.let {
						NotificationPart(R.string.ticker, it)
					}
					item.subtext?.let {
						NotificationPart(R.string.subtext, it)
					}
					item.contentTitle?.let {
						NotificationPart(R.string.title, it)
					}
					item.contentText?.let {
						NotificationPart(R.string.content_text, it)
					}
					item.contentInfoText?.let {
						NotificationPart(R.string.content_info_text, it)
					}
					item.bigContentTitle?.let {
						NotificationPart(R.string.big_content_title, it)
					}
					item.bigContentSummary?.let {
						NotificationPart(R.string.big_content_summary, it)
					}
					item.bigContentText?.let {
						NotificationPart(R.string.big_content_text, it)
					}
					item.textLines?.let {
						NotificationPart(R.string.text_lines, it.joinToString("\n"))
					}
					item.ttsMessage?.let {
						NotificationPart(R.string.tts_message, it)
					}
					item.flagsText.takeIf { it.isNotEmpty() }?.let {
						NotificationPart(R.string.flags, it)
					}
					NotificationPart(
						partName = R.string.metadata,
						text = StringBuilder(
							stringResource(
								R.string.metadata_id,
								item.id
							)
						).apply {
							item.category?.let {
								appendLine()
								append(stringResource(R.string.metadata_category, it))
							}
							item.progress?.let {
								appendLine()
								append(
									if (item.progressIndeterminate) {
										stringResource(R.string.metadata_progress_indeterminate)
									} else {
										stringResource(
											R.string.metadata_progress,
											it,
											item.progressMax!!
										)
									}
								)
							}
						}.toString()
					)
					val ignoreReasons by item.ignoreReasonsTextFlow.collectAsState("")
					if (ignoreReasons.isNotEmpty()) {
						val interruptedColor = if (isSystemInDarkTheme()) {
							Color.Yellow
						} else Color(0xFFBBBB00)
						NotificationPart(
							partName = if (item.isInterrupted) {
								R.string.interrupted_reason
							} else R.string.ignored_reasons,
							text = ignoreReasons,
							color = if (item.isInterrupted) interruptedColor else Color.Red
						)
					}
				}
			}
		}
	)
}

@Composable
private fun NotificationPart(
	@StringRes partName: Int,
	text: String,
	color: Color = Color.Unspecified
) {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Text(
			text = stringResource(partName),
			color = color,
			fontSize = 18.sp,
			textDecoration = TextDecoration.Underline,
			textAlign = TextAlign.Center
		)
		Text(
			text = text,
			color = color,
			textAlign = TextAlign.Center
		)
	}
}

@VNPreview
@Composable
private fun DetailDialogPreview() {
	val info = NotificationInfo(
		app = App("package.name.one", "App Name 1", true),
		sbn = previewNotification,
		Settings()
	)
	AppTheme {
		NotificationDetailDialog(info) {}
	}
}
