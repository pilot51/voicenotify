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
package com.pilot51.voicenotify.ui.dialog.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import com.pilot51.voicenotify.MainActivity
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import com.pilot51.voicenotify.prefs.db.AppRepository
import com.pilot51.voicenotify.ui.dialog.TextEditDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val NOTIFICATION_CHANNEL_ID = "test"

@Composable
fun TestNotificationDialog(onDismiss: () -> Unit) {
	TextEditDialog(
		titleRes = R.string.test,
		messageRes = R.string.test_summary,
		initialText = stringResource(R.string.test_content_text),
		onDismiss = onDismiss
	) { content ->
		CoroutineScope(Dispatchers.IO).launch {
			val vnApp = AppRepository.findOrAddApp(appContext.packageName)!!
			if (!vnApp.isEnabled) {
				launch(Dispatchers.Main) {
					Toast.makeText(appContext, appContext.getString(R.string.test_ignored), Toast.LENGTH_LONG).show()
				}
			}
			val notificationManager = appContext.getSystemService(NotificationManager::class.java)
			val intent = Intent(appContext, MainActivity::class.java)
			delay(5.seconds)
			val id = NOTIFICATION_CHANNEL_ID
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				var channel = notificationManager.getNotificationChannel(id)
				if (channel == null) {
					channel = NotificationChannel(
						id,
						appContext.getString(R.string.test),
						NotificationManager.IMPORTANCE_LOW
					)
					channel.description = appContext.getString(R.string.notification_channel_desc)
					notificationManager.createNotificationChannel(channel)
				}
			}
			val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			val pi = PendingIntent.getActivity(appContext, 0, intent, flags)
			val builder = NotificationCompat.Builder(appContext, id)
				.setAutoCancel(true)
				.setContentIntent(pi)
				.setSmallIcon(R.drawable.ic_notification)
				.setTicker(appContext.getString(R.string.test_ticker))
				.setSubText(appContext.getString(R.string.test_subtext))
				.setContentTitle(appContext.getString(R.string.test_content_title))
				.setContentText(content)
				.setContentInfo(appContext.getString(R.string.test_content_info))
				.setStyle(
					NotificationCompat.BigTextStyle()
						.setBigContentTitle(appContext.getString(R.string.test_big_content_title))
						.setSummaryText(appContext.getString(R.string.test_big_content_summary))
						.bigText(content)
				)
			notificationManager.notify(0, builder.build())
		}
		true
	}
}
