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

import android.util.Pair
import androidx.lifecycle.ViewModel
import com.pilot51.voicenotify.PreferenceHelper.KEY_TTS_TEXT_REPLACE
import com.pilot51.voicenotify.PreferenceHelper.prefs
import com.pilot51.voicenotify.Common.convertTextReplaceListToString as convertListToString
import com.pilot51.voicenotify.Common.convertTextReplaceStringToList as convertStringToList

class TextReplaceDialogViewModel : ViewModel() {
	fun load(): List<Pair<String, String>?> {
		return convertStringToList(prefs.getString(KEY_TTS_TEXT_REPLACE, null))
	}

	fun save(list: List<Pair<String, String>?>) {
		val trimmedList: MutableList<Pair<String, String>> = ArrayList(list.size)
		copyLoop@ for (pair in list) {
			if (pair != null && pair.first.isNotEmpty()) {
				for (p in trimmedList) {
					if (pair.first.equals(p.first, ignoreCase = true)) {
						continue@copyLoop
					}
				}
				trimmedList.add(pair)
			}
		}
		val savedList = convertStringToList(prefs.getString(KEY_TTS_TEXT_REPLACE, null))
		var changed = false
		if (trimmedList.size != savedList.size) {
			changed = true
		} else {
			for (i in trimmedList.indices) {
				if (trimmedList[i] != savedList[i]) {
					changed = true
					break
				}
			}
		}
		if (changed) {
			prefs.edit().putString(KEY_TTS_TEXT_REPLACE, convertListToString(trimmedList)).apply()
		}
	}

	companion object {
		fun updateListItem(
			position: Int,
			textFrom: String,
			textTo: String,
			list: MutableList<Pair<String, String>?>
		) {
			var listPair: Pair<String, String>? = null
			if (position < list.size) {
				listPair = list[position]
			}
			val replacePair = Pair(textFrom, textTo)
			if (replacePair.first.isNotEmpty()) {
				if (listPair == null) {
					list.add(position, replacePair)
				} else if (replacePair.first != listPair.first
					|| replacePair.second != listPair.second) {
					list[position] = replacePair
				}
			} else if (listPair != null) {
				list.removeAt(position)
			}
		}

		/**
		 * Checks if text is a duplicate of another entry in [list],
		 * otherwise clears error if not a duplicate.
		 * @param position The position of this entry in [list] to prevent detecting self as duplicate.
		 * @param textFrom The text to check if duplicated.
		 */
		fun isDuplicate(
			position: Int,
			textFrom: String,
			list: List<Pair<String, String>?>
		): Boolean {
			for (p in list) {
				if (p != null && list.indexOf(p) < position &&
					textFrom.equals(p.first, ignoreCase = true)
				) return true
			}
			return false
		}
	}
}
