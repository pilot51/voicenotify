package com.pilot51.voicenotify;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class MainActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private Common common;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		common = new Common(this);
		addPreferencesFromResource(R.xml.preferences);
		final AlertDialog dlgSupport = new AlertDialog.Builder(this)
		.setTitle(R.string.support)
		.setItems(R.array.support_items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		        switch (item) {
		        	case 0: // Donate
		        		startActivity(new Intent(Intent.ACTION_VIEW,
		    				Uri.parse("https://paypal.com/cgi-bin/webscr?cmd=_donations&business=pilota51%40gmail%2ecom&lc=US&item_name=Voice%20Notify&no_note=1&no_shipping=1&currency_code=USD")));
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
		((Preference)findPreference("support")).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				dlgSupport.show();
				return true;
			}
		});
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
