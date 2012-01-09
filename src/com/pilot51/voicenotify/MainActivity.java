package com.pilot51.voicenotify;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.TimePicker;
import android.widget.Toast;

public class MainActivity extends PreferenceActivity implements OnPreferenceClickListener, OnSharedPreferenceChangeListener {
	private Common common;
	private Preference pScreen, pQuietStart, pQuietEnd, pSupport;
	private static final int DLG_SCREEN = 0, DLG_QUIET_START = 1, DLG_QUIET_END = 2, DLG_SUPPORT = 3;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		addPreferencesFromResource(R.xml.preferences);
		pScreen = findPreference("screen");
		pScreen.setOnPreferenceClickListener(this);
		pQuietStart = findPreference("quietStart");
		pQuietStart.setOnPreferenceClickListener(this);
		pQuietEnd = findPreference("quietEnd");
		pQuietEnd.setOnPreferenceClickListener(this);
		pSupport = findPreference("support");
		pSupport.setOnPreferenceClickListener(this);
		findPreference("appList").setIntent(new Intent(this, AppList.class));
		Preference pAccess = findPreference("accessibility"),
			pTTS = findPreference("ttsSettings");
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
		if (!isCallable(intent))
			intent.setClassName("com.google.tv.settings", "com.google.tv.settings.TextToSpeechSettingsTop");
		if (isCallable(intent))
			pTTS.setIntent(intent);
		else {
			pTTS.setEnabled(false);
			pTTS.setSummary(R.string.tts_settings_summary_fail);
		}
	}
	
	private boolean isCallable(Intent intent) {
		return getPackageManager().resolveActivity(intent,
			PackageManager.MATCH_DEFAULT_ONLY) != null;
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == pScreen) {
			showDialog(DLG_SCREEN);
			return true;
		} else if (preference == pQuietStart) {
			showDialog(DLG_QUIET_START);
			return true;
		} else if (preference == pQuietEnd) {
			showDialog(DLG_QUIET_END);
			return true;
		} else if (preference == pSupport) {
			showDialog(DLG_SUPPORT);
			return true;
		}
		return false;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		int i;
		switch (id) {
		case DLG_SCREEN:
			final CharSequence[] items = {MainActivity.this.getString(R.string.off), MainActivity.this.getString(R.string.on)};
			return new AlertDialog.Builder(this)
			.setTitle(R.string.screen_state_dialog_title)
			.setMultiChoiceItems(items,
				new boolean[] {Common.prefs.getBoolean("speakScreenOff", true), Common.prefs.getBoolean("speakScreenOn", true)},
				new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						if (which == 0) // Off
							Common.prefs.edit().putBoolean("speakScreenOff", isChecked).commit();
						else if (which == 1) // On
							Common.prefs.edit().putBoolean("speakScreenOn", isChecked).commit();
					}
			}).create();
		case DLG_QUIET_START:
			i = Common.prefs.getInt("quietStart", 0);
			return new TimePickerDialog(MainActivity.this, sTimeSetListener, i/60, i%60, false);
		case DLG_QUIET_END:
			i = Common.prefs.getInt("quietEnd", 0);
			return new TimePickerDialog(MainActivity.this, eTimeSetListener, i/60, i%60, false);

		case DLG_SUPPORT:
			return new AlertDialog.Builder(this)
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
