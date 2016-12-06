/*
 * Copyright 2012 Mark Injerd
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class NotifyList extends ListView {
	private Resources res;
	private static ArrayList<NotifyItem> list = new ArrayList<NotifyItem>();
	private Adapter adapter;
	private static OnListChangeListener listener;
	private static final int HISTORY_LIMIT = 20;
	
	NotifyList(Context context) {
		super(context);
		res = getResources();
		setDivider(res.getDrawable(R.drawable.divider));
		adapter = new Adapter(context, list);
		setAdapter(adapter);
	}
	
	static void addNotification(App app, String message) {
		if (list.size() == HISTORY_LIMIT) {
			list.remove(list.size() - 1);
		}
		list.add(0, new NotifyItem(app, message));
		if (listener != null) {
			listener.onListChange();
		}
	}
	
	static void setLastIgnore(String ignoreReasons, boolean isNew) {
		if (list.isEmpty()) return;
		list.get(0).setIgnoreReasons(ignoreReasons, isNew);
		if (listener != null) {
			listener.onListChange();
		}
	}
	
	private static class NotifyItem {
		private App app;
		private String message, ignoreReasons, time;
		private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		private boolean silenced;
		
		private NotifyItem(App app, String message) {
			this.app = app;
			this.message = message;
			time = sdf.format(Calendar.getInstance().getTime());
		}
		
		private App getApp() {
			return app;
		}
		
		private String getMessage() {
			return message;
		}
		
		private String getIgnoreReasons() {
			return ignoreReasons;
		}
		
		void setIgnoreReasons(String reasons, boolean isNew) {
			silenced = !isNew;
			ignoreReasons = reasons;
		}
		
		private String getTime() {
			return time;
		}
	}
	
	private static interface OnListChangeListener {
		void onListChange();
	}
	
	private class Adapter extends BaseAdapter {
		private ArrayList<NotifyItem> data;
		private LayoutInflater mInflater;
		
		private Adapter(final Context context, ArrayList<NotifyItem> list) {
			data = list;
			mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			listener = new OnListChangeListener() {
				@Override
				public void onListChange() {
					((Activity)context).runOnUiThread(new Runnable() {
						public void run() {
							notifyDataSetChanged();
						}
					});
				}
			};
		}
		
		@Override
		public int getCount() {
			return data.size();
		}
		
		@Override
		public Object getItem(int position) {
			return data.get(position);
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				view = mInflater.inflate(R.layout.notify_log_item, parent, false);
			}
			final NotifyItem item = data.get(position);
			((TextView)view.findViewById(R.id.time)).setText(item.getTime());
			((TextView)view.findViewById(R.id.title)).setText(item.getApp().getLabel());
			TextView textView = (TextView)view.findViewById(R.id.message);
			if (item.getMessage().length() != 0) {
				textView.setText(item.getMessage());
				textView.setVisibility(TextView.VISIBLE);
			} else textView.setVisibility(TextView.GONE);
			textView = (TextView)view.findViewById(R.id.ignore_reasons);
			if (item.getIgnoreReasons() != null && item.getIgnoreReasons().length() != 0) {
				textView.setText(item.getIgnoreReasons());
				if (item.silenced) textView.setTextColor(Color.YELLOW);
				else textView.setTextColor(Color.RED);
				textView.setVisibility(TextView.VISIBLE);
			} else textView.setVisibility(TextView.GONE);
			view.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					new AlertDialog.Builder(getContext())
					.setTitle(res.getString(item.getApp().getEnabled()
					                        ? R.string.ignore_app
					                        : R.string.unignore_app,
					                        item.getApp().getLabel()))
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							item.getApp().setEnabled(!item.getApp().getEnabled(), true);
							Toast.makeText(getContext(), res.getString(item.getApp().getEnabled()
							                                           ? R.string.app_is_not_ignored
							                                           : R.string.app_is_ignored,
							                                           item.getApp().getLabel()),
							               Toast.LENGTH_SHORT).show();
						}
					})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
					return false;
				}
			});
			return view;
		}
	}
}
