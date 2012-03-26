package com.pilot51.voicenotify;

import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
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
	private Preference pDeviceState, pQuietStart, pQuietEnd, pTest, pSupport;
	private static final int DLG_DEVICE_STATE = 0, DLG_QUIET_START = 1, DLG_QUIET_END = 2, DLG_SUPPORT = 3;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		addPreferencesFromResource(R.xml.preferences);
		pDeviceState = findPreference("device_state");
		pDeviceState.setOnPreferenceClickListener(this);
		pQuietStart = findPreference("quietStart");
		pQuietStart.setOnPreferenceClickListener(this);
		pQuietEnd = findPreference("quietEnd");
		pQuietEnd.setOnPreferenceClickListener(this);
		pTest = findPreference("test");
		pTest.setOnPreferenceClickListener(this);
		pSupport = findPreference("support");
		pSupport.setOnPreferenceClickListener(this);
		findPreference("appList").setIntent(new Intent(this, AppList.class));
		Preference pAccess = findPreference("accessibility"),
			pTTS = findPreference("ttsSettings");
		Intent intent;
		int sdkVer = android.os.Build.VERSION.SDK_INT;
		if (sdkVer > 4)
			pAccess.setIntent(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
		else if (sdkVer == 4) {
			intent = new Intent(Intent.ACTION_MAIN);
			intent.setClassName("com.android.settings", "com.android.settings.AccessibilitySettings");
			pAccess.setIntent(intent);
		}
		intent = new Intent(Intent.ACTION_MAIN);
		if (isClassExist("com.android.settings.TextToSpeechSettings")) {
			if (sdkVer >= 11 & sdkVer <= 13) {
				intent.setAction(android.provider.Settings.ACTION_SETTINGS);
				intent.putExtra(EXTRA_SHOW_FRAGMENT, "com.android.settings.TextToSpeechSettings");
		        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, intent.getExtras());
			} else intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
		} else if (isClassExist("com.android.settings.Settings$TextToSpeechSettingsActivity")) {
			if (sdkVer == 14) {
				intent.setAction(android.provider.Settings.ACTION_SETTINGS);
				intent.putExtra(EXTRA_SHOW_FRAGMENT, "com.android.settings.tts.TextToSpeechSettings");
		        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, intent.getExtras());
			} else intent.setClassName("com.android.settings", "com.android.settings.Settings$TextToSpeechSettingsActivity");
		} else if (isClassExist("com.google.tv.settings.TextToSpeechSettingsTop"))
			intent.setClassName("com.google.tv.settings", "com.google.tv.settings.TextToSpeechSettingsTop");
		else {
			pTTS.setEnabled(false);
			pTTS.setSummary(R.string.tts_settings_summary_fail);
		}
		if (pTTS.isEnabled()) pTTS.setIntent(intent);
	}
	
	private boolean isClassExist(String name) {
		try {
			PackageInfo pkgInfo = getPackageManager().getPackageInfo(
				name.substring(0, name.lastIndexOf(".")), PackageManager.GET_ACTIVITIES);
			if (pkgInfo.activities != null) {
				for (int n = 0; n < pkgInfo.activities.length; n++) {
					if (pkgInfo.activities[n].name.equals(name)) return true;
				}
			}
		} catch (PackageManager.NameNotFoundException e) {}
		return false;
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == pDeviceState) {
			showDialog(DLG_DEVICE_STATE);
			return true;
		} else if (preference == pQuietStart) {
			showDialog(DLG_QUIET_START);
			return true;
		} else if (preference == pQuietEnd) {
			showDialog(DLG_QUIET_END);
			return true;
		} else if (preference == pTest) {
			if (!AppList.getIsEnabled(getPackageName()))
				Toast.makeText(this, getString(R.string.test_ignored), Toast.LENGTH_LONG).show();
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					Notification notification = new Notification(R.drawable.icon,
						getString(R.string.test_notify_msg), System.currentTimeMillis());
					notification.defaults |= Notification.DEFAULT_SOUND;
					notification.flags |= Notification.FLAG_AUTO_CANCEL;
					notification.setLatestEventInfo(MainActivity.this, Common.TAG, getString(R.string.test),
						PendingIntent.getActivity(MainActivity.this, 0, getIntent(), 0));
					((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notification);
				}
			}, 5000);
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
		case DLG_DEVICE_STATE:
			final CharSequence[] items = MainActivity.this.getResources().getStringArray(R.array.device_states);
			return new AlertDialog.Builder(this)
			.setTitle(R.string.device_state_dialog_title)
			.setMultiChoiceItems(items,
				new boolean[] {Common.prefs.getBoolean(Common.SPEAK_SCREEN_OFF, true), Common.prefs.getBoolean(Common.SPEAK_SCREEN_ON, true),
					Common.prefs.getBoolean(Common.SPEAK_HEADSET_OFF, true), Common.prefs.getBoolean(Common.SPEAK_HEADSET_ON, true),
					Common.prefs.getBoolean(Common.SPEAK_SILENT_ON, false)},
				new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						if (which == 0) // Screen off
							Common.prefs.edit().putBoolean(Common.SPEAK_SCREEN_OFF, isChecked).commit();
						else if (which == 1) // Screen on
							Common.prefs.edit().putBoolean(Common.SPEAK_SCREEN_ON, isChecked).commit();
						else if (which == 2) // Headset off
							Common.prefs.edit().putBoolean(Common.SPEAK_HEADSET_OFF, isChecked).commit();
						else if (which == 3) // Headset on
							Common.prefs.edit().putBoolean(Common.SPEAK_HEADSET_ON, isChecked).commit();
						else if (which == 4) // Silent/vibrate
							Common.prefs.edit().putBoolean(Common.SPEAK_SILENT_ON, isChecked).commit();
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
