/*
 * Copyright 2011-2024 Mark Injerd
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

import android.app.Notification
import android.util.Log
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_TTS_STRING
import com.pilot51.voicenotify.PreferenceHelper.KEY_MAX_LENGTH
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_STRING
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_TEXT_REPLACE
import com.pilot51.voicenotify.PreferenceHelper.prefs
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Class for all the information about a notification that we use.
 * @param app The app that posted the notification.
 * @param notification The notification from which to get most of the info.
 */
data class NotificationInfo(
	val app: App?,
	private val notification: Notification
) {
	/** The notification's ticker message. */
	val ticker = notification.tickerText?.toString()
	/** The notification's subtext. */
	val subtext: String?
	/** The notification's content title. */
	val contentTitle: String?
	/** The notification's content text. */
	val contentText: String?
	/** The notification's content info. */
	val contentInfoText: String?
	/** The notification's big content title. */
	val bigContentTitle: String?
	/** The notification's big content summary. */
	val bigContentSummary: String?
	/** The notification's big content text. */
	val bigContentText: String?
	/** Calendar representing the time that this instance of NotificationInfo was created. */
	val calendar: Calendar
	var isEmpty = false
		private set
	/** Set of reasons this notification was ignored, or an empty set if not ignored. */
	val ignoreReasons = linkedSetOf<IgnoreReason>()
	/**
	 * Indicates if the notification was interrupted during speech.
	 * Used to set the color of the ignore reasons in [NotifyList].
	 * Set to `true` to make the ignore message yellow.
	 * Default is red for never spoken.
	 */
	var isInterrupted = false
	/** The Ignore Repeats setting in seconds, set by [.addIgnoreReasonIdentical]. */
	private var ignoreRepeatSeconds = -1
	/** The message that was or shall be spoken. */
	var ttsMessage: String? = null
		private set

	init {
		val extras = notification.extras
		subtext = extras.getString(Notification.EXTRA_SUB_TEXT)
		contentTitle = extras.getString(Notification.EXTRA_TITLE)
		contentText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
		contentInfoText = extras.getString(Notification.EXTRA_INFO_TEXT)
		bigContentTitle = extras.getString(Notification.EXTRA_TITLE_BIG)
		bigContentSummary = extras.getString(Notification.EXTRA_SUMMARY_TEXT)
		bigContentText = extras.getString(Notification.EXTRA_BIG_TEXT)
		calendar = Calendar.getInstance()
		buildTtsMessage()
	}

	/** Generates the string to be used for TTS. */
	private fun buildTtsMessage() {
		val isComposePreview = notification.`when` == Long.MIN_VALUE
		val ttsStringPref = if (isComposePreview) DEFAULT_TTS_STRING else {
			prefs.getString(KEY_TTS_STRING, null) ?: DEFAULT_TTS_STRING
		}
		val ttsUnformattedMsg = ttsStringPref
			.replace("#a", "%1\$s") // App Label
			.replace("#t", "%2\$s") // Ticker
			.replace("#s", "%3\$s") // Subtext
			.replace("#c", "%4\$s") // Content Title
			.replace("#m", "%5\$s") // Content Text
			.replace("#i", "%6\$s") // Content Info Text
			.replace("#h", "%7\$s") // Big Content Title
			.replace("#y", "%8\$s") // Big Content Summary
			.replace("#b", "%9\$s") // Big Content Text
		try {
			ttsMessage = String.format(ttsUnformattedMsg,
				app?.label ?: "",
				ticker?.replace("[|\\[\\]{}*<>]+".toRegex(), " ") ?: "",
				subtext ?: "",
				contentTitle ?: "",
				contentText ?: "",
				contentInfoText ?: "",
				bigContentTitle ?: "",
				bigContentSummary ?: "",
				bigContentText ?: "")
		} catch (e: IllegalFormatException) {
			Log.w(TAG, "Error formatting custom TTS string!")
			e.printStackTrace()
		}
		isEmpty = ttsMessage.isNullOrBlank() ||
			ttsMessage == ttsStringPref.replace(Regex("#[atscmi]"), "")
		if (app != null && (ttsMessage == null || ttsMessage == app.label) && !isComposePreview) {
			ttsMessage = appContext.getString(R.string.notification_from, app.label)
		}
		if (!ttsMessage.isNullOrEmpty()) {
			val ttsTextReplace = if (isComposePreview) null else {
				prefs.getString(KEY_TTS_TEXT_REPLACE, null)
			}
			val textReplaceList = Common.convertTextReplaceStringToList(ttsTextReplace)
			for (pair in textReplaceList) {
				ttsMessage = ttsMessage!!.replace(
					"(?i)${Pattern.quote(pair.first)}".toRegex(), pair.second)
			}
		}
		if (ttsMessage != null) {
			try {
				val maxLength = if (isComposePreview) 0 else {
					prefs.getString(KEY_MAX_LENGTH, null)
						?.takeIf { it.isNotEmpty() }?.toInt() ?: 0
				}
				if (maxLength > 0) {
					ttsMessage = ttsMessage!!.substring(0, min(maxLength, ttsMessage!!.length))
				}
			} catch (e: NumberFormatException) {
				Log.w(TAG, "Failed to parse Maximum Message: ${e.message}")
			}
		}
	}

	/**
	 * Gets the reasons this notification was ignored as a single string.
	 * @return Comma-separated list of ignore reasons.
	 */
	fun getIgnoreReasonsAsText(): String {
		var text = ignoreReasons.joinToString()
			// If message ends with period and isn't last, replaces comma after it with newline
			.replace("., ", ".\n")
			// If message ends with period and isn't first, replaces comma before it with newline
			.replace(Regex(", (.+\\.)$"), "\n$1")
		if (ignoreReasons.contains(IgnoreReason.IDENTICAL)) {
			text = MessageFormat.format(text, ignoreRepeatSeconds)
		}
		return text
	}

	/**
	 * @return The time that this NotificationInfo was created (just after notification posted).<br></br>
	 * Formatted as HH:mm:ss.
	 */
	val time: String
		get() = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(calendar.time)

	/**
	 * Set this notification as ignored for being identical to a previous notification within the configured time
	 * @param ignoreRepeatTime The time in seconds that identical notifications are not to be spoken.
	 */
	fun addIgnoreReasonIdentical(ignoreRepeatTime: Int) {
		ignoreRepeatSeconds = ignoreRepeatTime
		ignoreReasons.add(IgnoreReason.IDENTICAL)
	}

	// For some reason this is needed to force list state update with simple object copy
	override fun equals(other: Any?) = super.equals(other)
	override fun hashCode() = super.hashCode()

	companion object {
		private val TAG = NotificationInfo::class.simpleName
	}
}
