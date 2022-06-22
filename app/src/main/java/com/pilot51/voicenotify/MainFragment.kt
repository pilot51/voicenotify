/*
 * Copyright 2022 Mark Injerd
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
package com.pilot51.voicenotify

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.*

class MainFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {
	private val navController by lazy { findNavController() }
	private lateinit var pStatus: Preference
	private lateinit var pAppList: Preference
	private lateinit var pTtsConfig: Preference
	private lateinit var pDeviceState: Preference
	private lateinit var pQuietStart: Preference
	private lateinit var pQuietEnd: Preference
	private lateinit var pTest: Preference
	private lateinit var pNotifyLog: Preference
	private lateinit var pSupport: Preference
	private val statusListener = object : Service.OnStatusChangeListener {
		override fun onStatusChanged() {
			updateStatus()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val activity = requireActivity()
		val context = requireContext()
		Common.init(activity)
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
			!= PackageManager.PERMISSION_GRANTED
		) {
			if (shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
				AlertDialog.Builder(context)
					.setMessage(R.string.permission_rationale_read_phone_state)
					.setPositiveButton(android.R.string.ok) { _, _ -> requestPhoneStatePerm() }
					.show()
			} else {
				requestPhoneStatePerm()
			}
		}
	}

	private fun requestPhoneStatePerm() {
		ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_PHONE_STATE), 1)
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.preferences)
		pStatus = findPreference(getString(R.string.key_status))!!
		pStatus.onPreferenceClickListener = this
		pAppList = findPreference(getString(R.string.key_appList))!!
		pAppList.onPreferenceClickListener = this
		pTtsConfig = findPreference(getString(R.string.key_ttsConfig))!!
		pTtsConfig.onPreferenceClickListener = this
		pDeviceState = findPreference(getString(R.string.key_device_state))!!
		pDeviceState.onPreferenceClickListener = this
		pQuietStart = findPreference(getString(R.string.key_quietStart))!!
		pQuietStart.onPreferenceClickListener = this
		pQuietEnd = findPreference(getString(R.string.key_quietEnd))!!
		pQuietEnd.onPreferenceClickListener = this
		pTest = findPreference(getString(R.string.key_test))!!
		pTest.onPreferenceClickListener = this
		pNotifyLog = findPreference(getString(R.string.key_notify_log))!!
		pNotifyLog.onPreferenceClickListener = this
		pSupport = findPreference(getString(R.string.key_support))!!
		pSupport.onPreferenceClickListener = this
	}

	override fun onResume() {
		super.onResume()
		Service.registerOnStatusChangeListener(statusListener)
		updateStatus()
	}

	override fun onPause() {
		Service.unregisterOnStatusChangeListener(statusListener)
		super.onPause()
	}

	override fun onPreferenceClick(preference: Preference): Boolean {
		return when (preference) {
			pStatus -> {
				if (!Service.isRunning) {
					startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
				} else Service.toggleSuspend()
				true
			}
			pAppList -> {
				navController.navigate(R.id.action_appListFragment)
				true
			}
			pTtsConfig -> {
				navController.navigate(R.id.action_ttsConfigFragment)
				true
			}
			pDeviceState -> {
				navController.navigate(MainFragmentDirections.actionPrefDialog(PrefDialogID.DEVICE_STATE))
				true
			}
			pQuietStart -> {
				navController.navigate(MainFragmentDirections.actionPrefDialog(PrefDialogID.QUIET_START))
				true
			}
			pQuietEnd -> {
				navController.navigate(MainFragmentDirections.actionPrefDialog(PrefDialogID.QUIET_END))
				true
			}
			pTest -> {
				val context = requireContext().applicationContext
				val vnApp = AppListFragment.findOrAddApp(context.packageName, context)!!
				if (!vnApp.enabled) {
					Toast.makeText(context, getString(R.string.test_ignored), Toast.LENGTH_LONG).show()
				}
				val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
				val intent = requireActivity().intent
				Timer().schedule(object : TimerTask() {
					override fun run() {
						val id = context.getString(R.string.notification_channel_id)
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							var channel = notificationManager.getNotificationChannel(id)
							if (channel == null) {
								channel = NotificationChannel(id, context.getString(R.string.test), NotificationManager.IMPORTANCE_LOW)
								channel.description = context.getString(R.string.notification_channel_desc)
								notificationManager.createNotificationChannel(channel)
							}
						}
						val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
							PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
						} else PendingIntent.FLAG_UPDATE_CURRENT
						val pi = PendingIntent.getActivity(context, 0, intent, flags)
						val builder = NotificationCompat.Builder(context, id)
							.setAutoCancel(true)
							.setContentIntent(pi)
							.setSmallIcon(R.drawable.ic_notification)
							.setTicker(context.getString(R.string.test_ticker))
							.setSubText(context.getString(R.string.test_subtext))
							.setContentTitle(context.getString(R.string.test_content_title))
							.setContentText(context.getString(R.string.test_content_text))
							.setContentInfo(context.getString(R.string.test_content_info))
						notificationManager.notify(0, builder.build())
					}
				}, 5000)
				true
			}
			pNotifyLog -> {
				navController.navigate(MainFragmentDirections.actionPrefDialog(PrefDialogID.LOG))
				true
			}
			pSupport -> {
				navController.navigate(MainFragmentDirections.actionPrefDialog(PrefDialogID.SUPPORT))
				true
			}
			else -> false
		}
	}

	private fun updateStatus() {
		if (Service.isSuspended && Service.isRunning) {
			pStatus.setTitle(R.string.service_suspended)
			pStatus.setSummary(R.string.status_summary_suspended)
			pStatus.intent = null
		} else {
			pStatus.setTitle(if (Service.isRunning) R.string.service_running else R.string.service_disabled)
			val context = requireContext()
			if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
				pStatus.setSummary(R.string.status_summary_notification_access_enabled)
			} else {
				pStatus.setSummary(R.string.status_summary_notification_access_disabled)
			}
			pStatus.intent = Common.notificationListenerSettingsIntent
		}
	}
}
