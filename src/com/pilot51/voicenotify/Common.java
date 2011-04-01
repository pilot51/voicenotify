package com.pilot51.voicenotify;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class Common {
	protected String TAG;
	private Activity activity;
	private Context context;
	protected SharedPreferences prefs;
	
	Common(Activity a) {
		activity = a;
		context = a;
		onStart();
		setVolumeStream();
	}
	Common(Context c) {
		context = c;
		onStart();
	}
	void onStart() {
		TAG = context.getString(R.string.app_name);
		PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}
	
	protected void setVolumeStream() {
		if (prefs.getString("ttsStream", null).contentEquals("media"))
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		else if (prefs.getString("ttsStream", null).contentEquals("notification"))
			activity.setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<String> readList() {
		ArrayList<String> list = new ArrayList<String>();
		FileInputStream file = null;
		try {
			file = context.openFileInput("ignored_apps");
		} catch (FileNotFoundException e) {
			Log.i(TAG, "ignored_apps file does not exist");
			return list;
		}
		try {
			ObjectInputStream in = new ObjectInputStream(file);
			try {
				list = (ArrayList<String>)in.readObject();
			} catch (ClassNotFoundException e) {
				Log.e(TAG, "Error: Failed to read ignored_apps - Data appears corrupt");
				e.printStackTrace();
			}
			in.close();
		} catch (IOException e) {
			Log.e(TAG, "Error: Failed to read ignored_apps");
			e.printStackTrace();
		}
		return list;
	}
}
