package com.pilot51.voicenotify;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

public class AppListAdapter extends BaseAdapter implements Filterable {
	private ArrayList<AppList.App> data = new ArrayList<AppList.App>();
	private int mResource = R.layout.app_list_item;
	private LayoutInflater mInflater;
	private SimpleFilter mFilter;
	private ArrayList<AppList.App> mUnfilteredData;

	public AppListAdapter(Context context, ArrayList<AppList.App> list) {
		data.addAll(list);
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public int getCount() {
		return data.size();
	}
	
	protected ArrayList<AppList.App> getData() {
		return data;
	}
	
	protected void setData(ArrayList<AppList.App> list) {
		data.clear();
		data.addAll(list);
		notifyDataSetChanged();
	}

	public Object getItem(int position) {
		return data.get(position);
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
		((TextView)view.findViewById(R.id.text1)).setText(data.get(position).getLabel());
		((TextView)view.findViewById(R.id.text2)).setText(data.get(position).getPackage());
		((CheckBox)view.findViewById(R.id.checkbox)).setChecked(data.get(position).getEnabled());
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
				mUnfilteredData = new ArrayList<AppList.App>(data);
			if (prefix == null || prefix.length() == 0) {
				ArrayList<AppList.App> list = mUnfilteredData;
				results.values = list;
				results.count = list.size();
			} else {
				String prefixString = prefix.toString().toLowerCase();
				ArrayList<AppList.App> unfilteredValues = mUnfilteredData;
				int count = unfilteredValues.size();
				ArrayList<AppList.App> newValues = new ArrayList<AppList.App>(count);
				for (int i = 0; i < count; i++) {
					AppList.App h = unfilteredValues.get(i);
					if (h != null) {
						String[] words = h.getLabel().split(" ");
						for (int k = 0; k < words.length; k++) {
							if (words[k].toLowerCase().startsWith(prefixString)) {
								newValues.add(h);
								break;
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
			data = (ArrayList<AppList.App>) results.values;
			if (results.count > 0)
				notifyDataSetChanged();
			else notifyDataSetInvalidated();
		}
	}
}
