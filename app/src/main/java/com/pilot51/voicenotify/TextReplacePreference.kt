/*
 * Copyright 2017 Mark Injerd
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
import android.database.DataSetObserver
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.View.OnAttachStateChangeListener
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.TextView.OnEditorActionListener
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.pilot51.voicenotify.databinding.PreferenceDialogTextReplaceBinding
import com.pilot51.voicenotify.databinding.TextReplaceRowBinding
import java.util.*

/**
 * Preference that provides a dynamic list with two EditTexts in each row for defining text replacement.
 */
class TextReplacePreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {
	private var isPersistedReplaceListSet = false
	val persistedReplaceList: MutableList<Pair<String, String>?> = ArrayList()

	init {
		dialogLayoutResource = R.layout.preference_dialog_text_replace
		setPositiveButtonText(android.R.string.ok)
		setNegativeButtonText(android.R.string.cancel)
	}

	@Deprecated("Deprecated in Java")
	override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
		setPersistedReplaceList(
			if (restorePersistedValue) {
				getPersistedString(convertListToString(persistedReplaceList))
			} else defaultValue as String
		)
	}

	fun setPersistedReplaceList(list: List<Pair<String, String>?>) {
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
		var changed = false
		if (trimmedList.size != persistedReplaceList.size) {
			changed = true
		} else {
			for (i in trimmedList.indices) {
				if (trimmedList[i] != persistedReplaceList[i]) {
					changed = true
					break
				}
			}
		}
		if (changed || !isPersistedReplaceListSet) {
			persistedReplaceList.clear()
			persistedReplaceList.addAll(trimmedList)
			isPersistedReplaceListSet = true
			persistString(convertListToString(persistedReplaceList))
			if (changed) {
				notifyDependencyChange(shouldDisableDependents())
				notifyChanged()
			}
		}
	}

	private fun setPersistedReplaceList(text: String) {
		persistedReplaceList.clear()
		persistedReplaceList.addAll(convertStringToList(text))
	}

	class TextReplaceFragment : PreferenceDialogFragmentCompat() {
		private lateinit var textReplacePreference: TextReplacePreference
		private lateinit var listView: ListView
		private lateinit var adapter: TextReplaceAdapter

		override fun onCreate(savedInstanceState: Bundle?) {
			super.onCreate(savedInstanceState)
			textReplacePreference = preference as TextReplacePreference
			listView = ListView(context)
			adapter = TextReplaceAdapter()
			listView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
				override fun onViewAttachedToWindow(v: View) {
					val window = dialog!!.window
					// Required for soft keyboard to appear.
					window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
					) ?: Log.e(TextReplaceFragment::class.simpleName,
						"Null window, unable to clear window flags, soft keyboard may not appear.")
				}

				override fun onViewDetachedFromWindow(v: View) {}
			})
		}

		override fun onSaveInstanceState(outState: Bundle) {
			if (dialog?.isShowing != true) return super.onSaveInstanceState(outState)
			adapter.updateFocusedRow()
			outState.putString(KEY_LIST_STATE, convertListToString(adapter.list))
		}

		override fun onViewStateRestored(savedInstanceState: Bundle?) {
			super.onViewStateRestored(savedInstanceState)
			savedInstanceState?.getString(KEY_LIST_STATE)?.let {
				adapter.list = convertStringToList(it)
			}
		}

		override fun onBindDialogView(view: View) {
			super.onBindDialogView(view)
			val binding = PreferenceDialogTextReplaceBinding.bind(view)
			listView.adapter = adapter
			val oldParent = listView.parent
			if (oldParent !== view) {
				(oldParent as? ViewGroup)?.removeView(listView)
				binding.container.addView(listView, ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT)
			}
		}

		/**
		 * Adapter for the list of text replacements.
		 */
		// FIXME: Adapter update forces focus to editFrom of the row with focus prior to update.
		private inner class TextReplaceAdapter : BaseAdapter() {
			private val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
			private val replaceList: MutableList<Pair<String, String>?> = ArrayList()

			/**
			 * Sets the adapter data and adds null for an empty last row
			 * if the list doesn't already contain null (it shouldn't).
			 */
			var list: List<Pair<String, String>?>
				get() = replaceList
				set(list) {
					replaceList.clear()
					replaceList.addAll(list)
					if (!replaceList.contains(null)) {
						replaceList.add(null)
					}
					notifyDataSetChanged()
				}

			init {
				list = textReplacePreference.persistedReplaceList
			}

			/**
			 * Finds the focused row and forces the entered text to be updated in the adapter's data.
			 * This is used when the dialog's positive button is pressed or when saving instance state.
			 */
			fun updateFocusedRow() {
				listView.focusedChild?.run {
					updateListItem(tag as ViewHolder)
				}
			}

			/**
			 * Updates a row in the adapter's data.
			 * @param holder The ViewHolder for the row. The EditTexts and position in data are used.
			 */
			private fun updateListItem(holder: ViewHolder) {
				var listPair: Pair<String, String>? = null
				if (holder.position < replaceList.size) {
					listPair = replaceList[holder.position]
				}
				val editStringPair = Pair.create(holder.editFrom.text.toString(),
					holder.editTo.text.toString())
				if (editStringPair.first.isNotEmpty()) {
					if (listPair == null) {
						replaceList.add(holder.position, editStringPair)
						notifyDataSetChanged()
					} else if (editStringPair.first != listPair.first
						|| editStringPair.second != listPair.second) {
						replaceList[holder.position] = editStringPair
					}
				} else if (listPair != null) {
					replaceList.removeAt(holder.position)
					notifyDataSetChanged()
				}
			}

			/**
			 * Sets EditText error message if entered text is a duplicate of another entry in [.replaceList],
			 * otherwise clears error if not a duplicate.
			 * @param editFrom The EditText to check and set error message if text is duplicate, otherwise clear error.
			 * @param position The position of this entry in [.replaceList] to prevent detecting self as duplicate.
			 */
			private fun setErrorIfDuplicate(editFrom: EditText, position: Int) {
				val editFromString = editFrom.text.toString()
				for (p in replaceList) {
					if (p != null && replaceList.indexOf(p) < position && editFromString.equals(p.first, ignoreCase = true)) {
						editFrom.error = requireContext().getString(R.string.text_replace_error_duplicate)
						return
					}
				}
				editFrom.error = null
			}

			override fun getCount() = replaceList.size

			override fun getItem(position: Int) = replaceList[position]

			override fun getItemId(position: Int) = position.toLong()

			override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
				lateinit var holder: ViewHolder
				val binding = convertView?.let {
					TextReplaceRowBinding.bind(it).apply {
						holder = root.tag as ViewHolder
					}
				} ?: TextReplaceRowBinding.inflate(inflater, parent, false).apply {
					holder = ViewHolder()
					holder.editFrom = textToReplace
					holder.editTo = replacementText
					holder.remove = remove
					root.tag = holder
				}
				holder.position = position
				val pair = replaceList[position]
				val editFrom = holder.editFrom
				val editTo = holder.editTo
				holder.remove.setOnClickListener {
					editFrom.text = null
					editTo.text = null
					updateListItem(holder)
				}
				editFrom.setText(pair?.first)
				editTo.setText(pair?.second)
				setErrorIfDuplicate(editFrom, position)
				val observer = DataChangedObserver()
				registerDataSetObserver(observer)
				val listener = OnFocusChangeListener { _, hasFocus ->
					if (hasFocus || observer.dataChanged) return@OnFocusChangeListener
					setErrorIfDuplicate(editFrom, position)
					updateListItem(holder)
				}
				editFrom.onFocusChangeListener = listener
				editTo.onFocusChangeListener = listener
				editTo.setOnEditorActionListener(OnEditorActionListener { _, actionId, _ ->
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						if (position != replaceList.size - 1) {
							val nextField = editFrom.focusSearch(View.FOCUS_DOWN) as TextView
							nextField.requestFocus()
						}
						return@OnEditorActionListener true
					}
					false
				})
				return binding.root
			}

			private inner class ViewHolder {
				var position = 0
				lateinit var editFrom: EditText
				lateinit var editTo: EditText
				lateinit var remove: ImageButton
			}

			/**
			 * A DataSetObserver used to prevent onFocusChange of the EditTexts from erroneously updating
			 * data with old row information after data has changed from onClick of the remove button.
			 */
			private inner class DataChangedObserver : DataSetObserver() {
				var dataChanged = false
				override fun onChanged() {
					dataChanged = true
					unregisterDataSetObserver(this)
				}
			}
		}

		override fun onDialogClosed(positiveResult: Boolean) {
			if (positiveResult) {
				adapter.updateFocusedRow()
				if (preference.callChangeListener(convertListToString(adapter.list))) {
					textReplacePreference.setPersistedReplaceList(adapter.list)
				}
			}
		}

		companion object {
			private const val KEY_LIST_STATE = "key_list_state"

			fun newInstance(key: String): TextReplaceFragment {
				return TextReplaceFragment().apply {
					arguments = Bundle(1).apply {
						putString(ARG_KEY, key)
					}
				}
			}
		}
	}

	companion object {
		fun convertListToString(list: List<Pair<String, String>?>): String {
			val saveString = StringBuilder()
			for (pair in list) {
				if (pair == null) {
					break
				}
				if (saveString.isNotEmpty()) {
					saveString.append("\n")
				}
				saveString.append(pair.first)
				saveString.append("\n")
				saveString.append(pair.second)
			}
			return saveString.toString()
		}

		/**
		 * Converts a string of paired substrings separated by newlines into a list of string pairs.
		 * @param string The string to convert. Each string in and between pairs must be separated by a newline.
		 * There should be an odd number of newlines for an even number of substrings (including zero-length),
		 * otherwise the last substring will be discarded.
		 * @return A List of string pairs.
		 */
		fun convertStringToList(string: String?): List<Pair<String, String>> {
			val list: MutableList<Pair<String, String>> = ArrayList()
			if (TextUtils.isEmpty(string)) {
				return list
			}
			val array = string!!.split("\n").dropLastWhile { it.isEmpty() }.toTypedArray()
			var i = 0
			while (i + 1 < array.size) {
				list.add(Pair(array[i], array[i + 1]))
				i += 2
			}
			return list
		}
	}
}
