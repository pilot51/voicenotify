package com.pilot51.voicenotify;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

import android.content.Context;
import android.util.Log;

public class Common {
	protected String TAG;
	private Context context;
	
	Common(Context c) {
		context = c;
		TAG = c.getString(R.string.app_name);
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
