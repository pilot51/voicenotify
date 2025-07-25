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
package com.pilot51.voicenotify

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import com.pilot51.voicenotify.prefs.db.App
import com.pilot51.voicenotify.prefs.db.Settings
import com.pilot51.voicenotify.prefs.db.Settings.Companion.DEFAULT_SPEAK_EMOJIS
import com.pilot51.voicenotify.ui.PreferencesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.lang.Character.UnicodeBlock
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.math.min

/**
 * Class for all the information about a notification that we use.
 * @param app The app that posted the notification.
 * @param sbn The [StatusBarNotification] from which to get most of the info.
 */
data class NotificationInfo(
	val app: App?,
	private val sbn: StatusBarNotification,
	val settings: Settings
) {
	private val notification = sbn.notification
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
	/** The [Instant] when the notification was posted. */
	val instant: Instant = Instant.ofEpochMilli(sbn.postTime)
	val flagsText by lazy { flagsToString(notification.flags) }
	var isEmpty = false
		private set
	private val ignoreReasonsState = MutableStateFlow<Set<IgnoreReason>>(linkedSetOf())
	/** A flow of the reasons this notification was ignored or silenced as a string. */
	val ignoreReasonsTextFlow = ignoreReasonsState.map { it.asString() }
	/** Set of reasons, if any, this notification was ignored or silenced. */
	var ignoreReasons: Set<IgnoreReason>
		get() = ignoreReasonsState.value
		private set(value) { ignoreReasonsState.value = value }
	/**
	 * Indicates if the notification was interrupted during speech.
	 * Used to set the color of the ignore reasons in [NotifyList].
	 * Set to `true` to make the ignore message yellow.
	 * Default is red for never spoken.
	 */
	var isInterrupted by mutableStateOf(false)
	/** The Ignore Repeats setting in seconds, set by [addIgnoreReasonIdentical]. */
	private var ignoreRepeatSeconds = -1
	/** The TTS Message setting. */
	private lateinit var ttsStringPref: String
	/** The message that was or shall be spoken. */
	var ttsMessage: String? = null
		private set

	init {
		buildTtsMessage()
	}

	/** Generates the string to be used for TTS. */
	private fun buildTtsMessage() {
		ttsStringPref = settings.ttsString ?: ""
		val ttsFormat = ttsStringPref
			.replace(TTS_APP_LABEL, "%1\$s", true)
			.replace(TTS_TICKER, "%2\$s", true)
			.replace(TTS_SUBTEXT, "%3\$s", true)
			.replace(TTS_CONTENT_TITLE, "%4\$s", true)
			.replace(TTS_CONTENT_TEXT, "%5\$s", true)
			.replace(TTS_CONTENT_INFO_TEXT, "%6\$s", true)
			.replace(TTS_BIG_CONTENT_TITLE, "%7\$s", true)
			.replace(TTS_BIG_CONTENT_SUMMARY, "%8\$s", true)
			.replace(TTS_BIG_CONTENT_TEXT, "%9\$s", true)
			.replace(TTS_TEXT_LINES, "%10\$s", true)
			.trim()
		try {
			ttsMessage = String.format(
				ttsFormat,
				app?.label ?: "",
				ticker?.replace("[|\\[\\]{}*<>]+".toRegex(), " ") ?: "",
				subtext ?: "",
				contentTitle ?: "",
				contentText ?: "",
				contentInfoText ?: "",
				bigContentTitle ?: "",
				bigContentSummary ?: "",
				bigContentText ?: "",
				textLines?.joinToString("\n") ?: ""
			).trim()
		} catch (e: IllegalFormatException) {
			Log.w(TAG, "Error formatting custom TTS string!\n$e")
		}
		isEmpty = isUnusedOrBlank(TTS_TICKER, ticker) &&
			isUnusedOrBlank(TTS_SUBTEXT, subtext) &&
			isUnusedOrBlank(TTS_CONTENT_TITLE, contentTitle) &&
			isUnusedOrBlank(TTS_CONTENT_TEXT, contentText) &&
			isUnusedOrBlank(TTS_CONTENT_INFO_TEXT, contentInfoText) &&
			isUnusedOrBlank(TTS_BIG_CONTENT_TITLE, bigContentTitle) &&
			isUnusedOrBlank(TTS_BIG_CONTENT_SUMMARY, bigContentSummary) &&
			isUnusedOrBlank(TTS_BIG_CONTENT_TEXT, bigContentText) &&
			(!ttsStringPref.contains(TTS_TEXT_LINES, true) || textLines.run {
				isNullOrEmpty() || all { it.isBlank() }
			})
		if (app != null
			&& (ttsMessage == null || (ttsMessage == app.label && !ttsStringPref.equals(TTS_APP_LABEL, true)))
		) {
			ttsMessage = appContext.getString(R.string.notification_from, app.label)
		}
		if (ttsMessage.isNullOrBlank()) return
		val ttsTextReplace = settings.ttsTextReplace
		val textReplaceList = PreferencesViewModel.convertTextReplaceStringToList(ttsTextReplace)
		for (pair in textReplaceList) {
			val pattern = try {
				if (pair.first.startsWith(Constants.REGEX_PREFIX))
					Regex(pair.first.removePrefix(Constants.REGEX_PREFIX))
				else
					null
			} catch (e: PatternSyntaxException) {
				Log.w(TAG, e)
				null
			} ?: "(?i)${Pattern.quote(pair.first)}".toRegex()
			try {
				ttsMessage = ttsMessage!!.replace(pattern, pair.second)
			} catch (e: IndexOutOfBoundsException) { // Catches "no group" regex error
				Log.w(TAG, e)
			}
		}
		val speakEmojis = settings.ttsSpeakEmojis ?: DEFAULT_SPEAK_EMOJIS
		if (!speakEmojis) {
			ttsMessage = ttsMessage!!.removeEmojis().trim()
		}
		settings.ttsMaxLength?.takeIf { it > 0 }?.let { maxLength ->
			val msgLength = ttsMessage!!.length
			if (maxLength < msgLength) {
				ttsMessage = ttsMessage!!.substring(0, min(maxLength, msgLength))
			}
		}
	}

	/** @return `true` if [tag] is not in [ttsStringPref] or [text] is null or blank. */
	private fun isUnusedOrBlank(tag: String, text: String?) =
		!ttsStringPref.contains(tag, true) || text.isNullOrBlank()

	/** @return The reasons this notification was ignored or silenced as a string. */
	fun getIgnoreReasonsAsText() = ignoreReasons.asString()

	private fun Set<IgnoreReason>.asString(): String {
		var text = joinToString()
			// If message ends with period and isn't last, replaces comma after it with newline
			.replace("., ", ".\n")
			// If message ends with period and isn't first, replaces comma before it with newline
			.replace(Regex(", (.+\\.)$"), "\n$1")
		if (contains(IgnoreReason.IDENTICAL)) {
			text = MessageFormat.format(text, ignoreRepeatSeconds)
		}
		return text
	}

	/** @return The time that the notification was posted. Format: HH:mm:ss */
	val time: String
		get() = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault())
		.format(instant)

	/** @return The date and time that the notification was posted. Format: yyyy-MM-dd HH:mm:ss.SSS */
	val dateTime: String
		get() = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
		.withZone(ZoneId.systemDefault())
		.format(instant)

	/**
	 * Set this notification as ignored for being identical to a previous notification within the configured time
	 * @param ignoreRepeatTime The time in seconds that identical notifications are not to be spoken.
	 */
	fun addIgnoreReasonIdentical(ignoreRepeatTime: Int) {
		ignoreRepeatSeconds = ignoreRepeatTime
		addIgnoreReasons(IgnoreReason.IDENTICAL)
	}

	fun addIgnoreReasons(vararg reasons: IgnoreReason) {
		ignoreReasons += reasons
	}

	// For some reason this is needed to force list state update with simple object copy
	override fun equals(other: Any?) = super.equals(other)
	override fun hashCode() = super.hashCode()

	companion object {
		private val TAG = NotificationInfo::class.simpleName
		const val TTS_APP_LABEL = "#A"
		const val TTS_TICKER = "#T"
		const val TTS_SUBTEXT = "#S"
		const val TTS_CONTENT_TITLE = "#C"
		const val TTS_CONTENT_TEXT = "#M"
		const val TTS_CONTENT_INFO_TEXT = "#I"
		const val TTS_BIG_CONTENT_TITLE = "#H"
		const val TTS_BIG_CONTENT_SUMMARY = "#Y"
		const val TTS_BIG_CONTENT_TEXT = "#B"
		const val TTS_TEXT_LINES = "#L"
		private val flagsMap = mapOf(
			Notification.FLAG_SHOW_LIGHTS to "SHOW_LIGHTS",
			Notification.FLAG_ONGOING_EVENT to "ONGOING_EVENT",
			Notification.FLAG_INSISTENT to "INSISTENT",
			Notification.FLAG_ONLY_ALERT_ONCE to "ONLY_ALERT_ONCE",
			Notification.FLAG_AUTO_CANCEL to "AUTO_CANCEL",
			Notification.FLAG_NO_CLEAR to "NO_CLEAR",
			Notification.FLAG_FOREGROUND_SERVICE to "FOREGROUND_SERVICE",
			Notification.FLAG_HIGH_PRIORITY to "HIGH_PRIORITY",
			Notification.FLAG_LOCAL_ONLY to "LOCAL_ONLY",
			Notification.FLAG_GROUP_SUMMARY to "GROUP_SUMMARY",
			0x0400 to "AUTOGROUP_SUMMARY",
			0x0800 to "CAN_COLORIZE",
			(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Notification.FLAG_BUBBLE else 0x1000) to "BUBBLE",
			0x2000 to "NO_DISMISS",
			0x4000 to "FSI_REQUESTED_BUT_DENIED",
			0x8000 to "USER_INITIATED_JOB"
		)

		private fun flagsToString(flags: Int) =
			flagsMap.filter { (flag, _) -> flags and flag != 0 }.values.joinToString("\n")

		private fun String.removeEmojis() = filterNot {
			Character.isSurrogate(it) || UnicodeBlock.of(it).isAny(
				UnicodeBlock.DINGBATS,
				UnicodeBlock.MISCELLANEOUS_SYMBOLS,
			)
		}
	}
}
