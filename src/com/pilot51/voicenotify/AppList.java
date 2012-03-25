package com.pilot51.voicenotify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.ListActivity;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class AppList extends ListActivity {
	private ListView lv;
	private AppListAdapter adapter;
	private static ArrayList<App> apps;
	private List<ApplicationInfo> installedApps;
	private SharedPreferences prefs;
	private static boolean defEnable;
	private static final int IGNORE_TOGGLE = 0, IGNORE_ALL = 1, IGNORE_NONE = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		prefs = getSharedPreferences("defValues", MODE_WORLD_READABLE);
		defEnable = prefs.getBoolean("enable", true);
		new Thread(new Runnable() {
			public void run() {
				apps = Database.getApps();
				runOnUiThread(new Runnable() {
					public void run() {
						lv = getListView();
						lv.setTextFilterEnabled(true);
						adapter = new AppListAdapter(AppList.this, apps);
						lv.setAdapter(adapter);
						lv.setOnItemClickListener(new OnItemClickListener() {
							@Override
							public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
								setIgnore(position, IGNORE_TOGGLE);
								adapter.setData(apps);
							}
						});
						setProgressBarIndeterminateVisibility(true);
					}
				});
				PackageManager packMan = getPackageManager();
				
				// Remove uninstalled
				App app;
				for (int i = 0; i < apps.size(); i++) {
					app = apps.get(i);
					try {
						packMan.getApplicationInfo(app.getPackage(), 0);
					} catch (NameNotFoundException e) {
						app.remove();
						apps.remove(i);
						runOnUiThread(new Runnable() {
							public void run() {
								adapter.setData(apps);
							}
						});
					}
				}
				
				// Add new
				installedApps = packMan.getInstalledApplications(0);
				ApplicationInfo appInfo;
				inst:for (int i = 0; i < installedApps.size(); i++) {
					appInfo = installedApps.get(i);
					for (int n = 0; n < apps.size(); n++) {
						if (apps.get(n).getPackage().equals(appInfo.packageName))
							continue inst;
					}
					apps.add(new App(appInfo.packageName, String.valueOf(appInfo.loadLabel(packMan)),
						getIsEnabled(appInfo.packageName)).updateDb());
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
		for (int i = 0; i < apps.size(); i++) {
			setIgnore(i, ignoreType);
		}
		adapter.notifyDataSetChanged();
		new Thread(new Runnable() {
			public void run() {
				Database.setApps(apps);
			}
		}).start();
	}
	
	private void setIgnore(int position, int ignoreType) {
		App app = adapter.getData().get(position);
		if (!app.getEnabled() & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_NONE)) {
			app.setEnabled(true, ignoreType == IGNORE_TOGGLE);
			if (ignoreType == IGNORE_TOGGLE)
				Toast.makeText(this, app.getLabel() + " " + getString(R.string.is_not_ignored), Toast.LENGTH_SHORT).show();
		} else if (app.getEnabled() & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_ALL)) {
			app.setEnabled(false, ignoreType == IGNORE_TOGGLE);
			if (ignoreType == IGNORE_TOGGLE)
				Toast.makeText(this, app.getLabel() + " " + getString(R.string.is_ignored), Toast.LENGTH_SHORT).show();
		}
	}
	
	/** Set the default enabled value for new apps. */
	private void setDefaultEnable(boolean enable) {
		defEnable = enable;
		prefs.edit().putBoolean("enable", defEnable).commit();
	}
	
	protected static boolean getIsEnabled(String pkg) {
		App app;
		for (int n = 0; n < apps.size(); n++) {
			app = apps.get(n);
			if (app.getPackage().equals(pkg))
				return app.enabled;
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
}
