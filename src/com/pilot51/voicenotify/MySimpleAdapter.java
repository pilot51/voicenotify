package com.pilot51.voicenotify;

import android.content.Context;
import android.database.DataSetObservable;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MySimpleAdapter extends BaseAdapter implements Filterable {
	private int[] mTo;
	private String[] mFrom;
	private ViewBinder mViewBinder;

	private List<? extends Map<String, ?>> mData;

	private int mResource;
	private int mDropDownResource;
	private LayoutInflater mInflater;

	private SimpleFilter mFilter;
	private ArrayList<Map<String, ?>> mUnfilteredData;
	private final DataSetObservable mDataSetObservable = new DataSetObservable();

	public MySimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
		mData = data;
		mResource = mDropDownResource = resource;
		mFrom = from;
		mTo = to;
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	public void notifyDataSetChanged() {
		mDataSetObservable.notifyChanged();
	}

	public int getCount() {
		return mData.size();
	}

	public Object getItem(int position) {
		return mData.get(position);
	}

	public long getItemId(int position) {
		return position;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		return createViewFromResource(position, convertView, parent, mResource);
	}

	private View createViewFromResource(int position, View convertView, ViewGroup parent, int resource) {
		View v;
		if (convertView == null) {
			v = mInflater.inflate(resource, parent, false);
		} else {
			v = convertView;
		}
		bindView(position, v);
		return v;
	}

	public void setDropDownViewResource(int resource) {
		this.mDropDownResource = resource;
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return createViewFromResource(position, convertView, parent, mDropDownResource);
	}

	private void bindView(int position, View view) {
		final Map<String, ?> dataSet = mData.get(position);
		if (dataSet == null) {
			return;
		}

		final String[] from = mFrom;
		final int[] to = mTo;
		final int len = to.length;

		for (int i = 0; i < len; i++) {
			final View v = view.findViewById(to[i]);
			if (v != null) {
				final Object data = dataSet.get(from[i]);
				String text = data == null ? "" : data.toString();
				if (text == null) {
					text = "";
				}

				boolean bound = false;
				if (mViewBinder != null) {
					bound = mViewBinder.setViewValue(v, data, text);
				}

				if (!bound) {
					if (v instanceof CheckBox) {
						setViewCheckbox((CheckBox) v, text);
					} else if (v instanceof TextView) {
						setViewText((TextView) v, text);
					} else if (v instanceof ImageView) {
						if (data instanceof Integer) {
							setViewImage((ImageView) v, (Integer) data);
						} else {
							setViewImage((ImageView) v, text);
						}
					} else {
						throw new IllegalStateException(v.getClass().getName() + " is not a " + " view that can be bounds by this SimpleAdapter");
					}
				}
			}
		}
	}

	public ViewBinder getViewBinder() {
		return mViewBinder;
	}

	public void setViewBinder(ViewBinder viewBinder) {
		mViewBinder = viewBinder;
	}

	public void setViewImage(ImageView v, int value) {
		v.setImageResource(value);
	}

	public void setViewImage(ImageView v, String value) {
		try {
			v.setImageResource(Integer.parseInt(value));
		} catch (NumberFormatException nfe) {
			v.setImageURI(Uri.parse(value));
		}
	}

	public void setViewText(TextView v, String text) {
		v.setText(text);
	}

	public void setViewCheckbox(CheckBox v, String text) {
		v.setChecked(Boolean.parseBoolean(text));
	}

	public Filter getFilter() {
		if (mFilter == null) {
			mFilter = new SimpleFilter();
		}
		return mFilter;
	}

	public static interface ViewBinder {
		boolean setViewValue(View view, Object data, String textRepresentation);
	}

	private class SimpleFilter extends Filter {

		@Override
		protected FilterResults performFiltering(CharSequence prefix) {
			FilterResults results = new FilterResults();

			if (mUnfilteredData == null) {
				mUnfilteredData = new ArrayList<Map<String, ?>>(mData);
			}

			if (prefix == null || prefix.length() == 0) {
				ArrayList<Map<String, ?>> list = mUnfilteredData;
				results.values = list;
				results.count = list.size();
			} else {
				String prefixString = prefix.toString().toLowerCase();

				ArrayList<Map<String, ?>> unfilteredValues = mUnfilteredData;
				int count = unfilteredValues.size();

				ArrayList<Map<String, ?>> newValues = new ArrayList<Map<String, ?>>(count);

				for (int i = 0; i < count; i++) {
					Map<String, ?> h = unfilteredValues.get(i);
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
			//noinspection unchecked
			mData = (List<Map<String, ?>>) results.values;
			if (results.count > 0) {
				notifyDataSetChanged();
			} else {
				notifyDataSetInvalidated();
			}
		}
	}
}
