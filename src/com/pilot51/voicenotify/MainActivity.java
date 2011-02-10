package com.pilot51.voicenotify;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class MainActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		Preference accessPref = (Preference)findPreference("accessibility");
		int sdkVer = android.os.Build.VERSION.SDK_INT;
		if (sdkVer > 4) {
			accessPref.setIntent(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
		} else if (sdkVer == 4) {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setClassName("com.android.settings", "com.android.settings.AccessibilitySettings");
			accessPref.setIntent(intent);
		}
		
		((Preference)findPreference("appList")).setIntent(new Intent(this, AppList.class));
	}
}
