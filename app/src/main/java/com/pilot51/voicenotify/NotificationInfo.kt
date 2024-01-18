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
import androidx.core.app.NotificationCompat
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_MAX_LENGTH
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
	private val extras by notification::extras
	val id = extras.getInt(NotificationCompat.EXTRA_NOTIFICATION_ID)
	val category: String? = notification.category
	val progress = extras.getInt(Notification.EXTRA_PROGRESS).takeUnless { it == 0 }
	val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX).takeUnless { it == 0 }
	val progressIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
	/** The notification's ticker message. */
	val ticker = notification.tickerText?.toString()
	/** The notification's subtext. */
	val subtext = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
	/** The notification's content title. */
	val contentTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
	/** The notification's content text. */
	val contentText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
	/** The notification's content info. */
	val contentInfoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
	/** The notification's big content title. */
	val bigContentTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
	/** The notification's big content summary. */
	val bigContentSummary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
	/** The notification's big content text. */
	val bigContentText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
	/** The notification's lines of text for [Notification.InboxStyle]. */
	val textLines: Array<CharSequence>? = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
	/** Calendar representing the time that this instance of NotificationInfo was created. */
	val calendar: Calendar = Calendar.getInstance()
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
	/** The Ignore Repeats setting in seconds, set by [addIgnoreReasonIdentical]. */
	private var ignoreRepeatSeconds = -1
	/** The message that was or shall be spoken. */
	var ttsMessage: String? = null
		private set

	init {
		buildTtsMessage()
	}

	/** Generates the string to be used for TTS. */
	private fun buildTtsMessage() {
		val isComposePreview = notification.`when` == Long.MIN_VALUE
		val ttsStringPref = if (isComposePreview) DEFAULT_TTS_STRING else {
			prefs.getString(KEY_TTS_STRING, null) ?: DEFAULT_TTS_STRING
		}
		val ttsUnformattedMsg = ttsStringPref
			.replace("#A", "%1\$s", true) // App Label
			.replace("#T", "%2\$s", true) // Ticker
			.replace("#S", "%3\$s", true) // Subtext
			.replace("#C", "%4\$s", true) // Content Title
			.replace("#M", "%5\$s", true) // Content Text
			.replace("#I", "%6\$s", true) // Content Info Text
			.replace("#H", "%7\$s", true) // Big Content Title
			.replace("#Y", "%8\$s", true) // Big Content Summary
			.replace("#B", "%9\$s", true) // Big Content Text
			.replace("#L", "%10\$s", true) // Text Lines
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
				bigContentText ?: "",
				textLines?.joinToString("\n") ?: "")
		} catch (e: IllegalFormatException) {
			Log.w(TAG, "Error formatting custom TTS string!")
			e.printStackTrace()
		}
		isEmpty = ttsMessage.isNullOrBlank() ||
			ttsMessage == ttsStringPref.replace(Regex("#[atscmi]"), "")
		if (app != null && (ttsMessage == null || ttsMessage == app.label) && !isComposePreview) {
			ttsMessage = appContext.getString(R.string.notification_from, app.label)
		}
		ttsMessage?.takeIf { it.isNotEmpty() }?.let {
			val ttsTextReplace = if (isComposePreview) null else {
				prefs.getString(KEY_TTS_TEXT_REPLACE, null)
			}
			val textReplaceList = Common.convertTextReplaceStringToList(ttsTextReplace)
			for (pair in textReplaceList) {
				ttsMessage = it.replace(
					"(?i)${Pattern.quote(pair.first)}".toRegex(), pair.second)
			}
			val maxLength = if (isComposePreview) 0 else {
				prefs.getString(KEY_MAX_LENGTH, null)?.toIntOrNull() ?: DEFAULT_MAX_LENGTH
			}
			if (maxLength > 0 && maxLength < it.length) {
				ttsMessage = it.substring(0, min(maxLength, it.length))
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
