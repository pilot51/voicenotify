/*
 * Copyright 2018 Mark Injerd
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

package com.pilot51.voicenotify;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Class for all the information about a notification that we use.
 */
public class NotificationInfo {
	/** The app that posted the notification. */
	private final App app;
	/** The notification's ticker message. */
	private final String ticker;
	/** The notification's subtext. */
	private final String subtext;
	/** The notification's content title. */
	private final String contentTitle;
	/** The notification's content text. */
	private final String contentText;
	/** The notification's content info. */
	private final String contentInfoText;
	/** The formatted time that this instance of NotificationInfo was created. */
	private final String time;
	/** Indicates if the notification was silenced (interrupted). */
	private boolean silenced;
	/** List of reasons this notification was ignored. */
	private final List<String> ignoreReasons = new ArrayList<>();
	
	// Suppressing lint because documentation says extras added in API 19 when actually added in API 18.
	// See reported issue: https://issuetracker.google.com/issues/69396548
	@SuppressLint("InlinedApi")
	NotificationInfo(App app, Notification notification) {
		this.app = app;
		ticker = notification.tickerText != null ? notification.tickerText.toString() : null;
		Bundle extras = notification.extras;
		subtext = extras.getString(Notification.EXTRA_SUB_TEXT);
		contentTitle = extras.getString(Notification.EXTRA_TITLE);
		contentText = extras.getString(Notification.EXTRA_TEXT);
		contentInfoText = extras.getString(Notification.EXTRA_INFO_TEXT);
		time = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Calendar.getInstance().getTime());
	}
	
	/**
	 * @return The app that posted this notification.
	 */
	App getApp() {
		return app;
	}
	
	/**
	 * Generates the string to be used for TTS.
	 * @param context Required to get preferences and string resources.
	 * @return The generated TTS message.
	 */
	String buildTtsMessage(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final String ttsStringPref = prefs.getString(context.getString(R.string.key_ttsString), "");
		String ttsUnformattedMsg = ttsStringPref
				.replace("#a", "%1$s") // App Label
				.replace("#t", "%2$s") // Ticker
				.replace("#s", "%3$s") // Subtext
				.replace("#c", "%4$s") // Content Title
				.replace("#m", "%5$s") // Content Text
				.replace("#i", "%6$s"); // Content Info Text
		String ttsMsg = null;
		try {
			ttsMsg = String.format(ttsUnformattedMsg,
					app == null ? "" : app.getLabel(),
					ticker == null ? "" : ticker.replaceAll("[|\\[\\]{}*<>]+", " "),
					subtext == null ? "" : subtext,
					contentTitle == null ? "" : contentTitle,
					contentText == null ? "" : contentText,
					contentInfoText == null ? "" : contentInfoText);
		} catch (IllegalFormatException e) {
			Log.w(Service.class.getSimpleName(), "Error formatting custom TTS string!");
			e.printStackTrace();
		}
		if (app != null && (ttsMsg == null || ttsMsg.equals(app.getLabel()))) {
			ttsMsg = context.getString(R.string.notification_from, app.getLabel());
		}
		if (!TextUtils.isEmpty(ttsMsg)) {
			String ttsTextReplace = prefs.getString(context.getString(R.string.key_ttsTextReplace), null);
			List<Pair<String, String>> textReplaceList = TextReplacePreference.convertStringToList(ttsTextReplace);
			for (Pair<String, String> pair : textReplaceList) {
				ttsMsg = ttsMsg.replaceAll("(?i)" + Pattern.quote(pair.first), pair.second);
			}
		}
		if (ttsMsg != null) {
			try {
				int maxLength = Integer.parseInt(prefs.getString(context.getString(R.string.key_max_length), null));
				if (maxLength > 0) {
					ttsMsg = ttsMsg.substring(0, Math.min(maxLength, ttsMsg.length()));
				}
			} catch (NumberFormatException e) {}
		}
		return ttsMsg;
	}
	
	/**
	 * Builds a string from the various notification texts to be displayed in {@link NotifyList}.
	 * @return The string containing notification details.
	 */
	String getLogMessage() {
		StringBuilder logBuilder = new StringBuilder();
		for (String s : new String[] {ticker, subtext, contentTitle, contentText, contentInfoText}) {
			if (!TextUtils.isEmpty(s)) {
				if (logBuilder.length() > 0) logBuilder.append("\n");
				logBuilder.append(s);
			}
		}
		return logBuilder.toString();
	}
	
	/**
	 * Gets the reasons this notification was ignored as a single string.
	 * @return Comma-separated list of ignore reasons.
	 */
	String getIgnoreReasonsAsText() {
		return getIgnoreReasons().toString().replaceAll("[\\[\\]]", "");
	}
	
	/**
	 * @return The time that this NotificationInfo was created (just after notification posted).<br />
	 * Formatted as HH:mm:ss.
	 */
	String getTime() {
		return time;
	}
	
	/**
	 * @return True if this notification was silenced (interrupted), otherwise false.
	 */
	boolean isSilenced() {
		return silenced;
	}
	
	/**
	 * Used to indicate the color of the ignore reasons in {@link NotifyList}.
	 * Yellow if silenced (interrupted), red if never spoken.
	 * @param silenced True if this notification was silenced (interrupted), otherwise false.
	 */
	void setSilenced(boolean silenced) {
		this.silenced = silenced;
	}
	
	/**
	 * Add a reason this notification was ignored.
	 * @param reason The ignore reason to add.
	 */
	void addIgnoreReason(String reason) {
		ignoreReasons.add(reason);
	}
	
	/**
	 * @return List of reasons this notification was ignored, or an empty list if not ignored.
	 */
	List<String> getIgnoreReasons() {
		return ignoreReasons;
	}
}
