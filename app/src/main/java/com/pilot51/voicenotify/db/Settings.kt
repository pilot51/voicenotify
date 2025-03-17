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
package com.pilot51.voicenotify.db

import android.media.AudioManager
import androidx.room.*
import com.pilot51.voicenotify.NotificationInfo.Companion.TTS_APP_LABEL
import com.pilot51.voicenotify.NotificationInfo.Companion.TTS_CONTENT_TEXT
import com.pilot51.voicenotify.NotificationInfo.Companion.TTS_CONTENT_TITLE
import com.pilot51.voicenotify.isNone
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

@Entity(
	tableName = "settings",
	indices = [ Index(value = ["app_package"], unique = true) ],
	foreignKeys = [ ForeignKey(
		App::class,
		parentColumns = ["package"],
		childColumns = ["app_package"]
	) ]
)
data class Settings(
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "id")
	val id: Long = 0,
	/** If `null`, these are the global settings. */
	@ColumnInfo(name = "app_package")
	val appPackage: String? = null,
	@ColumnInfo(name = "audio_focus")
	val audioFocus: Boolean? = null,
	@ColumnInfo(name = "require_strings")
	val requireStrings: String? = null,
	@ColumnInfo(name = "ignore_strings")
	val ignoreStrings: String? = null,
	@ColumnInfo(name = "ignore_empty")
	val ignoreEmpty: Boolean? = null,
	@ColumnInfo(name = "ignore_groups")
	val ignoreGroups: Boolean? = null,
	/** Number of minutes to ignore repeats. -1 is infinite. */
	@ColumnInfo(name = "ignore_repeat")
	val ignoreRepeat: Int? = null,
	@ColumnInfo(name = "speak_screen_off")
	val speakScreenOff: Boolean? = null,
	@ColumnInfo(name = "speak_screen_on")
	val speakScreenOn: Boolean? = null,
	@ColumnInfo(name = "speak_headset_off")
	val speakHeadsetOff: Boolean? = null,
	@ColumnInfo(name = "speak_headset_on")
	val speakHeadsetOn: Boolean? = null,
	@ColumnInfo(name = "speak_silent_on")
	val speakSilentOn: Boolean? = null,
	@ColumnInfo(name = "quiet_start")
	val quietStart: Int? = null,
	@ColumnInfo(name = "quiet_end")
	val quietEnd: Int? = null,
	@ColumnInfo(name = "tts_string")
	val ttsString: String? = null,
	@ColumnInfo(name = "tts_text_replace")
	val ttsTextReplace: String? = null,
	@ColumnInfo(name = "tts_speak_emojis")
	val ttsSpeakEmojis: Boolean? = null,
	@ColumnInfo(name = "tts_max_length")
	val ttsMaxLength: Int? = null,
	/** The selected audio stream matching the `STREAM_` constant from [AudioManager]. */
	@ColumnInfo(name = "tts_stream")
	val ttsStream: Int? = null,
	@ColumnInfo(name = "tts_delay")
	val ttsDelay: Int? = null,
	@ColumnInfo(name = "tts_repeat")
	val ttsRepeat: Double? = null
) {
	@Ignore
	val isGlobal = appPackage == null

	/**
	 * Creates a new instance where only the `null` values
	 * in [overrides] fall back to the values in `this`.
	 * The result should not be saved to the database.
	 * @throws IllegalStateException if `this` is not the global settings.
	 */
	fun merge(overrides: Settings): Settings {
		if (!isGlobal) throw IllegalStateException(
			"Must only be called on the global settings instance." +
				" Called on id=$id appPackage=$appPackage"
		)
		return copy(
			id = 0,
			appPackage = overrides.appPackage,
			audioFocus = overrides.audioFocus ?: audioFocus,
			requireStrings = overrides.requireStrings ?: requireStrings,
			ignoreStrings = overrides.ignoreStrings ?: ignoreStrings,
			ignoreEmpty = overrides.ignoreEmpty ?: ignoreEmpty,
			ignoreGroups = overrides.ignoreGroups ?: ignoreGroups,
			ignoreRepeat = overrides.ignoreRepeat ?: ignoreRepeat,
			speakScreenOff = overrides.speakScreenOff ?: speakScreenOff,
			speakScreenOn = overrides.speakScreenOn ?: speakScreenOn,
			speakHeadsetOff = overrides.speakHeadsetOff ?: speakHeadsetOff,
			speakHeadsetOn = overrides.speakHeadsetOn ?: speakHeadsetOn,
			speakSilentOn = overrides.speakSilentOn ?: speakSilentOn,
			quietStart = overrides.quietStart ?: quietStart,
			quietEnd = overrides.quietEnd ?: quietEnd,
			ttsString = overrides.ttsString ?: ttsString,
			ttsTextReplace = overrides.ttsTextReplace ?: ttsTextReplace,
			ttsSpeakEmojis = overrides.ttsSpeakEmojis ?: ttsSpeakEmojis,
			ttsMaxLength = overrides.ttsMaxLength ?: ttsMaxLength,
			ttsStream = overrides.ttsStream ?: ttsStream,
			ttsDelay = overrides.ttsDelay ?: ttsDelay,
			ttsRepeat = overrides.ttsRepeat ?: ttsRepeat
		)
	}

	@Suppress("UNCHECKED_CAST")
	fun areAllSettingsNull() = this::class.declaredMemberProperties.filter {
		it.name.isNone(::id.name, ::appPackage.name, ::isGlobal.name)
	}.all {
		(it as KProperty1<Settings, *>).get(this) == null
	}

	companion object {
		const val DEFAULT_AUDIO_FOCUS = true
		const val DEFAULT_IGNORE_EMPTY = true
		const val DEFAULT_IGNORE_GROUPS = true
		const val DEFAULT_IGNORE_REPEAT = 10
		const val DEFAULT_QUIET_TIME = 0
		const val DEFAULT_SPEAK_SCREEN_OFF = true
		const val DEFAULT_SPEAK_SCREEN_ON = true
		const val DEFAULT_SPEAK_HEADSET_OFF = true
		const val DEFAULT_SPEAK_HEADSET_ON = true
		const val DEFAULT_SPEAK_SILENT_ON = false
		const val DEFAULT_TTS_STRING = "${TTS_APP_LABEL}\n${TTS_CONTENT_TITLE}\n${TTS_CONTENT_TEXT}"
		const val DEFAULT_SPEAK_EMOJIS = true
		const val DEFAULT_MAX_LENGTH = 500
		const val DEFAULT_TTS_STREAM = AudioManager.STREAM_MUSIC
		val defaults get() = Settings(
			id = 1,
			appPackage = null,
			audioFocus = DEFAULT_AUDIO_FOCUS,
			requireStrings = null,
			ignoreStrings = null,
			ignoreEmpty = DEFAULT_IGNORE_EMPTY,
			ignoreGroups = DEFAULT_IGNORE_GROUPS,
			ignoreRepeat = DEFAULT_IGNORE_REPEAT,
			speakScreenOff = DEFAULT_SPEAK_SCREEN_OFF,
			speakScreenOn = DEFAULT_SPEAK_SCREEN_ON,
			speakHeadsetOff = DEFAULT_SPEAK_HEADSET_OFF,
			speakHeadsetOn = DEFAULT_SPEAK_HEADSET_ON,
			speakSilentOn = DEFAULT_SPEAK_SILENT_ON,
			quietStart = DEFAULT_QUIET_TIME,
			quietEnd = DEFAULT_QUIET_TIME,
			ttsString = DEFAULT_TTS_STRING,
			ttsTextReplace = null,
			ttsSpeakEmojis = DEFAULT_SPEAK_EMOJIS,
			ttsMaxLength = DEFAULT_MAX_LENGTH,
			ttsStream = DEFAULT_TTS_STREAM,
			ttsDelay = null,
			ttsRepeat = null
		)
	}
}
