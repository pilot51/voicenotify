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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.NotificationChannel;
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
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.TimePicker;
import android.widget.Toast;

import com.pilot51.voicenotify.Service.OnStatusChangeListener;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) {
			MainFragment fragment = new MainFragment();
			getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
		}
	}
	
	public static class MainFragment extends PreferenceFragment implements OnPreferenceClickListener, OnSharedPreferenceChangeListener {
		private Preference pStatus, pDeviceState, pQuietStart, pQuietEnd, pTest, pNotifyLog, pSupport;
		private final OnStatusChangeListener statusListener = new OnStatusChangeListener() {
			@Override
			public void onStatusChanged() {
				updateStatus();
			}
		};
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			Common.init(getActivity());
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
			findPreference(getString(R.string.key_appList)).setIntent(new Intent(getActivity(), AppListActivity.class));
			Preference pTTS = findPreference(getString(R.string.key_ttsSettings));
			Intent ttsIntent = getTtsIntent();
			if (ttsIntent != null) {
				pTTS.setIntent(ttsIntent);
			} else {
				pTTS.setEnabled(false);
				pTTS.setSummary(R.string.tts_settings_summary_fail);
			}
			EditTextPreference pTtsString = (EditTextPreference)findPreference(getString(R.string.key_ttsString));
			if (pTtsString.getText().contains("%")) {
				Toast.makeText(getActivity(), R.string.tts_message_reset_default, Toast.LENGTH_LONG).show();
				pTtsString.setText(getString(R.string.ttsString_default_value));
			}
		}
		
		private Intent getTtsIntent() {
			Intent intent = new Intent(Intent.ACTION_MAIN);
			if (checkActivityExist("com.android.settings.TextToSpeechSettings")) {
				intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
			} else if (checkActivityExist("com.android.settings.Settings$TextToSpeechSettingsActivity")) {
				intent.setClassName("com.android.settings", "com.android.settings.Settings$TextToSpeechSettingsActivity");
			} else if (checkActivityExist("com.google.tv.settings.TextToSpeechSettingsTop")) {
				intent.setClassName("com.google.tv.settings", "com.google.tv.settings.TextToSpeechSettingsTop");
			} else return null;
			return intent;
		}
		
		private boolean checkActivityExist(String name) {
			try {
				PackageInfo pkgInfo = getActivity().getPackageManager().getPackageInfo(
						name.substring(0, name.lastIndexOf(".")), PackageManager.GET_ACTIVITIES);
				if (pkgInfo.activities != null) {
					for (int n = 0; n < pkgInfo.activities.length; n++) {
						if (pkgInfo.activities[n].name.equals(name)) return true;
					}
				}
			} catch (PackageManager.NameNotFoundException e) {
				e.printStackTrace();
			}
			return false;
		}
		
		@Override
		public boolean onPreferenceClick(Preference preference) {
			if (preference == pStatus && Service.isRunning() && Service.isSuspended()) {
				Service.toggleSuspend();
				return true;
			} else if (preference == pDeviceState) {
				MyDialog.show(getFragmentManager(), MyDialog.ID.DEVICE_STATE);
				return true;
			} else if (preference == pQuietStart) {
				MyDialog.show(getFragmentManager(), MyDialog.ID.QUIET_START);
				return true;
			} else if (preference == pQuietEnd) {
				MyDialog.show(getFragmentManager(), MyDialog.ID.QUIET_END);
				return true;
			} else if (preference == pTest) {
				final Context context = getActivity().getApplicationContext();
				App vnApp = AppListActivity.findOrAddApp(context.getPackageName(), context);
				assert vnApp != null; // Prevent Lint warning. Should never be null, I want a crash report if it is.
				if (!vnApp.getEnabled()) {
					Toast.makeText(context, getString(R.string.test_ignored), Toast.LENGTH_LONG).show();
				}
				final NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
				if (notificationManager != null) {
					final Intent intent = getActivity().getIntent();
					new Timer().schedule(new TimerTask() {
						@Override
						public void run() {
							String id = context.getString(R.string.notification_channel_id);
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
								NotificationChannel channel = notificationManager.getNotificationChannel(id);
								if (channel == null) {
									channel = new NotificationChannel(id, context.getString(R.string.test), NotificationManager.IMPORTANCE_LOW);
									channel.setDescription(context.getString(R.string.notification_channel_desc));
									notificationManager.createNotificationChannel(channel);
								}
							}
							PendingIntent pi = PendingIntent.getActivity(context,
									0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
							NotificationCompat.Builder builder =
									new NotificationCompat.Builder(context, id)
											.setAutoCancel(true)
											.setContentIntent(pi)
											.setSmallIcon(R.drawable.ic_notification)
											.setTicker(context.getString(R.string.test_ticker))
											.setSubText(context.getString(R.string.test_subtext))
											.setContentTitle(context.getString(R.string.test_content_title))
											.setContentText(context.getString(R.string.test_content_text))
											.setContentInfo(context.getString(R.string.test_content_info));
							notificationManager.notify(0, builder.build());
						}
					}, 5000);
				}
				return true;
			} else if (preference == pNotifyLog) {
				MyDialog.show(getFragmentManager(), MyDialog.ID.LOG);
				return true;
			} else if (preference == pSupport) {
				MyDialog.show(getFragmentManager(), MyDialog.ID.SUPPORT);
				return true;
			}
			return false;
		}
		
		private void updateStatus() {
			if (Service.isSuspended() && Service.isRunning()) {
				pStatus.setTitle(R.string.service_suspended);
				pStatus.setSummary(R.string.status_summary_suspended);
				pStatus.setIntent(null);
			} else {
				pStatus.setTitle(Service.isRunning() ? R.string.service_running : R.string.service_disabled);
				if (NotificationManagerCompat.getEnabledListenerPackages(getActivity()).contains(getActivity().getPackageName())) {
					pStatus.setSummary(R.string.status_summary_notification_access_enabled);
				} else {
					pStatus.setSummary(R.string.status_summary_notification_access_disabled);
				}
				pStatus.setIntent(Common.getNotificationListenerSettingsIntent());
			}
		}
		
		@Override
		public void onResume() {
			super.onResume();
			Common.getPrefs(getActivity()).registerOnSharedPreferenceChangeListener(this);
			Service.registerOnStatusChangeListener(statusListener);
			updateStatus();
		}
		
		@Override
		public void onPause() {
			Service.unregisterOnStatusChangeListener(statusListener);
			Common.getPrefs(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
			super.onPause();
		}
		
		public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
			if (key.equals(getString(R.string.key_ttsStream))) {
				Common.setVolumeStream(getActivity());
			}
		}
	}
	
	public static class MyDialog extends DialogFragment {
		private static final String KEY_ID = "id";
		
		private enum ID {
			DEVICE_STATE,
			QUIET_START,
			QUIET_END,
			LOG,
			SUPPORT,
			DONATE,
			WALLET,
		}
		
		private final TimePickerDialog.OnTimeSetListener sTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
			public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
				Common.getPrefs(getActivity()).edit().putInt(getString(R.string.key_quietStart), hourOfDay * 60 + minute).apply();
			}
		};
		private final TimePickerDialog.OnTimeSetListener eTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
			public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
				Common.getPrefs(getActivity()).edit().putInt(getString(R.string.key_quietEnd), hourOfDay * 60 + minute).apply();
			}
		};
		
		public MyDialog() {}
		
		private static void show(FragmentManager fm, ID id) {
			Bundle bundle = new Bundle();
			bundle.putSerializable(KEY_ID, id);
			MyDialog dialogFragment = new MyDialog();
			dialogFragment.setArguments(bundle);
			dialogFragment.show(fm, id.name());
		}
		
		/**
		 * @return The intent for Google Wallet, otherwise null if installation is not found.
		 */
		private Intent getWalletIntent() {
			String walletPackage = "com.google.android.apps.gmoney";
			PackageManager pm = getActivity().getPackageManager();
			try {
				pm.getPackageInfo(walletPackage, PackageManager.GET_ACTIVITIES);
				return pm.getLaunchIntentForPackage(walletPackage);
			} catch (PackageManager.NameNotFoundException e) {
				return null;
			}
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			ID id = (ID)getArguments().getSerializable(KEY_ID);
			assert id != null; // Prevent Lint warning. Should never be null, I want a crash report if it is.
			switch (id) {
				case DEVICE_STATE:
					final CharSequence[] items = getResources().getStringArray(R.array.device_states);
					return new AlertDialog.Builder(getActivity())
							.setTitle(R.string.device_state_dialog_title)
							.setMultiChoiceItems(items,
									new boolean[] {
											Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true),
											Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_SCREEN_ON, true),
											Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true),
											Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_HEADSET_ON, true),
											Common.getPrefs(getActivity()).getBoolean(Common.KEY_SPEAK_SILENT_ON, false)
									},
									new DialogInterface.OnMultiChoiceClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which, boolean isChecked) {
											switch (which) {
												case 0:  // Screen off
													Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_SCREEN_OFF, isChecked).apply();
													break;
												case 1:  // Screen on
													Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_SCREEN_ON, isChecked).apply();
													break;
												case 2:  // Headset off
													Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_HEADSET_OFF, isChecked).apply();
													break;
												case 3:  // Headset on
													Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_HEADSET_ON, isChecked).apply();
													break;
												case 4:  // Silent/vibrate
													Common.getPrefs(getActivity()).edit().putBoolean(Common.KEY_SPEAK_SILENT_ON, isChecked).apply();
													break;
											}
										}
									}
							).create();
				case QUIET_START:
					int quietStart = Common.getPrefs(getActivity()).getInt(getString(R.string.key_quietStart), 0);
					return new TimePickerDialog(getActivity(), sTimeSetListener,
							quietStart / 60, quietStart % 60, false);
				case QUIET_END:
					int quietEnd = Common.getPrefs(getActivity()).getInt(getString(R.string.key_quietEnd), 0);
					return new TimePickerDialog(getActivity(), eTimeSetListener,
							quietEnd / 60, quietEnd % 60, false);
				case LOG:
					return new AlertDialog.Builder(getActivity())
							.setTitle(R.string.notify_log)
							.setView(new NotifyList(getActivity()))
							.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									dialog.dismiss();
								}
							})
							.create();
				case SUPPORT:
					return new AlertDialog.Builder(getActivity())
							.setTitle(R.string.support)
							.setItems(R.array.support_items, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int item) {
									switch (item) {
										case 0: // Donate
											MyDialog.show(getFragmentManager(), ID.DONATE);
											break;
										case 1: // Rate/Comment
											Intent iMarket = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.pilot51.voicenotify"));
											iMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
											try {
												startActivity(iMarket);
											} catch (ActivityNotFoundException e) {
												e.printStackTrace();
												Toast.makeText(getActivity(), R.string.error_market, Toast.LENGTH_LONG).show();
											}
											break;
										case 2: // Contact developer
											Intent iEmail = new Intent(Intent.ACTION_SEND);
											iEmail.setType("plain/text");
											iEmail.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.dev_email)});
											iEmail.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
											String version = null;
											try {
												version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
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
												Toast.makeText(getActivity(), R.string.error_email, Toast.LENGTH_LONG).show();
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
				case DONATE:
					return new AlertDialog.Builder(getActivity())
							.setTitle(R.string.donate)
							.setItems(R.array.donate_services, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int item) {
									switch (item) {
										case 0: // Google Wallet
											MyDialog.show(getFragmentManager(), ID.WALLET);
											break;
										case 1: // PayPal
											startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.com/cgi-bin/webscr?"
													+ "cmd=_donations&business=pilota51%40gmail%2ecom&lc=US&item_name=Voice%20Notify&"
													+ "no_note=0&no_shipping=1&currency_code=USD")));
											break;
									}
								}
							}).create();
				case WALLET:
					final Intent walletIntent = getWalletIntent();
					AlertDialog.Builder dlg = new AlertDialog.Builder(getActivity())
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
								startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wallet.google.com")));
							}
						});
					}
					return dlg.create();
			}
			return null;
		}
	}
}
