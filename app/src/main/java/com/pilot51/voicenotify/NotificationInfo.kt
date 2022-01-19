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
package com.pilot51.voicenotify

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.preference.PreferenceManager
import com.pilot51.voicenotify.IgnoreReason.Companion.convertSetToString
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Class for all the information about a notification that we use.
 * @param app The app that posted the notification.
 * @param notification The notification from which to get most of the info.
 * @param context Required to get preferences and string resources when building the TTS message.
 */
// Suppressing lint because documentation says extras added in API 19 when actually added in API 18.
// See reported issue: https://issuetracker.google.com/issues/69396548
class NotificationInfo @SuppressLint("InlinedApi") constructor(
	val app: App?,
	notification: Notification,
	context: Context
) {
	/** The notification's ticker message. */
	private val ticker = notification.tickerText?.toString()
	/** The notification's subtext. */
	private val subtext: String?
	/** The notification's content title. */
	private val contentTitle: String?
	/** The notification's content text. */
	private val contentText: String?
	/** The notification's content info. */
	private val contentInfoText: String?
	/** Calendar representing the time that this instance of NotificationInfo was created. */
	val calendar: Calendar
	/** Set of reasons this notification was ignored. */
	private val ignoreReasons: MutableSet<IgnoreReason> = HashSet()
	/** Indicates if the notification was silenced (interrupted). */
	var isSilenced = false
		private set
	/** The Ignore Repeats setting in seconds, set by [.addIgnoreReasonIdentical]. */
	private var ignoreRepeatSeconds = -1
	/** The message that was or shall be spoken. */
	var ttsMessage: String? = null
		private set

	init {
		val extras = notification.extras
		subtext = extras.getString(Notification.EXTRA_SUB_TEXT)
		contentTitle = extras.getString(Notification.EXTRA_TITLE)
		val text = extras.getCharSequence(Notification.EXTRA_TEXT)
		contentText = text?.toString()
		contentInfoText = extras.getString(Notification.EXTRA_INFO_TEXT)
		calendar = Calendar.getInstance()
		buildTtsMessage(context)
	}

	/**
	 * Generates the string to be used for TTS.
	 * @param context Required to get preferences and string resources.
	 */
	private fun buildTtsMessage(context: Context) {
		val prefs = PreferenceManager.getDefaultSharedPreferences(context)
		val ttsStringPref = prefs.getString(context.getString(R.string.key_ttsString), "")!!
		val ttsUnformattedMsg = ttsStringPref
			.replace("#a", "%1\$s") // App Label
			.replace("#t", "%2\$s") // Ticker
			.replace("#s", "%3\$s") // Subtext
			.replace("#c", "%4\$s") // Content Title
			.replace("#m", "%5\$s") // Content Text
			.replace("#i", "%6\$s") // Content Info Text
		try {
			ttsMessage = String.format(ttsUnformattedMsg,
				app?.label ?: "",
				ticker?.replace("[|\\[\\]{}*<>]+".toRegex(), " ") ?: "",
				subtext ?: "",
				contentTitle ?: "",
				contentText ?: "",
				contentInfoText ?: "")
		} catch (e: IllegalFormatException) {
			Log.w(Service::class.simpleName, "Error formatting custom TTS string!")
			e.printStackTrace()
		}
		if (app != null && (ttsMessage == null || ttsMessage == app.label)) {
			ttsMessage = context.getString(R.string.notification_from, app.label)
		}
		if (!TextUtils.isEmpty(ttsMessage)) {
			val ttsTextReplace = prefs.getString(context.getString(R.string.key_ttsTextReplace), null)
			val textReplaceList = TextReplacePreference.convertStringToList(ttsTextReplace)
			for (pair in textReplaceList) {
				ttsMessage = ttsMessage!!.replace("(?i)${Pattern.quote(pair.first)}".toRegex(), pair.second)
			}
		}
		if (ttsMessage != null) {
			val maxLengthStr = prefs.getString(context.getString(R.string.key_max_length), null)
			if (!TextUtils.isEmpty(maxLengthStr)) {
				val maxLength = maxLengthStr!!.toInt()
				if (maxLength > 0) {
					ttsMessage = ttsMessage!!.substring(0, min(maxLength, ttsMessage!!.length))
				}
			}
		}
	}

	/**
	 * Builds a string from the various notification texts to be displayed in [NotifyList].
	 * @return The string containing notification details.
	 */
	val logMessage: String
		get() {
			val logBuilder = StringBuilder()
			for (s in arrayOf(ticker, subtext, contentTitle, contentText, contentInfoText)) {
				if (!TextUtils.isEmpty(s)) {
					if (logBuilder.isNotEmpty()) logBuilder.append("\n")
					logBuilder.append(s)
				}
			}
			return logBuilder.toString()
		}

	/**
	 * Gets the reasons this notification was ignored as a single string.
	 * @param c Context required to get string resources.
	 * @return Comma-separated list of ignore reasons.
	 */
	fun getIgnoreReasonsAsText(c: Context?): String {
		var text = convertSetToString(ignoreReasons, c!!)
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
	 * Used to indicate the color of the ignore reasons in [NotifyList].
	 * Call this to set it yellow for silenced (interrupted). Default is red for never spoken.
	 */
	fun setSilenced() {
		isSilenced = true
	}

	/**
	 * Set this notification as ignored for being identical to a previous notification within the configured time
	 * @param ignoreRepeatTime The time in seconds that identical notifications are not to be spoken.
	 */
	fun addIgnoreReasonIdentical(ignoreRepeatTime: Int) {
		ignoreRepeatSeconds = ignoreRepeatTime
		ignoreReasons.add(IgnoreReason.IDENTICAL)
	}

	/**
	 * Add a reason this notification was ignored.
	 * @param reason The ignore reason to add.
	 */
	fun addIgnoreReason(reason: IgnoreReason) {
		ignoreReasons.add(reason)
	}

	/**
	 * Add a set of reasons this notification was ignored.
	 * @param reasons The ignore reasons to add.
	 */
	fun addIgnoreReasons(reasons: Set<IgnoreReason>?) {
		ignoreReasons.addAll(reasons!!)
	}

	/**
	 * @return Set of reasons this notification was ignored, or an empty set if not ignored.
	 */
	fun getIgnoreReasons(): Set<IgnoreReason> {
		return ignoreReasons
	}
}
