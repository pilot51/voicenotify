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

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.glance.*
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import androidx.glance.unit.ColorProvider

class AppWidget : GlanceAppWidget() {
	override suspend fun provideGlance(context: Context, id: GlanceId) {
		provideContent {
			val isRunning by Service.isRunning.collectAsState()
			val isSuspended by Service.isSuspended.collectAsState()
			Widget(context, isRunning, isSuspended)
		}
	}

	@Composable
	private fun Widget(
		context: Context,
		isSvcRunning: Boolean,
		isSvcSuspended: Boolean
	) {
		val color = if (!isSvcRunning) Color(0xFFCC0000)
			else if (isSvcSuspended) Color(0xFFCC8800)
			else Color(0xFF00CC00)
		Image(
			provider = ImageProvider(R.drawable.widget),
			contentDescription = null,
			modifier = GlanceModifier
				.fillMaxSize()
				.clickable {
					if (isSvcRunning) {
						Toast.makeText(
							context,
							if (Service.toggleSuspend()) {
								R.string.service_suspended
							} else R.string.service_running,
							Toast.LENGTH_SHORT
						).show()
					} else {
						actionStartActivity(
							Common.notificationListenerSettingsIntent,
							actionParametersOf()
						)
					}
				},
			colorFilter = ColorFilter.tint(ColorProvider(color))
		)
	}
}

class AppWidgetReceiver : GlanceAppWidgetReceiver() {
	override val glanceAppWidget: GlanceAppWidget = AppWidget()
}
