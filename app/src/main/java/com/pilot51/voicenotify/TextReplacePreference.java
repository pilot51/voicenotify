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

package com.pilot51.voicenotify;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference that provides a dynamic list with two EditTexts in each row for defining text replacement.
 */
public class TextReplacePreference extends DialogPreference {
	private ListView listView;
	private TextReplaceAdapter adapter;
	private boolean isPersistedReplaceListSet;
	private final List<Pair<String, String>> persistedReplaceList = new ArrayList<>();
	
	public TextReplacePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setDialogLayoutResource(R.layout.preference_dialog_text_replace);
		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);
		listView = new ListView(context, attrs);
		listView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			@Override
			public void onViewAttachedToWindow(View v) {
				Window window = getDialog().getWindow();
				if (window != null) {
					// Required for soft keyboard to appear.
					window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
							| WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
				} else {
					Log.e(TextReplacePreference.class.getSimpleName(),
							"Null window, unable to clear window flags, soft keyboard may not appear.");
				}
			}
			
			@Override
			public void onViewDetachedFromWindow(View v) {}
		});
	}
	
	private void setPersistedReplaceList(List<Pair<String, String>> list) {
		List<Pair<String, String>> trimmedList = new ArrayList<>(list.size());
		copyLoop:for (Pair<String, String> pair : list) {
			if (pair != null && !pair.first.isEmpty()) {
				for (Pair<String, String> p : trimmedList) {
					if (pair.first.equalsIgnoreCase(p.first)) {
						continue copyLoop;
					}
				}
				trimmedList.add(pair);
			}
		}
		boolean changed = false;
		if (trimmedList.size() != persistedReplaceList.size()) {
			changed = true;
		} else {
			for (int i = 0; i < trimmedList.size(); i++) {
				if (!trimmedList.get(i).equals(persistedReplaceList.get(i))) {
					changed = true;
					break;
				}
			}
		}
		if (changed || !isPersistedReplaceListSet) {
			persistedReplaceList.clear();
			persistedReplaceList.addAll(trimmedList);
			isPersistedReplaceListSet = true;
			persistString(convertListToString(persistedReplaceList));
			if (changed) {
				notifyDependencyChange(shouldDisableDependents());
				notifyChanged();
			}
		}
	}
	
	private void setPersistedReplaceList(String text) {
		persistedReplaceList.clear();
		persistedReplaceList.addAll(convertStringToList(text));
	}
	
	private static String convertListToString(List<Pair<String, String>> list) {
		StringBuilder saveString = new StringBuilder();
		for (Pair<String, String> pair : list) {
			if (pair == null) {
				break;
			}
			if (saveString.length() > 0) {
				saveString.append("\n");
			}
			saveString.append(pair.first);
			saveString.append("\n");
			saveString.append(pair.second);
		}
		return saveString.toString();
	}
	
	/**
	 * Converts a string of paired substrings separated by newlines into a list of string pairs.
	 * @param string The string to convert. Each string in and between pairs must be separated by a newline.
	 *               There should be an odd number of newlines for an even number of substrings (including zero-length),
	 *               otherwise the last substring will be discarded.
	 * @return A List of string pairs.
	 */
	static List<Pair<String, String>> convertStringToList(String string) {
		List<Pair<String, String>> list = new ArrayList<>();
		if (TextUtils.isEmpty(string)) {
			return list;
		}
		String[] array = string.split("\n", -1);
		for (int i = 0; i+1 < array.length; i+=2) {
			list.add(new Pair<>(array[i], array[i+1]));
		}
		return list;
	}
	
	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		adapter = new TextReplaceAdapter();
		listView.setAdapter(adapter);
		ViewParent oldParent = listView.getParent();
		if (oldParent != view) {
			if (oldParent != null) {
				((ViewGroup) oldParent).removeView(listView);
			}
			ViewGroup container = view.findViewById(R.id.container);
			if (container != null) {
				container.addView(listView, ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT);
			}
		}
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			adapter.updateFocusedRow();
			if (callChangeListener(convertListToString(adapter.getList()))) {
				setPersistedReplaceList(adapter.getList());
			}
		}
	}
	
	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		setPersistedReplaceList(restorePersistedValue ? getPersistedString(convertListToString(persistedReplaceList))
				: (String)defaultValue);
	}
	
	@Override
	public boolean shouldDisableDependents() {
		return persistedReplaceList.isEmpty() || super.shouldDisableDependents();
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		SavedState myState = new SavedState(super.onSaveInstanceState());
		adapter.updateFocusedRow();
		myState.text = convertListToString(adapter.getList());
		return myState;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			super.onRestoreInstanceState(state);
			return;
		}
		SavedState myState = (SavedState)state;
		super.onRestoreInstanceState(myState.getSuperState());
		adapter.setList(convertStringToList(myState.text));
	}
	
	private static class SavedState extends BaseSavedState {
		String text;
		
		private SavedState(Parcelable superState) {
			super(superState);
		}
		
		private SavedState(Parcel source) {
			super(source);
			text = source.readString();
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeString(text);
		}
		
		public static final Parcelable.Creator<SavedState> CREATOR =
				new Parcelable.Creator<SavedState>() {
					public SavedState createFromParcel(Parcel in) {
						return new SavedState(in);
					}
					
					public SavedState[] newArray(int size) {
						return new SavedState[size];
					}
				};
	}
	
	/**
	 * Adapter for the list of text replacements.
	 */
	// FIXME: Adapter update forces focus to editFrom of the row with focus prior to update.
	private class TextReplaceAdapter extends BaseAdapter {
		private final LayoutInflater inflater;
		private final List<Pair<String, String>> replaceList = new ArrayList<>();
		
		private TextReplaceAdapter() {
			inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			setList(persistedReplaceList);
		}
		
		private List<Pair<String, String>> getList() {
			return replaceList;
		}
		
		/**
		 * Sets the adapter data and adds null for an empty last row
		 * if the list doesn't already contain null (it shouldn't).
		 * @param list The data to set in the adapter.
		 */
		private void setList(List<Pair<String, String>> list) {
			replaceList.clear();
			replaceList.addAll(list);
			if (!replaceList.contains(null)) {
				replaceList.add(null);
			}
			notifyDataSetChanged();
		}
		
		/**
		 * Finds the focused row and forces the entered text to be updated in the adapter's data.
		 * This is used when the dialog's positive button is pressed or when saving instance state.
		 */
		private void updateFocusedRow() {
			View focusedRow = listView.getFocusedChild();
			if (focusedRow != null) {
				updateListItem((ViewHolder)focusedRow.getTag());
			}
		}
		
		/**
		 * Updates a row in the adapter's data.
		 * @param holder The ViewHolder for the row. The EditTexts and position in data are used.
		 */
		private void updateListItem(ViewHolder holder) {
			Pair<String, String> listPair = null;
			if (holder.position < replaceList.size()) {
				listPair = replaceList.get(holder.position);
			}
			Pair<String, String> editStringPair = Pair.create(holder.editFrom.getText().toString(),
					holder.editTo.getText().toString());
			if (!editStringPair.first.isEmpty()) {
				if (listPair == null) {
					replaceList.add(holder.position, editStringPair);
					notifyDataSetChanged();
				} else if (!editStringPair.first.equals(listPair.first)
						|| !editStringPair.second.equals(listPair.second)) {
					replaceList.set(holder.position, editStringPair);
				}
			} else if (listPair != null) {
				replaceList.remove(holder.position);
				notifyDataSetChanged();
			}
		}
		
		/**
		 * Sets EditText error message if entered text is a duplicate of another entry in {@link #replaceList},
		 * otherwise clears error if not a duplicate.
		 * @param editFrom The EditText to check and set error message if text is duplicate, otherwise clear error.
		 * @param position The position of this entry in {@link #replaceList} to prevent detecting self as duplicate.
		 */
		private void setErrorIfDuplicate(EditText editFrom, int position) {
			String editFromString = editFrom.getText().toString();
			for (Pair<String, String> p : replaceList) {
				if (p != null && replaceList.indexOf(p) < position && editFromString.equalsIgnoreCase(p.first)) {
					editFrom.setError(getContext().getString(R.string.text_replace_error_duplicate));
					return;
				}
			}
			editFrom.setError(null);
		}
		
		@Override
		public int getCount() {
			return replaceList.size();
		}
		
		@Override
		public Object getItem(int position) {
			return replaceList.get(position);
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		private class ViewHolder {
			private int position;
			private EditText editFrom;
			private EditText editTo;
			private ImageButton remove;
		}
		
		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			final ViewHolder holder;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.text_replace_row, parent, false);
				holder = new ViewHolder();
				holder.editFrom = convertView.findViewById(R.id.text_to_replace);
				holder.editTo = convertView.findViewById(R.id.replacement_text);
				holder.remove = convertView.findViewById(R.id.remove);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder)convertView.getTag();
			}
			holder.position = position;
			final Pair<String, String> pair = replaceList.get(position);
			final EditText editFrom = holder.editFrom;
			final EditText editTo = holder.editTo;
			holder.remove.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					editFrom.setText(null);
					editTo.setText(null);
					updateListItem(holder);
				}
			});
			if (pair != null) {
				editFrom.setText(pair.first);
				editTo.setText(pair.second);
			} else {
				editFrom.setText(null);
				editTo.setText(null);
			}
			setErrorIfDuplicate(editFrom, position);
			final DataChangedObserver observer = new DataChangedObserver();
			registerDataSetObserver(observer);
			View.OnFocusChangeListener listener = new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (hasFocus || observer.dataChanged) return;
					setErrorIfDuplicate(editFrom, position);
					updateListItem(holder);
				}
			};
			editFrom.setOnFocusChangeListener(listener);
			editTo.setOnFocusChangeListener(listener);
			editTo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						if (position != replaceList.size() - 1) {
							TextView nextField = (TextView)editFrom.focusSearch(View.FOCUS_DOWN);
							if (nextField != null) {
								nextField.requestFocus();
							}
						}
						return true;
					}
					return false;
				}
			});
			return convertView;
		}
		
		/**
		 * A DataSetObserver used to prevent onFocusChange of the EditTexts from erroneously updating
		 * data with old row information after data has changed from onClick of the remove button.
		 */
		private class DataChangedObserver extends DataSetObserver {
			private boolean dataChanged;
			@Override
			public void onChanged() {
				dataChanged = true;
				unregisterDataSetObserver(this);
			}
		}
	}
}
