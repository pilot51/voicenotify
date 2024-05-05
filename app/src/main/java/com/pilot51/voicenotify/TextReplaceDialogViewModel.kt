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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pilot51.voicenotify.PreferenceHelper.globalSettings
import com.pilot51.voicenotify.PreferenceHelper.globalSettingsFlow
import com.pilot51.voicenotify.PreferenceHelper.save
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TextReplaceDialogViewModel : ViewModel() {
	val ttsTextReplace @Composable get() = globalSettingsFlow.map {
		convertStringToList(it.ttsTextReplace)
	}.collectAsState(initial = listOf())

	fun save(list: List<Pair<String, String>?>) {
		viewModelScope.launch {
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
			val savedList = convertStringToList(globalSettings.ttsTextReplace)
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
				globalSettings.run {
					ttsTextReplace = convertListToString(trimmedList)
					save()
				}
			}
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

		private fun convertListToString(list: List<Pair<String, String>?>): String? {
			if (list.filterNotNull().isEmpty()) return null
			val saveString = StringBuilder()
			for (pair in list) {
				if (pair == null) break
				if (saveString.isNotEmpty()) {
					saveString.append("\n")
				}
				saveString.append(pair.first)
				saveString.append("\n")
				saveString.append(pair.second)
			}
			return saveString.toString().ifEmpty { null }
		}

		/**
		 * Converts a string of paired substrings separated by newlines into a list of string pairs.
		 * @param string The string to convert. Each string in and between pairs must be separated by a newline.
		 * There should be an odd number of newlines for an even number of substrings (including zero-length),
		 * otherwise the last substring will be discarded.
		 * @return A List of string pairs.
		 */
		fun convertStringToList(string: String?): List<Pair<String, String>> {
			val list = mutableListOf<Pair<String, String>>()
			if (string.isNullOrEmpty()) return list
			val array = string.split("\n").dropLastWhile { it.isEmpty() }.toTypedArray()
			var i = 0
			while (i + 1 < array.size) {
				list.add(Pair(array[i], array[i + 1]))
				i += 2
			}
			return list
		}
	}
}
