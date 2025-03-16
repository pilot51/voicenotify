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

import android.annotation.SuppressLint
import android.app.Notification
import android.content.res.Configuration
import android.os.Environment
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.VNApplication

/**
 * Opacity of disabled components as defined in
 * [Material Design 2](https://m2.material.io/design/interaction/states.html#disabled).
 */
const val disabledAlpha = 0.38f

val debugLogPath @Composable get() = if (isPreview) {
	"Android/data/${LocalContext.current.packageName}/files/debug.log"
} else {
	VNApplication.logFile?.relativeTo(Environment.getExternalStorageDirectory()).toString()
}

val isPreview @Composable get() = LocalInspectionMode.current

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class VNPreview

class BooleanProvider: PreviewParameterProvider<Boolean> {
	override val values: Sequence<Boolean> = sequenceOf(false, true)
}

val previewNotification
	@Suppress("DEPRECATION")
	@SuppressLint("NewApi")
	@Composable
	get() = StatusBarNotification(
		"pkg", "opPkg", 0, "tag", 0, 0, 0,
		Notification().apply {
			tickerText = "Ticker"
			flags = Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_ONLY_ALERT_ONCE
			extras.apply {
				putCharSequence(
					Notification.EXTRA_SUB_TEXT,
					stringResource(R.string.test_subtext)
				)
				putCharSequence(
					Notification.EXTRA_TITLE,
					stringResource(R.string.test_content_title)
				)
				putCharSequence(
					Notification.EXTRA_TEXT,
					stringResource(R.string.test_content_text)
				)
				putCharSequence(
					Notification.EXTRA_INFO_TEXT,
					stringResource(R.string.test_content_info)
				)
				putCharSequence(
					Notification.EXTRA_TITLE_BIG,
					stringResource(R.string.test_big_content_title)
				)
				putCharSequence(
					Notification.EXTRA_SUMMARY_TEXT,
					stringResource(R.string.test_big_content_summary)
				)
				putCharSequence(
					Notification.EXTRA_BIG_TEXT,
					stringResource(R.string.test_big_content_text)
				)
				putCharSequenceArray(
					Notification.EXTRA_TEXT_LINES,
					stringResource(R.string.test_text_lines).split("\n").toTypedArray()
				)
				putInt(Notification.EXTRA_PROGRESS, 50)
				putInt(Notification.EXTRA_PROGRESS_MAX, 100)
			}
		},
		UserHandle.getUserHandleForUid(0), System.currentTimeMillis()
	)
