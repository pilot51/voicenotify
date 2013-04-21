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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.ListActivity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AppList extends ListActivity {
	private ListView lv;
	private Adapter adapter;
	private static ArrayList<App> apps;
	private static boolean defEnable;
	private static final String KEY_DEFAULT_ENABLE = "defEnable";
	private static final int IGNORE_TOGGLE = 0, IGNORE_ALL = 1, IGNORE_NONE = 2;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		defEnable = Common.prefs.getBoolean(KEY_DEFAULT_ENABLE, true);
		new Thread(new Runnable() {
			public void run() {
				apps = Database.getApps();
				runOnUiThread(new Runnable() {
					public void run() {
						lv = getListView();
						lv.setTextFilterEnabled(true);
						adapter = new Adapter(AppList.this, apps);
						lv.setAdapter(adapter);
						lv.setOnItemClickListener(new OnItemClickListener() {
							@Override
							public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
								setIgnore(position, IGNORE_TOGGLE);
								adapter.notifyDataSetChanged();
							}
						});
						setProgressBarIndeterminateVisibility(true);
					}
				});
				PackageManager packMan = getPackageManager();
				
				// Remove uninstalled
				for (int a = apps.size() - 1; a >= 0; a--) {
					App app = apps.get(a);
					try {
						packMan.getApplicationInfo(app.getPackage(), 0);
					} catch (NameNotFoundException e) {
						app.remove();
						apps.remove(a);
						runOnUiThread(new Runnable() {
							public void run() {
								adapter.setData(apps);
							}
						});
					}
				}
				
				// Add new
				inst:for (ApplicationInfo appInfo : packMan.getInstalledApplications(0)) {
					for (App app : apps) {
						if (app.getPackage().equals(appInfo.packageName)) {
							continue inst;
						}
					}
					apps.add(new App(appInfo.packageName, String.valueOf(appInfo.loadLabel(packMan)), defEnable).updateDb());
					runOnUiThread(new Runnable() {
						public void run() {
							adapter.setData(apps);
						}
					});
				}
				
				Collections.sort(apps, new Comparator<App>() {
					@Override
					public int compare(App object1, App object2) {
						return object1.label.compareToIgnoreCase(object2.label);
					}
				});
				runOnUiThread(new Runnable() {
					public void run() {
						adapter.setData(apps);
						setProgressBarIndeterminateVisibility(false);
					}
				});
			}
		}).start();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, 1, 0, R.string.ignore_all);
		menu.add(0, 2, 0, R.string.ignore_none);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 1:
			setDefaultEnable(false);
			massIgnore(IGNORE_ALL);
			return true;
		case 2:
			setDefaultEnable(true);
			massIgnore(IGNORE_NONE);
			return true;
		}
		return false;
	}
	
	private void massIgnore(int ignoreType) {
		for (App app : apps) {
			setIgnore(app, ignoreType);
		}
		adapter.notifyDataSetChanged();
		new Thread(new Runnable() {
			public void run() {
				Database.setApps(apps);
			}
		}).start();
	}
	
	private void setIgnore(int position, int ignoreType) {
		setIgnore(apps.get(position), ignoreType);
	}
	
	private void setIgnore(App app, int ignoreType) {
		if (!app.getEnabled() & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_NONE)) {
			app.setEnabled(true, ignoreType == IGNORE_TOGGLE);
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(this, app.getLabel() + " " + getString(R.string.is_not_ignored), Toast.LENGTH_SHORT).show();
			}
		} else if (app.getEnabled() & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_ALL)) {
			app.setEnabled(false, ignoreType == IGNORE_TOGGLE);
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(this, app.getLabel() + " " + getString(R.string.is_ignored), Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	/** Set the default enabled value for new apps. */
	private void setDefaultEnable(boolean enable) {
		defEnable = enable;
		Common.prefs.edit().putBoolean(KEY_DEFAULT_ENABLE, defEnable).commit();
	}
	
	protected static boolean getIsEnabled(String pkg) {
		if (apps == null) {
			defEnable = Common.prefs.getBoolean(KEY_DEFAULT_ENABLE, true);
			apps = Database.getApps();
		}
		for (App app : apps) {
			if (app.getPackage().equals(pkg)) {
				return app.enabled;
			}
		}
		return defEnable;
	}
	
	protected static class App {
		private String packageName, label;
		private boolean enabled;
		
		protected App(String pkg, String name, boolean enable) {
			packageName = pkg;
			label = name;
			enabled = enable;
		}
		
		/**
		 * Updates self in database.
		 * @return This instance.
		 */
		private App updateDb() {
			Database.updateApp(this);
			return this;
		}
		
		private void setEnabled(boolean enable, boolean updateDb) {
			enabled = enable;
			if (updateDb) Database.updateAppEnable(this);
		}
		
		/** Removes self from database. */
		private void remove() {
			Database.removeApp(this);
		}
		
		protected String getLabel() {
			return label;
		}
		
		protected String getPackage() {
			return packageName;
		}
		
		protected boolean getEnabled() {
			return enabled;
		}
	}
	
	private class Adapter extends BaseAdapter implements Filterable {
		private ArrayList<App> data = new ArrayList<App>();
		private LayoutInflater mInflater;
		private SimpleFilter mFilter;
		private ArrayList<App> mUnfilteredData;
		
		private Adapter(Context context, ArrayList<App> list) {
			data.addAll(list);
			mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		private void setData(ArrayList<App> list) {
			data.clear();
			data.addAll(list);
			notifyDataSetChanged();
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
			View view;
			if (convertView == null) {
				view = mInflater.inflate(R.layout.app_list_item, parent, false);
			} else view = convertView;
			((TextView)view.findViewById(R.id.text1)).setText(data.get(position).getLabel());
			((TextView)view.findViewById(R.id.text2)).setText(data.get(position).getPackage());
			((CheckBox)view.findViewById(R.id.checkbox)).setChecked(data.get(position).getEnabled());
			return view;
		}
		
		@Override
		public Filter getFilter() {
			if (mFilter == null) mFilter = new SimpleFilter();
			return mFilter;
		}
		
		private class SimpleFilter extends Filter {
			@Override
			protected FilterResults performFiltering(CharSequence prefix) {
				FilterResults results = new FilterResults();
				if (mUnfilteredData == null) {
					mUnfilteredData = new ArrayList<App>(data);
				}
				if (prefix == null || prefix.length() == 0) {
					ArrayList<App> list = mUnfilteredData;
					results.values = list;
					results.count = list.size();
				} else {
					String prefixString = prefix.toString().toLowerCase();
					ArrayList<App> newValues = new ArrayList<App>(mUnfilteredData.size());
					for (App app : mUnfilteredData) {
						if (app.getLabel().toLowerCase().contains(prefixString)
								|| app.getPackage().toLowerCase().contains(prefixString)) {
							newValues.add(app);
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
				data = (ArrayList<App>)results.values;
				if (results.count > 0) notifyDataSetChanged();
				else notifyDataSetInvalidated();
			}
		}
	}
}
