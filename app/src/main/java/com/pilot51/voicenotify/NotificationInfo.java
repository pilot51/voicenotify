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

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class for all the information about a notification that we use.
 */
class NotificationInfo {
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
	/** Calendar representing the time that this instance of NotificationInfo was created. */
	private final Calendar calendar;
	/** Set of reasons this notification was ignored. */
	private final Set<IgnoreReason> ignoreReasons = new HashSet<>();
	/** Indicates if the notification was silenced (interrupted). */
	private boolean silenced;
	/** The Ignore Repeats setting in seconds, set by {@link #addIgnoreReasonIdentical(int)}. */
	private int ignoreRepeatSeconds = -1;
	/** The message that was or shall be spoken. */
	private String ttsMessage;
	
	/**
	 * @param app The app that posted the notification.
	 * @param notification The notification from which to get most of the info.
	 * @param context Required to get preferences and string resources when building the TTS message.
	 */
	// Suppressing lint because documentation says extras added in API 19 when actually added in API 18.
	// See reported issue: https://issuetracker.google.com/issues/69396548
	@SuppressLint("InlinedApi")
	NotificationInfo(App app, Notification notification, Context context) {
		this.app = app;
		ticker = notification.tickerText != null ? notification.tickerText.toString() : null;
		Bundle extras = notification.extras;
		subtext = extras.getString(Notification.EXTRA_SUB_TEXT);
		contentTitle = extras.getString(Notification.EXTRA_TITLE);
		CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
		contentText = text != null ? text.toString() : null;
		contentInfoText = extras.getString(Notification.EXTRA_INFO_TEXT);
		calendar = Calendar.getInstance();
		buildTtsMessage(context);
	}
	
	/**
	 * @return The app that posted this notification.
	 */
	App getApp() {
		return app;
	}
	
	/**
	 * @return The message that was or shall be spoken.
	 */
	String getTtsMessage() {
		return ttsMessage;
	}
	
	/**
	 * Generates the string to be used for TTS.
	 * @param context Required to get preferences and string resources.
	 */
	private void buildTtsMessage(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final String ttsStringPref = prefs.getString(context.getString(R.string.key_ttsString), "");
		String ttsUnformattedMsg = ttsStringPref
				.replace("#a", "%1$s") // App Label
				.replace("#t", "%2$s") // Ticker
				.replace("#s", "%3$s") // Subtext
				.replace("#c", "%4$s") // Content Title
				.replace("#m", "%5$s") // Content Text
				.replace("#i", "%6$s"); // Content Info Text
		try {
			ttsMessage = String.format(ttsUnformattedMsg,
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
		if (app != null && (ttsMessage == null || ttsMessage.equals(app.getLabel()))) {
			ttsMessage = context.getString(R.string.notification_from, app.getLabel());
		}
		if (!TextUtils.isEmpty(ttsMessage)) {
			String ttsTextReplace = prefs.getString(context.getString(R.string.key_ttsTextReplace), null);
			List<Pair<String, String>> textReplaceList = TextReplacePreference.convertStringToList(ttsTextReplace);
			for (Pair<String, String> pair : textReplaceList) {
				ttsMessage = ttsMessage.replaceAll("(?i)" + Pattern.quote(pair.first), pair.second);
			}
		}
		if (ttsMessage != null) {
			String maxLengthStr = prefs.getString(context.getString(R.string.key_max_length), null);
			if (!TextUtils.isEmpty(maxLengthStr)) {
				int maxLength = Integer.parseInt(maxLengthStr);
				if (maxLength > 0) {
					ttsMessage = ttsMessage.substring(0, Math.min(maxLength, ttsMessage.length()));
				}
			}
		}
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
	 * @param c Context required to get string resources.
	 * @return Comma-separated list of ignore reasons.
	 */
	String getIgnoreReasonsAsText(Context c) {
		String text = IgnoreReason.convertSetToString(ignoreReasons, c);
		if (ignoreReasons.contains(IgnoreReason.IDENTICAL)) {
			text = MessageFormat.format(text, ignoreRepeatSeconds);
		}
		return text;
	}
	
	/**
	 * @return The calendar representing the time that this NotificationInfo was created.
	 */
	Calendar getCalendar() {
		return calendar;
	}
	
	/**
	 * @return The time that this NotificationInfo was created (just after notification posted).<br />
	 * Formatted as HH:mm:ss.
	 */
	String getTime() {
		return new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(calendar.getTime());
	}
	
	/**
	 * @return True if this notification was silenced (interrupted), otherwise false.
	 */
	boolean isSilenced() {
		return silenced;
	}
	
	/**
	 * Used to indicate the color of the ignore reasons in {@link NotifyList}.
	 * Call this to set it yellow for silenced (interrupted). Default is red for never spoken.
	 */
	void setSilenced() {
		silenced = true;
	}
	
	/**
	 * Set this notification as ignored for being identical to a previous notification within the configured time
	 * @param ignoreRepeatTime The time in seconds that identical notifications are not to be spoken.
	 */
	void addIgnoreReasonIdentical(int ignoreRepeatTime) {
		ignoreRepeatSeconds = ignoreRepeatTime;
		ignoreReasons.add(IgnoreReason.IDENTICAL);
	}
	
	/**
	 * Add a reason this notification was ignored.
	 * @param reason The ignore reason to add.
	 */
	void addIgnoreReason(IgnoreReason reason) {
		ignoreReasons.add(reason);
	}
	
	/**
	 * Add a set of reasons this notification was ignored.
	 * @param reasons The ignore reasons to add.
	 */
	void addIgnoreReasons(Set<IgnoreReason> reasons) {
		ignoreReasons.addAll(reasons);
	}
	
	/**
	 * @return Set of reasons this notification was ignored, or an empty set if not ignored.
	 */
	Set<IgnoreReason> getIgnoreReasons() {
		return ignoreReasons;
	}
}
