package com.pilot51.voicenotify;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

public class MySimpleAdapter extends BaseAdapter implements Filterable {
	private int[] mTo;
	private String[] mFrom;
	private ViewBinder mViewBinder;
	private ArrayList<HashMap<String, String>> mData;
	private int mResource;
	private LayoutInflater mInflater;
	private SimpleFilter mFilter;
	private ArrayList<HashMap<String, String>> mUnfilteredData;

	public MySimpleAdapter(Context context, ArrayList<HashMap<String, String>> data, int resource, String[] from, int[] to) {
		mData = data;
		mResource = resource;
		mFrom = from;
		mTo = to;
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public int getCount() {
		return mData.size();
	}
	
	protected ArrayList<HashMap<String, String>> getData() {
		return mData;
	}

	public Object getItem(int position) {
		return mData.get(position);
	}

	public long getItemId(int position) {
		return position;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		View v;
		if (convertView == null)
			v = mInflater.inflate(mResource, parent, false);
		else v = convertView;
		bindView(position, v);
		return v;
	}

	private void bindView(int position, View view) {
		final HashMap<String, String> dataSet = mData.get(position);
		if (dataSet == null) return;
		final String[] from = mFrom;
		final int[] to = mTo;
		final int len = to.length;
		for (int i = 0; i < len; i++) {
			final View v = view.findViewById(to[i]);
			if (v != null) {
				final Object data = dataSet.get(from[i]);
				String text = data == null ? "" : data.toString();
				if (text == null)
					text = "";
				boolean bound = false;
				if (mViewBinder != null)
					bound = mViewBinder.setViewValue(v, data, text);
				if (!bound) {
					if (v instanceof CheckBox)
						((CheckBox)v).setChecked(Boolean.parseBoolean(text));
					else if (v instanceof TextView)
						((TextView)v).setText(text);
					else throw new IllegalStateException(v.getClass().getName() + " is not a view that can be bound by this SimpleAdapter");
				}
			}
		}
	}

	public Filter getFilter() {
		if (mFilter == null)
			mFilter = new SimpleFilter();
		return mFilter;
	}

	public static interface ViewBinder {
		boolean setViewValue(View view, Object data, String textRepresentation);
	}

	private class SimpleFilter extends Filter {
		@Override
		protected FilterResults performFiltering(CharSequence prefix) {
			FilterResults results = new FilterResults();
			if (mUnfilteredData == null)
				mUnfilteredData = new ArrayList<HashMap<String, String>>(mData);
			if (prefix == null || prefix.length() == 0) {
				ArrayList<HashMap<String, String>> list = mUnfilteredData;
				results.values = list;
				results.count = list.size();
			} else {
				String prefixString = prefix.toString().toLowerCase();
				ArrayList<HashMap<String, String>> unfilteredValues = mUnfilteredData;
				int count = unfilteredValues.size();
				ArrayList<HashMap<String, String>> newValues = new ArrayList<HashMap<String, String>>(count);
				for (int i = 0; i < count; i++) {
					HashMap<String, String> h = unfilteredValues.get(i);
					if (h != null) {
						int len = mTo.length;
						for (int j = 0; j < len; j++) {
							String str = (String) h.get(mFrom[j]);
							String[] words = str.split(" ");
							int wordCount = words.length;
							for (int k = 0; k < wordCount; k++) {
								String word = words[k];
								if (word.toLowerCase().startsWith(prefixString)) {
									newValues.add(h);
									break;
								}
							}
						}
					}
				}
				results.values = newValues;
				results.count = newValues.size();
			}
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			mData = (ArrayList<HashMap<String, String>>) results.values;
			if (results.count > 0)
				notifyDataSetChanged();
			else notifyDataSetInvalidated();
		}
	}
}
