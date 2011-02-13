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
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		TAG = common.TAG;
		progress = new ProgressDialog(this);
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progress.setCancelable(true);
		progress.setTitle("App List");
		progress.setMessage("Loading...");
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
						adapter = new MySimpleAdapter(AppList.this, appArray, R.layout.app_list_item,
								new String[] {"label", "package", "enabled"},
								new int[] {R.id.text1, R.id.text2, R.id.checkbox});
						lv.setAdapter(adapter);
						lv.setOnItemClickListener(new OnItemClickListener() {
							@Override
							public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
								HashMap<String, String> clickedApp = appArray.get(position);
								clickedApp.put("enabled", Boolean.toString(!Boolean.parseBoolean(clickedApp.get("enabled"))));
								appArray.set(position, clickedApp);
								adapter = new MySimpleAdapter(AppList.this, appArray, R.layout.app_list_item,
										new String[] {"label", "package", "enabled"},
										new int[] {R.id.text1, R.id.text2, R.id.checkbox});
								lv.setAdapter(adapter);
								String
									pkg = clickedApp.get("package"),
									label = clickedApp.get("label");
								if (ignoredApps.contains(pkg)) {
									ignoredApps.remove(pkg);
									Toast.makeText(AppList.this, label + " is not ignored", Toast.LENGTH_SHORT).show();
								} else {
									ignoredApps.add(pkg);
									Toast.makeText(AppList.this, label + " is ignored", Toast.LENGTH_SHORT).show();
								}
								saveList(ignoredApps);
							}
						});
						progress.dismiss();
					}
				});
			}
		}).start();
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
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
