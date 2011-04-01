package com.pilot51.voicenotify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class MainActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private Common common;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		addPreferencesFromResource(R.xml.preferences);

		Preference accessPref = (Preference)findPreference("accessibility");
		int sdkVer = android.os.Build.VERSION.SDK_INT;
		if (sdkVer > 4) {
			accessPref.setIntent(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
		} else if (sdkVer == 4) {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setClassName("com.android.settings", "com.android.settings.AccessibilitySettings");
			accessPref.setIntent(intent);
		}
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
		((Preference)findPreference("ttsSettings")).setIntent(intent);
		((Preference)findPreference("appList")).setIntent(new Intent(this, AppList.class));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		common.prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		common.prefs.unregisterOnSharedPreferenceChangeListener(this);
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		if (key.equals("ttsStream")) {
			common.setVolumeStream();
		}
	}
}
