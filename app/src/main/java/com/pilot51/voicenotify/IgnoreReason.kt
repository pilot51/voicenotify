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

import com.pilot51.voicenotify.VNApplication.Companion.appContext

enum class IgnoreReason(
	private val stringId: Int
) {
	SERVICE_STOPPED(R.string.reason_service_stopped),
	SUSPENDED(R.string.reason_suspended),
	QUIET(R.string.reason_quiet),
	SHAKE(R.string.reason_shake),
	SILENT(R.string.reason_silent),
	CALL(R.string.reason_call),
	SCREEN_OFF(R.string.reason_screen_off),
	SCREEN_ON(R.string.reason_screen_on),
	HEADSET_OFF(R.string.reason_headset_off),
	HEADSET_ON(R.string.reason_headset_on),
	APP(R.string.reason_app),
	STRING_REQUIRED(R.string.reason_string_required),
	STRING_IGNORED(R.string.reason_string_ignored),
	EMPTY_MSG(R.string.reason_empty_msg),
	IDENTICAL(R.string.reason_identical),
	TTS_FAILED(R.string.reason_tts_failed),
	TTS_RESTARTED(R.string.reason_tts_restarted),
	TTS_INTERRUPTED(R.string.reason_tts_interrupted),
	TTS_LENGTH_LIMIT(R.string.reason_tts_length_limit);

	/** @return The user-visible string for this ignore reason. */
	override fun toString() = appContext.getString(stringId)
}
