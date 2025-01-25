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
package com.pilot51.voicenotify.ui.dialog.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import com.pilot51.voicenotify.Common
import com.pilot51.voicenotify.MainActivity
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.dialog.TextEditDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val NOTIFICATION_CHANNEL_ID = "test"

@Composable
fun TestNotificationDialog(onDismiss: () -> Unit) {
	val context = LocalContext.current
	TextEditDialog(
		titleRes = R.string.test,
		messageRes = R.string.test_summary,
		initialText = context.getString(R.string.test_content_text),
		onDismiss = onDismiss
	) { content ->
		CoroutineScope(Dispatchers.IO).launch {
			val vnApp = Common.findOrAddApp(context.packageName)!!
			if (!vnApp.isEnabled) {
				launch(Dispatchers.Main) {
					Toast.makeText(context, context.getString(R.string.test_ignored), Toast.LENGTH_LONG).show()
				}
			}
			val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			val intent = Intent(context, MainActivity::class.java)
			delay(5.seconds)
			val id = NOTIFICATION_CHANNEL_ID
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				var channel = notificationManager.getNotificationChannel(id)
				if (channel == null) {
					channel = NotificationChannel(
						id,
						context.getString(R.string.test),
						NotificationManager.IMPORTANCE_LOW
					)
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
				.setContentText(content)
				.setContentInfo(context.getString(R.string.test_content_info))
				.setStyle(
					NotificationCompat.BigTextStyle()
						.setBigContentTitle(context.getString(R.string.test_big_content_title))
						.setSummaryText(context.getString(R.string.test_big_content_summary))
						.bigText(content)
				)
			notificationManager.notify(0, builder.build())
		}
		true
	}
}
