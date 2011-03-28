package com.pilot51.voicenotify;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class AppList extends ListActivity {
	private Common common;
	private MySimpleAdapter adapter;
	private ArrayList<HashMap<String, String>> appArray = new ArrayList<HashMap<String, String>>();
	private ProgressDialog progress;
	private String TAG;
	private ArrayList<String> ignoredApps;
	private static final int IGNORE_TOGGLE = 0, IGNORE_ON = 1, IGNORE_OFF = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		TAG = common.TAG;
		progress = new ProgressDialog(this);
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progress.setCancelable(true);
		progress.setTitle(R.string.app_list);
		progress.setMessage(getString(R.string.loading));
		progress.show();
		new Thread(new Runnable() {
			public void run() {
				ignoredApps = common.readList();
				PackageManager packMan = getPackageManager();
				//List<PackageInfo> packList = packMan.getInstalledPackages(0);
				List<ApplicationInfo> appList = packMan.getInstalledApplications(0);
				int listSize = appList.size();
				progress.setMax(listSize);
				ApplicationInfo appInfo = new ApplicationInfo();
				HashMap<String, String> app;
				for (int i = 0; i < listSize; i++) {
					appInfo = appList.get(i);
					app = new HashMap<String, String>();
					String pkg = appInfo.packageName;
					app.put("package", pkg);
					String label = String.valueOf(appInfo.loadLabel(packMan));
					app.put("label", label);
					app.put("enabled", Boolean.toString(!ignoredApps.contains(pkg)));
					appArray.add(app);
					//Log.d(TAG, "Label: " + label + " | Package: " + pkg);
					progress.setProgress(i + 1);
				}
				Collections.sort(appArray, new Comparator<HashMap<String, String>>() {
					@Override
					public int compare(HashMap<String, String> object1, HashMap<String, String> object2) {
						return object1.get("label").compareToIgnoreCase(object2.get("label"));
					}
				});
				runOnUiThread(new Runnable() {
					public void run() {
						final ListView lv = getListView();
						lv.setTextFilterEnabled(true);
						updateList(false);
						lv.setOnItemClickListener(new OnItemClickListener() {
							@Override
							public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
								setIgnore(position, IGNORE_TOGGLE);
								updateList(true);
							}
						});
						progress.dismiss();
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
			massIgnore(IGNORE_ON);
			return true;
		case 2:
			massIgnore(IGNORE_OFF);
			return true;
		}
		return false;
	}
	
	private void massIgnore(int ignoreType) {
		for (int i = 0; i < appArray.size(); i++) {
			setIgnore(i, ignoreType);
		}
		updateList(true);
	}
	
	private void setIgnore(int position, int ignoreType) {
		HashMap<String, String> app = appArray.get(position);
		String
			pkg = app.get("package"),
			label = app.get("label");
		if (ignoredApps.contains(pkg) & (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_OFF)) {
			ignoredApps.remove(pkg);
			app.put("enabled", Boolean.toString(true));
			if (ignoreType == IGNORE_TOGGLE) Toast.makeText(this, label + " " + getString(R.string.is_not_ignored), Toast.LENGTH_SHORT).show();
		} else if (ignoreType == IGNORE_TOGGLE | ignoreType == IGNORE_ON) {
			ignoredApps.add(pkg);
			app.put("enabled", Boolean.toString(false));
			if (ignoreType == IGNORE_TOGGLE) Toast.makeText(this, label + " " + getString(R.string.is_ignored), Toast.LENGTH_SHORT).show();
		}
		appArray.set(position, app);
	}
	
	private void updateList(boolean doSave) {
		adapter = new MySimpleAdapter(AppList.this, appArray, R.layout.app_list_item,
				new String[] {"label", "package", "enabled"},
				new int[] {R.id.text1, R.id.text2, R.id.checkbox});
		getListView().setAdapter(adapter);
		if (doSave) saveList(ignoredApps);
	}

	private void saveList(ArrayList<String> list) {
		try {
			ObjectOutputStream out = new ObjectOutputStream(openFileOutput("ignored_apps", Context.MODE_WORLD_READABLE));
			out.writeObject(list);
			out.flush();
			out.close();
		} catch (IOException e) {
			Log.e(TAG, "Error: Failed to save ignored_apps");
			e.printStackTrace();
		}
	}
}
