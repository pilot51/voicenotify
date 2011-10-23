package com.pilot51.voicenotify;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.TimePicker;
import android.widget.Toast;

public class MainActivity extends PreferenceActivity implements OnPreferenceClickListener, OnSharedPreferenceChangeListener {
	private Common common;
	private AlertDialog dlgSupport;
	private Preference pSupport, pQuietStart, pQuietEnd;
	private static final int DLG_QUIET_START = 0, DLG_QUIET_END = 1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		addPreferencesFromResource(R.xml.preferences);
		dlgSupport = new AlertDialog.Builder(this)
		.setTitle(R.string.support)
		.setItems(R.array.support_items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				switch (item) {
					case 0: // Donate
						startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("https://paypal.com/cgi-bin/webscr?cmd=_donations&business=pilota51%40gmail%2ecom&lc=US&item_name=Voice%20Notify&no_note=0&no_shipping=1&currency_code=USD")));
						break;
					case 1: // Rate/Comment
						Intent iMarket = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.pilot51.voicenotify"));
						iMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						try {
							startActivity(iMarket);
						} catch (ActivityNotFoundException e) {
							e.printStackTrace();
							Toast.makeText(getBaseContext(), R.string.error_market, Toast.LENGTH_LONG).show();
						}
						break;
					case 2: // Contact developer
						Intent iEmail = new Intent(Intent.ACTION_SEND);
						iEmail.setType("plain/text");
						iEmail.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.dev_email)});
						iEmail.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
						try {
							startActivity(iEmail);
						} catch (ActivityNotFoundException e) {
							e.printStackTrace();
							Toast.makeText(getBaseContext(), R.string.error_email, Toast.LENGTH_LONG).show();
						}
						break;
				}
			}
		}).create();
		Preference pAccess = findPreference("accessibility");
		pSupport = findPreference("support");
		pSupport.setOnPreferenceClickListener(this);
		pQuietStart = findPreference("quietStart");
		pQuietStart.setOnPreferenceClickListener(this);
		pQuietEnd = findPreference("quietEnd");
		pQuietEnd.setOnPreferenceClickListener(this);
		int sdkVer = android.os.Build.VERSION.SDK_INT;
		if (sdkVer > 4)
			pAccess.setIntent(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
		else if (sdkVer == 4) {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.setClassName("com.android.settings", "com.android.settings.AccessibilitySettings");
			pAccess.setIntent(intent);
		}
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
		findPreference("ttsSettings").setIntent(intent);
		findPreference("appList").setIntent(new Intent(this, AppList.class));
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == pSupport) {
			dlgSupport.show();
			return true;
		} else if (preference == pQuietStart) {
			showDialog(DLG_QUIET_START);
			return true;
		} else if (preference == pQuietEnd) {
			showDialog(DLG_QUIET_END);
			return true;
		}
		return false;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		int i;
		switch (id) {
		case DLG_QUIET_START:
			i = Common.prefs.getInt("quietStart", 0);
			return new TimePickerDialog(MainActivity.this, sTimeSetListener, i/60, i%60, false);
		case DLG_QUIET_END:
			i = Common.prefs.getInt("quietEnd", 0);
			return new TimePickerDialog(MainActivity.this, eTimeSetListener, i/60, i%60, false);
		}
		return null;
	}

	private TimePickerDialog.OnTimeSetListener sTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			Common.prefs.edit().putInt("quietStart", hourOfDay * 60 + minute).commit();
		}
	};
	private TimePickerDialog.OnTimeSetListener eTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			Common.prefs.edit().putInt("quietEnd", hourOfDay * 60 + minute).commit();
		}
	};
	
	@Override
	protected void onResume() {
		super.onResume();
		Common.prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		Common.prefs.unregisterOnSharedPreferenceChangeListener(this);
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		if (key.equals("ttsStream"))
			common.setVolumeStream();
	}
}
