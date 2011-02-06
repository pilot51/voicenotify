package com.pilot51.voicenotify;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class MainActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		((Preference) findPreference("accessibility")).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
				return true;
			}
		});
		
		((Preference) findPreference("appList")).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(MainActivity.this, AppList.class));
				return true;
			}
		});
	}
}
