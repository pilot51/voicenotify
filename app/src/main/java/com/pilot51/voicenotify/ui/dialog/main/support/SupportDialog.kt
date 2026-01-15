/*
 * Copyright 2011-2026 Mark Injerd
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
package com.pilot51.voicenotify.ui.dialog.main.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.BuildConfig
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.VNPreview

private const val TAG = "SupportDialog"

private fun openBrowser(context: Context, url: String) {
	try {
		context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
	} catch (e: ActivityNotFoundException) {
		Log.w(TAG, e)
		Toast.makeText(context, R.string.error_browser, Toast.LENGTH_LONG).show()
	}
}

@Composable
fun SupportDialog(onDismiss: () -> Unit) {
	val context = LocalContext.current
	var showEmailDialog by remember { mutableStateOf(false) }
	var showPrivacyDialog by remember { mutableStateOf(false) }
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
					supportItem(
						title = R.string.support_rate,
						subtext = R.string.support_rate_subtext
					) {
						val iMarket = Intent(
							Intent.ACTION_VIEW,
							"market://details?id=com.pilot51.voicenotify".toUri()
						).apply {
							addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						}
						try {
							context.startActivity(iMarket)
						} catch (e: ActivityNotFoundException) {
							Log.w(TAG, e)
							Toast.makeText(context, R.string.error_market, Toast.LENGTH_LONG)
								.show()
						}
					}
					supportItem(
						title = R.string.support_email,
						subtext = R.string.support_email_subtext
					) {
						showEmailDialog = true
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
					supportItem(
						title = R.string.support_translations,
						subtext = R.string.support_translations_subtext
					) {
						openBrowser(context, "https://hosted.weblate.org/projects/voice-notify")
					}
					supportItem(
						title = R.string.support_github,
						subtext = R.string.support_github_subtext
					) {
						openBrowser(context, "https://github.com/pilot51/voicenotify")
					}
					supportItem(title = R.string.support_privacy) {
						showPrivacyDialog = true
					}
				}
				Text(
					text = BuildConfig.VERSION_NAME,
					modifier = Modifier
						.align(Alignment.CenterHorizontally)
						.padding(top = 10.dp),
					fontSize = 12.sp
				)
			}
		}
	)
	if (showEmailDialog) {
		EmailDialog { showEmailDialog = false }
	}
	if (showPrivacyDialog) {
		PrivacyDialog { showPrivacyDialog = false }
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
private fun SupportDialogPreview() {
	AppTheme {
		SupportDialog {}
	}
}
