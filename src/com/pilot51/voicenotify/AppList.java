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
import android.view.inputmethod.InputMethodManager;
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
	private static final Object SYNC_APPS = new Object();
	private static OnListUpdateListener listener;
	private static boolean isUpdating;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Common.init(this);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		lv = getListView();
		lv.setTextFilterEnabled(true);
		lv.setFastScrollEnabled(true);
		adapter = new Adapter();
		listener = new OnListUpdateListener() {
			@Override
			public void onListUpdated() {
				runOnUiThread(new Runnable() {
					public void run() {
						adapter.setData(apps);
					}
				});
			}
			@Override
			public void onUpdateCompleted() {
				runOnUiThread(new Runnable() {
					public void run() {
						setProgressBarIndeterminateVisibility(false);
					}
				});
				listener = null;
			}
		};
		lv.setAdapter(adapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				setIgnore((App)adapter.getItem(position), IGNORE_TOGGLE);
				adapter.notifyDataSetChanged();
			}
		});
		defEnable = Common.getPrefs(this).getBoolean(KEY_DEFAULT_ENABLE, true);
		updateAppsList();
	}
	
	private interface OnListUpdateListener {
		void onListUpdated();
		void onUpdateCompleted();
	}
	private static void onListUpdated() {
		if (listener != null) listener.onListUpdated();
	}
	
	private void updateAppsList() {
		setProgressBarIndeterminateVisibility(true);
		if (isUpdating) {
			adapter.setData(apps);
			return;
		}
		isUpdating = true;
		new Thread(new Runnable() {
			public void run() {
				synchronized (SYNC_APPS) {
					apps = Database.getApps();
					onListUpdated();
					final boolean isFirstLoad = apps.isEmpty();
					PackageManager packMan = getPackageManager();
					
					// Remove uninstalled
					for (int a = apps.size() - 1; a >= 0; a--) {
						App app = apps.get(a);
						try {
							packMan.getApplicationInfo(app.getPackage(), 0);
						} catch (NameNotFoundException e) {
							if (!isFirstLoad) app.remove();
							apps.remove(a);
							onListUpdated();
						}
					}
					
					// Add new
					inst:for (ApplicationInfo appInfo : packMan.getInstalledApplications(0)) {
						for (App app : apps) {
							if (app.getPackage().equals(appInfo.packageName)) {
								continue inst;
							}
						}
						App app = new App(appInfo.packageName, String.valueOf(appInfo.loadLabel(packMan)), defEnable);
						apps.add(app);
						onListUpdated();
						if (!isFirstLoad) app.updateDb();
					}
					
					Collections.sort(apps, new Comparator<App>() {
						@Override
						public int compare(App app1, App app2) {
							return app1.getLabel().compareToIgnoreCase(app2.getLabel());
						}
					});
					onListUpdated();
					if (isFirstLoad) Database.setApps(apps);
				}
				isUpdating = false;
				if (listener != null) listener.onUpdateCompleted();
			}
		}).start();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.app_list, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.ignore_all:
			setDefaultEnable(false);
			massIgnore(IGNORE_ALL);
			return true;
		case R.id.ignore_none:
			setDefaultEnable(true);
			massIgnore(IGNORE_NONE);
			return true;
		case R.id.filter:
			((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).toggleSoftInput(0, 0);
			return true;
		}
		return false;
	}
	
	/**
	 * @param pkg Package name used to find {@link App} in current list or create a new one from system.
	 * @param ctx Context required to get default enabled preference and to get package manager for searching system.
	 * @return Found or created {@link App}, otherwise null if app not found on system.
	 */
	static App findOrAddApp(String pkg, Context ctx) {
		synchronized (SYNC_APPS) {
			if (apps == null) {
				defEnable = Common.getPrefs(ctx).getBoolean(KEY_DEFAULT_ENABLE, true);
				apps = Database.getApps();
			}
			for (App app : apps) {
				if (app.getPackage().equals(pkg)) {
					return app;
				}
			}
			try {
				PackageManager packMan = ctx.getPackageManager();
				App app = new App(pkg, packMan.getApplicationInfo(pkg, 0).loadLabel(packMan).toString(), defEnable);
				apps.add(app.updateDb());
				return app;
			} catch (NameNotFoundException e) {
				e.printStackTrace();
				return null;
			}
		}
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
	
	private void setIgnore(App app, int ignoreType) {
		if (!app.getEnabled() & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_NONE)) {
			app.setEnabled(true, ignoreType == IGNORE_TOGGLE);
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(this, getString(R.string.app_is_not_ignored, app.getLabel()), Toast.LENGTH_SHORT).show();
			}
		} else if (app.getEnabled() & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_ALL)) {
			app.setEnabled(false, ignoreType == IGNORE_TOGGLE);
			if (ignoreType == IGNORE_TOGGLE) {
				Toast.makeText(this, getString(R.string.app_is_ignored, app.getLabel()), Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	/** Set the default enabled value for new apps. */
	private void setDefaultEnable(boolean enable) {
		defEnable = enable;
		Common.getPrefs(this).edit().putBoolean(KEY_DEFAULT_ENABLE, defEnable).commit();
	}
	
	private class Adapter extends BaseAdapter implements Filterable {
		private final ArrayList<App> baseData = new ArrayList<App>();
		private final ArrayList<App> adapterData = new ArrayList<App>();
		private LayoutInflater mInflater;
		private SimpleFilter filter;
		
		private Adapter() {
			mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		private void setData(ArrayList<App> list) {
			baseData.clear();
			baseData.addAll(list);
			refresh();
		}
		
		private void refresh() {
			adapterData.clear();
			adapterData.addAll(baseData);
			notifyDataSetChanged();
		}
		
		@Override
		public int getCount() {
			return adapterData.size();
		}
		
		@Override
		public Object getItem(int position) {
			return adapterData.get(position);
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if (view == null) {
				view = mInflater.inflate(R.layout.app_list_item, parent, false);
			}
			((TextView)view.findViewById(R.id.text1)).setText(adapterData.get(position).getLabel());
			((TextView)view.findViewById(R.id.text2)).setText(adapterData.get(position).getPackage());
			((CheckBox)view.findViewById(R.id.checkbox)).setChecked(adapterData.get(position).getEnabled());
			return view;
		}
		
		@Override
		public Filter getFilter() {
			if (filter == null) filter = new SimpleFilter();
			return filter;
		}
		
		private class SimpleFilter extends Filter {
			@Override
			protected FilterResults performFiltering(CharSequence prefix) {
				FilterResults results = new FilterResults();
				if (prefix == null || prefix.length() == 0) {
					results.values = baseData;
					results.count = baseData.size();
				} else {
					String prefixString = prefix.toString().toLowerCase();
					ArrayList<App> newValues = new ArrayList<App>();
					for (App app : baseData) {
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
				adapterData.clear();
				adapterData.addAll((ArrayList<App>)results.values);
				if (results.count > 0) notifyDataSetChanged();
				else notifyDataSetInvalidated();
			}
		}
	}
}
