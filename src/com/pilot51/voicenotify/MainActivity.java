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

import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.TimePicker;
import android.widget.Toast;

import com.pilot51.voicenotify.Service.OnStatusChangeListener;

public class MainActivity extends PreferenceActivity implements OnPreferenceClickListener, OnSharedPreferenceChangeListener {
	private static final int SDK_VERSION = Build.VERSION.SDK_INT;
	private Preference pStatus, pDeviceState, pQuietStart, pQuietEnd, pTest, pNotifyLog, pSupport;
	private static final int DLG_DEVICE_STATE = 0,
	                         DLG_QUIET_START = 1,
	                         DLG_QUIET_END = 2,
	                         DLG_LOG = 3,
	                         DLG_SUPPORT = 4,
	                         DLG_DONATE = 5;
	private OnStatusChangeListener statusListener = new OnStatusChangeListener() {
		@Override
		public void onStatusChanged() {
			updateStatus();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Common.init(this);
		addPreferencesFromResource(R.xml.preferences);
		pStatus = findPreference(getString(R.string.key_status));
		pStatus.setOnPreferenceClickListener(this);
		pDeviceState = findPreference(getString(R.string.key_device_state));
		pDeviceState.setOnPreferenceClickListener(this);
		pQuietStart = findPreference(getString(R.string.key_quietStart));
		pQuietStart.setOnPreferenceClickListener(this);
		pQuietEnd = findPreference(getString(R.string.key_quietEnd));
		pQuietEnd.setOnPreferenceClickListener(this);
		pTest = findPreference(getString(R.string.key_test));
		pTest.setOnPreferenceClickListener(this);
		pNotifyLog = findPreference(getString(R.string.key_notify_log));
		pNotifyLog.setOnPreferenceClickListener(this);
		pSupport = findPreference(getString(R.string.key_support));
		pSupport.setOnPreferenceClickListener(this);
		findPreference(getString(R.string.key_appList)).setIntent(new Intent(this, AppList.class));
		Preference pTTS = findPreference(getString(R.string.key_ttsSettings));
		Intent ttsIntent = getTtsIntent();
		if (ttsIntent != null) {
			pTTS.setIntent(ttsIntent);
		} else {
			pTTS.setEnabled(false);
			pTTS.setSummary(R.string.tts_settings_summary_fail);
		}
		if (SDK_VERSION < 11) {
			getPreferenceScreen().removePreference(findPreference(getString(R.string.key_toasts)));
			if (SDK_VERSION < 8) {
				getPreferenceScreen().removePreference(findPreference(getString(R.string.key_audio_focus)));
			}
		}
	}
	
	static Intent getAccessibilityIntent() {
		Intent intent = new Intent();
		if (SDK_VERSION > 4) {
			intent.setAction(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
		} else if (SDK_VERSION == 4) {
			intent.setAction(Intent.ACTION_MAIN);
			intent.setClassName("com.android.settings", "com.android.settings.AccessibilitySettings");
		}
		return intent;
	}
	
	private Intent getTtsIntent() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		if (isClassExist("com.android.settings.TextToSpeechSettings")) {
			if (SDK_VERSION >= 11 && SDK_VERSION <= 13) {
				intent.setAction(android.provider.Settings.ACTION_SETTINGS);
				intent.putExtra(EXTRA_SHOW_FRAGMENT, "com.android.settings.TextToSpeechSettings");
				intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, intent.getExtras());
			} else intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
		} else if (isClassExist("com.android.settings.Settings$TextToSpeechSettingsActivity")) {
			if (SDK_VERSION == 14) {
				intent.setAction(android.provider.Settings.ACTION_SETTINGS);
				intent.putExtra(EXTRA_SHOW_FRAGMENT, "com.android.settings.tts.TextToSpeechSettings");
				intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, intent.getExtras());
			} else intent.setClassName("com.android.settings", "com.android.settings.Settings$TextToSpeechSettingsActivity");
		} else if (isClassExist("com.google.tv.settings.TextToSpeechSettingsTop")) {
			intent.setClassName("com.google.tv.settings", "com.google.tv.settings.TextToSpeechSettingsTop");
		} else return null;
		return intent;
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
		if (preference == pStatus && Service.isRunning() && Service.isSuspended()) {
			Service.toggleSuspend();
			return true;
		} else if (preference == pDeviceState) {
			showDialog(DLG_DEVICE_STATE);
			return true;
		} else if (preference == pQuietStart) {
			showDialog(DLG_QUIET_START);
			return true;
		} else if (preference == pQuietEnd) {
			showDialog(DLG_QUIET_END);
			return true;
		} else if (preference == pTest) {
			if (!AppList.findOrAddApp(getPackageName(), this).getEnabled()) {
				Toast.makeText(this, getString(R.string.test_ignored), Toast.LENGTH_LONG).show();
			}
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					Notification notification = new Notification(R.drawable.icon,
						getString(R.string.test_notify_msg), System.currentTimeMillis());
					notification.defaults |= Notification.DEFAULT_SOUND;
					notification.flags |= Notification.FLAG_AUTO_CANCEL;
					notification.setLatestEventInfo(MainActivity.this, getString(R.string.app_name), getString(R.string.test),
						PendingIntent.getActivity(MainActivity.this, 0, getIntent(), 0));
					((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, notification);
				}
			}, 5000);
			return true;
		} else if (preference == pNotifyLog) {
			showDialog(DLG_LOG);
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
			final CharSequence[] items = getResources().getStringArray(R.array.device_states);
			return new AlertDialog.Builder(this)
			.setTitle(R.string.device_state_dialog_title)
			.setMultiChoiceItems(items,
				new boolean[] {
					Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true),
					Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SCREEN_ON, true),
					Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true),
					Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_HEADSET_ON, true),
					Common.getPrefs(this).getBoolean(Common.KEY_SPEAK_SILENT_ON, false)
				},
				new DialogInterface.OnMultiChoiceClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which, boolean isChecked) {
						if (which == 0) { // Screen off
							Common.getPrefs(MainActivity.this).edit().putBoolean(Common.KEY_SPEAK_SCREEN_OFF, isChecked).commit();
						} else if (which == 1) { // Screen on
							Common.getPrefs(MainActivity.this).edit().putBoolean(Common.KEY_SPEAK_SCREEN_ON, isChecked).commit();
						} else if (which == 2) { // Headset off
							Common.getPrefs(MainActivity.this).edit().putBoolean(Common.KEY_SPEAK_HEADSET_OFF, isChecked).commit();
						} else if (which == 3) { // Headset on
							Common.getPrefs(MainActivity.this).edit().putBoolean(Common.KEY_SPEAK_HEADSET_ON, isChecked).commit();
						} else if (which == 4) { // Silent/vibrate
							Common.getPrefs(MainActivity.this).edit().putBoolean(Common.KEY_SPEAK_SILENT_ON, isChecked).commit();
						}
					}
				}
			).create();
		case DLG_QUIET_START:
			i = Common.getPrefs(this).getInt(getString(R.string.key_quietStart), 0);
			return new TimePickerDialog(this, sTimeSetListener, i/60, i%60, false);
		case DLG_QUIET_END:
			i = Common.getPrefs(this).getInt(getString(R.string.key_quietEnd), 0);
			return new TimePickerDialog(this, eTimeSetListener, i/60, i%60, false);
		case DLG_LOG:
			return new AlertDialog.Builder(this)
			.setTitle(R.string.notify_log)
			.setView(new NotifyList(this))
			.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
				}
			})
			.create();
		case DLG_SUPPORT:
			return new AlertDialog.Builder(this)
			.setTitle(R.string.support)
			.setItems(R.array.support_items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					switch (item) {
						case 0: // Donate
							showDialog(DLG_DONATE);
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
							String version = null;
							try {
								version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
							} catch (NameNotFoundException e) {
								e.printStackTrace();
							}
							iEmail.putExtra(Intent.EXTRA_TEXT,
							                getString(R.string.email_body,
							                          version,
							                          Build.VERSION.RELEASE,
							                          Build.ID,
							                          Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL));
							try {
								startActivity(iEmail);
							} catch (ActivityNotFoundException e) {
								e.printStackTrace();
								Toast.makeText(getBaseContext(), R.string.error_email, Toast.LENGTH_LONG).show();
							}
							break;
						case 3: // Translations
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://getlocalization.com/voicenotify")));
							break;
						case 4: // Source Code
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pilot51/voicenotify")));
							break;
					}
				}
			}).create();
		case DLG_DONATE:
			return new AlertDialog.Builder(this)
			.setTitle(R.string.donate)
			.setItems(R.array.donate_services, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					switch (item) {
						case 0: // Google Wallet
							showWalletDialog();
							break;
						case 1: // PayPal
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.com/cgi-bin/webscr?"
									+ "cmd=_donations&business=pilota51%40gmail%2ecom&lc=US&item_name=Voice%20Notify&"
									+ "no_note=0&no_shipping=1&currency_code=USD")));
							break;
					}
				}
			}).create();
		}
		return null;
	}
	
	private void showWalletDialog() {
		final Intent walletIntent = getWalletIntent();
		AlertDialog.Builder dlg = new AlertDialog.Builder(this)
		.setTitle(R.string.donate_wallet_title)
		.setMessage(R.string.donate_wallet_message)
		.setNegativeButton(android.R.string.cancel, null);
		if (walletIntent != null) {
			dlg.setPositiveButton(R.string.donate_wallet_launch_app, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					startActivity(walletIntent);
				}
			});
		} else {
			dlg.setPositiveButton(R.string.donate_wallet_launch_web, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wallet.google.com/manage/#sendMoney:")));
				}
			});
		}
		dlg.show();
	}
	
	/**
	 * @return The intent for Google Wallet, otherwise null if installation is not found.
	 */
	private Intent getWalletIntent() {
		try {
			getPackageManager().getPackageInfo("com.google.android.apps.walletnfcrel", PackageManager.GET_ACTIVITIES);
			return new Intent(Intent.ACTION_MAIN)
			       .setComponent(new ComponentName("com.google.android.apps.walletnfcrel",
			                                       "com.google.android.apps.wallet.WalletRootActivity"));
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}
	
	private TimePickerDialog.OnTimeSetListener sTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			Common.getPrefs(MainActivity.this).edit().putInt(getString(R.string.key_quietStart), hourOfDay * 60 + minute).commit();
		}
	};
	private TimePickerDialog.OnTimeSetListener eTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			Common.getPrefs(MainActivity.this).edit().putInt(getString(R.string.key_quietEnd), hourOfDay * 60 + minute).commit();
		}
	};
	
	private void updateStatus() {
		if (Service.isSuspended() && Service.isRunning()) {
			pStatus.setTitle(R.string.service_suspended);
			pStatus.setSummary(R.string.status_summary_suspended);
			pStatus.setIntent(null);
		} else {
			pStatus.setTitle(Service.isRunning() ? R.string.service_running : R.string.service_disabled);
			pStatus.setSummary(R.string.status_summary_accessibility);
			pStatus.setIntent(getAccessibilityIntent());
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Common.getPrefs(this).registerOnSharedPreferenceChangeListener(this);
		Service.registerOnStatusChangeListener(statusListener);
		updateStatus();
	}
	
	@Override
	protected void onPause() {
		Service.unregisterOnStatusChangeListener(statusListener);
		Common.getPrefs(this).unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		if (key.equals(getString(R.string.key_ttsStream))) {
			Common.setVolumeStream(this);
		}
	}
}
