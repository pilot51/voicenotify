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

import android.content.Context

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
	STRING(R.string.reason_string),
	EMPTY_MSG(R.string.reason_empty_msg),
	IDENTICAL(R.string.reason_identical);

	/**
	 * @param c Context required to get the string resource.
	 * @return The user-visible string for this ignore reason.
	 */
	fun getString(c: Context) = c.getString(stringId)

	companion object {
		/**
		 * Converts a set of ignore reasons to a comma-separated string.
		 * @param reasons The set to be converted.
		 * @param c Context required to get string resources.
		 * @return The resulting string.
		 */
		fun convertSetToString(reasons: Set<IgnoreReason>, c: Context): String {
			val builder = StringBuilder()
			val iterator = reasons.iterator()
			while (iterator.hasNext()) {
				builder.append(iterator.next().getString(c))
				if (iterator.hasNext()) builder.append(", ")
			}
			return builder.toString()
		}
	}
}
