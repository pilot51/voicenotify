package com.pilot51.voicenotify;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

public class WidgetProvider extends AppWidgetProvider {
	static final String ACTION_TOGGLE = "voicenotify.widget.TOGGLE",
	                    ACTION_UPDATE = "voicenotify.widget.UPDATE";
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		for (int i = 0; i < appWidgetIds.length; i++) {
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget);
			updateViews(context, views);
			appWidgetManager.updateAppWidget(appWidgetIds[i], views);
		}
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(ACTION_TOGGLE)) {
			if (Service.isRunning()) {
				Toast.makeText(context,
				               Service.toggleSuspend() ? R.string.service_suspended : R.string.service_running,
				               Toast.LENGTH_SHORT).show();
			}
		} else if (intent.getAction().equals(ACTION_UPDATE)) {
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget);
			updateViews(context, views);
			AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), views);
		} else super.onReceive(context, intent);
	}
	
	private static void updateViews(Context context, RemoteViews views) {
		PendingIntent pendingIntent;
		if (Service.isRunning()) {
			pendingIntent = PendingIntent.getBroadcast(context, 0,
					new Intent(context, WidgetProvider.class).setAction(ACTION_TOGGLE), 0);
			if (Service.isSuspended()) {
				views.setImageViewResource(R.id.button, R.drawable.widget_suspended);
			} else {
				views.setImageViewResource(R.id.button, R.drawable.widget_running);
			}
		} else {
			pendingIntent = PendingIntent.getActivity(context, 0, MainActivity.getAccessibilityIntent(), 0);
			views.setImageViewResource(R.id.button, R.drawable.widget_disabled);
		}
		views.setOnClickPendingIntent(R.id.button, pendingIntent);
	}
}
