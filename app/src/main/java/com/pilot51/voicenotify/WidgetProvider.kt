/*
 * Copyright 2013-2022 Mark Injerd
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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.pilot51.voicenotify.Common.notificationListenerSettingsIntent
import com.pilot51.voicenotify.Service.Companion.isRunning
import com.pilot51.voicenotify.Service.Companion.isSuspended
import com.pilot51.voicenotify.Service.Companion.toggleSuspend

class WidgetProvider : AppWidgetProvider() {
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		for (appWidgetId in appWidgetIds) {
			val views = RemoteViews(context.packageName, R.layout.appwidget)
			updateViews(context, views)
			appWidgetManager.updateAppWidget(appWidgetId, views)
		}
	}

	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action!!) {
			ACTION_TOGGLE -> if (isRunning) {
				Toast.makeText(
					context,
					if (toggleSuspend()) {
						R.string.service_suspended
					} else R.string.service_running,
					Toast.LENGTH_SHORT
				).show()
			}
			ACTION_UPDATE -> {
				val views = RemoteViews(context.packageName, R.layout.appwidget)
				updateViews(context, views)
				AppWidgetManager.getInstance(context).updateAppWidget(
					ComponentName(context, WidgetProvider::class.java), views
				)
			}
			ACTION_ON -> if (isRunning) {
				Toast.makeText(
					context,
					if (toggleSuspend(false)) {
						R.string.service_suspended
					} else R.string.service_running,
					Toast.LENGTH_SHORT
				).show()
			}
			ACTION_OFF -> {
				if (isRunning) {
					Toast.makeText(
						context,
						if (toggleSuspend(true)) {
							R.string.service_suspended
						} else R.string.service_running,
						Toast.LENGTH_SHORT
					).show()
				}
				super.onReceive(context, intent)
			}
			else -> super.onReceive(context, intent)
		}
	}

	companion object {
		private const val ACTION_TOGGLE = "voicenotify.widget.TOGGLE"
		const val ACTION_UPDATE = "voicenotify.widget.UPDATE"
		private const val ACTION_ON = "voicenotify.widget.ON"
		private const val ACTION_OFF = "voicenotify.widget.OFF"
		private fun updateViews(context: Context, views: RemoteViews) {
			val pendingIntent: PendingIntent
			if (isRunning) {
				pendingIntent = PendingIntent.getBroadcast(
					context, 0,
					Intent(context, WidgetProvider::class.java).setAction(ACTION_TOGGLE),
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						PendingIntent.FLAG_IMMUTABLE
					} else 0
				)
				views.setImageViewResource(
					R.id.button,
					if (isSuspended) {
						R.drawable.widget_suspended
					} else R.drawable.widget_running
				)
			} else {
				pendingIntent = PendingIntent.getActivity(
					context, 0,
					notificationListenerSettingsIntent,
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						PendingIntent.FLAG_IMMUTABLE
					} else 0
				)
				views.setImageViewResource(R.id.button, R.drawable.widget_disabled)
			}
			views.setOnClickPendingIntent(R.id.button, pendingIntent)
		}
	}
}
