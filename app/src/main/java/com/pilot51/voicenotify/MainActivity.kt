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
package com.pilot51.voicenotify

import android.app.*
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pilot51.voicenotify.MainActivity.MyDialog.ID
import com.pilot51.voicenotify.Service.OnStatusChangeListener
import java.util.*

class MainActivity : PreferenceActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (savedInstanceState == null) {
			val fragment = MainFragment()
			fragmentManager.beginTransaction().replace(android.R.id.content, fragment).commit()
		}
	}

	class MainFragment : PreferenceFragment(), OnPreferenceClickListener, OnSharedPreferenceChangeListener {
		private lateinit var pStatus: Preference
		private lateinit var pDeviceState: Preference
		private lateinit var pQuietStart: Preference
		private lateinit var pQuietEnd: Preference
		private lateinit var pTest: Preference
		private lateinit var pNotifyLog: Preference
		private lateinit var pSupport: Preference
		private val statusListener = object : OnStatusChangeListener {
			override fun onStatusChanged() {
				updateStatus()
			}
		}

		override fun onCreate(savedInstanceState: Bundle?) {
			super.onCreate(savedInstanceState)
			Common.init(activity)
			addPreferencesFromResource(R.xml.preferences)
			pStatus = findPreference(getString(R.string.key_status))
			pStatus.onPreferenceClickListener = this
			pDeviceState = findPreference(getString(R.string.key_device_state))
			pDeviceState.onPreferenceClickListener = this
			pQuietStart = findPreference(getString(R.string.key_quietStart))
			pQuietStart.onPreferenceClickListener = this
			pQuietEnd = findPreference(getString(R.string.key_quietEnd))
			pQuietEnd.onPreferenceClickListener = this
			pTest = findPreference(getString(R.string.key_test))
			pTest.onPreferenceClickListener = this
			pNotifyLog = findPreference(getString(R.string.key_notify_log))
			pNotifyLog.onPreferenceClickListener = this
			pSupport = findPreference(getString(R.string.key_support))
			pSupport.onPreferenceClickListener = this
			findPreference(getString(R.string.key_appList)).intent = Intent(activity, AppListActivity::class.java)
			val pTTS = findPreference(getString(R.string.key_ttsSettings))
			val ttsIntent = ttsIntent
			if (ttsIntent != null) {
				pTTS.intent = ttsIntent
			} else {
				pTTS.isEnabled = false
				pTTS.setSummary(R.string.tts_settings_summary_fail)
			}
			val pTtsString = findPreference(getString(R.string.key_ttsString)) as EditTextPreference
			if (pTtsString.text.contains("%")) {
				Toast.makeText(activity, R.string.tts_message_reset_default, Toast.LENGTH_LONG).show()
				pTtsString.text = getString(R.string.ttsString_default_value)
			}
		}

		private val ttsIntent: Intent?
			get() {
				val intent = Intent(Intent.ACTION_MAIN)
				when {
					checkActivityExist("com.android.settings.TextToSpeechSettings") ->
						intent.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings")
					checkActivityExist("com.android.settings.Settings\$TextToSpeechSettingsActivity") ->
						intent.setClassName("com.android.settings", "com.android.settings.Settings\$TextToSpeechSettingsActivity")
					checkActivityExist("com.google.tv.settings.TextToSpeechSettingsTop") ->
						intent.setClassName("com.google.tv.settings", "com.google.tv.settings.TextToSpeechSettingsTop")
					else -> return null
				}
				return intent
			}

		private fun checkActivityExist(name: String): Boolean {
			try {
				val pkgInfo = activity.packageManager.getPackageInfo(
					name.substring(0, name.lastIndexOf(".")), PackageManager.GET_ACTIVITIES)
				if (pkgInfo.activities != null) {
					for (n in pkgInfo.activities.indices) {
						if (pkgInfo.activities[n].name == name) return true
					}
				}
			} catch (e: PackageManager.NameNotFoundException) {
				e.printStackTrace()
			}
			return false
		}

		override fun onPreferenceClick(preference: Preference): Boolean {
			if (preference === pStatus && Service.isRunning && Service.isSuspended) {
				Service.toggleSuspend()
				return true
			} else if (preference === pDeviceState) {
				MyDialog.show(fragmentManager, ID.DEVICE_STATE)
				return true
			} else if (preference === pQuietStart) {
				MyDialog.show(fragmentManager, ID.QUIET_START)
				return true
			} else if (preference === pQuietEnd) {
				MyDialog.show(fragmentManager, ID.QUIET_END)
				return true
			} else if (preference === pTest) {
				val context = activity.applicationContext
				// Prevent Lint warning. Should never be null, I want a crash report if it is.
				val vnApp = AppListActivity.findOrAddApp(context.packageName, context)!!
				if (!vnApp.enabled) {
					Toast.makeText(context, getString(R.string.test_ignored), Toast.LENGTH_LONG).show()
				}
				val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
				val intent = activity.intent
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
				return true
			} else if (preference === pNotifyLog) {
				MyDialog.show(fragmentManager, ID.LOG)
				return true
			} else if (preference === pSupport) {
				MyDialog.show(fragmentManager, ID.SUPPORT)
				return true
			}
			return false
		}

		private fun updateStatus() {
			if (Service.isSuspended && Service.isRunning) {
				pStatus.setTitle(R.string.service_suspended)
				pStatus.setSummary(R.string.status_summary_suspended)
				pStatus.intent = null
			} else {
				pStatus.setTitle(if (Service.isRunning) R.string.service_running else R.string.service_disabled)
				if (NotificationManagerCompat.getEnabledListenerPackages(activity).contains(activity.packageName)) {
					pStatus.setSummary(R.string.status_summary_notification_access_enabled)
				} else {
					pStatus.setSummary(R.string.status_summary_notification_access_disabled)
				}
				pStatus.intent = Common.notificationListenerSettingsIntent
			}
		}

		override fun onResume() {
			super.onResume()
			Common.getPrefs(activity).registerOnSharedPreferenceChangeListener(this)
			Service.registerOnStatusChangeListener(statusListener)
			updateStatus()
		}

		override fun onPause() {
			Service.unregisterOnStatusChangeListener(statusListener)
			Common.getPrefs(activity).unregisterOnSharedPreferenceChangeListener(this)
			super.onPause()
		}

		override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
			if (key == getString(R.string.key_ttsStream)) {
				Common.setVolumeStream(activity)
			}
		}
	}

	class MyDialog : DialogFragment() {
		enum class ID {
			DEVICE_STATE, QUIET_START, QUIET_END, LOG, SUPPORT, DONATE, WALLET
		}

		private val sTimeSetListener = OnTimeSetListener { _, hourOfDay, minute -> Common.getPrefs(activity).edit().putInt(getString(R.string.key_quietStart), hourOfDay * 60 + minute).apply() }
		private val eTimeSetListener = OnTimeSetListener { _, hourOfDay, minute -> Common.getPrefs(activity).edit().putInt(getString(R.string.key_quietEnd), hourOfDay * 60 + minute).apply() }

		/**
		 * @return The intent for Google Wallet, otherwise null if installation is not found.
		 */
		private val walletIntent: Intent?
			get() {
				val walletPackage = "com.google.android.apps.gmoney"
				val pm = activity.packageManager
				return try {
					pm.getPackageInfo(walletPackage, PackageManager.GET_ACTIVITIES)
					pm.getLaunchIntentForPackage(walletPackage)
				} catch (e: PackageManager.NameNotFoundException) {
					null
				}
			}

		override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
			return when (arguments.getSerializable(KEY_ID) as ID) {
				ID.DEVICE_STATE -> {
					val items = resources.getStringArray(R.array.device_states)
					AlertDialog.Builder(activity)
						.setTitle(R.string.device_state_dialog_title)
						.setMultiChoiceItems(items, booleanArrayOf(
							Common.getPrefs(activity).getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true),
							Common.getPrefs(activity).getBoolean(Common.KEY_SPEAK_SCREEN_ON, true),
							Common.getPrefs(activity).getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true),
							Common.getPrefs(activity).getBoolean(Common.KEY_SPEAK_HEADSET_ON, true),
							Common.getPrefs(activity).getBoolean(Common.KEY_SPEAK_SILENT_ON, false)
						)
						) { _, which, isChecked ->
							when (which) {
								0 -> Common.getPrefs(activity).edit().putBoolean(Common.KEY_SPEAK_SCREEN_OFF, isChecked).apply()
								1 -> Common.getPrefs(activity).edit().putBoolean(Common.KEY_SPEAK_SCREEN_ON, isChecked).apply()
								2 -> Common.getPrefs(activity).edit().putBoolean(Common.KEY_SPEAK_HEADSET_OFF, isChecked).apply()
								3 -> Common.getPrefs(activity).edit().putBoolean(Common.KEY_SPEAK_HEADSET_ON, isChecked).apply()
								4 -> Common.getPrefs(activity).edit().putBoolean(Common.KEY_SPEAK_SILENT_ON, isChecked).apply()
							}
						}.create()
				}
				ID.QUIET_START -> {
					val quietStart = Common.getPrefs(activity).getInt(getString(R.string.key_quietStart), 0)
					TimePickerDialog(activity, sTimeSetListener,
						quietStart / 60, quietStart % 60, false)
				}
				ID.QUIET_END -> {
					val quietEnd = Common.getPrefs(activity).getInt(getString(R.string.key_quietEnd), 0)
					TimePickerDialog(activity, eTimeSetListener,
						quietEnd / 60, quietEnd % 60, false)
				}
				ID.LOG -> AlertDialog.Builder(activity)
					.setTitle(R.string.notify_log)
					.setView(NotifyList(activity))
					.setNeutralButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
					.create()
				ID.SUPPORT -> AlertDialog.Builder(activity)
					.setTitle(R.string.support)
					.setItems(R.array.support_items) { _, item ->
						when (item) {
							0 -> show(fragmentManager, ID.DONATE)
							1 -> {
								val iMarket = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.pilot51.voicenotify"))
								iMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
								try {
									startActivity(iMarket)
								} catch (e: ActivityNotFoundException) {
									e.printStackTrace()
									Toast.makeText(activity, R.string.error_market, Toast.LENGTH_LONG).show()
								}
							}
							2 -> {
								val iEmail = Intent(Intent.ACTION_SEND)
								iEmail.type = "plain/text"
								iEmail.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.dev_email)))
								iEmail.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
								var version: String? = null
								try {
									version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
								} catch (e: PackageManager.NameNotFoundException) {
									e.printStackTrace()
								}
								iEmail.putExtra(Intent.EXTRA_TEXT,
									getString(R.string.email_body,
										version,
										Build.VERSION.RELEASE,
										Build.ID,
										Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL))
								try {
									startActivity(iEmail)
								} catch (e: ActivityNotFoundException) {
									e.printStackTrace()
									Toast.makeText(activity, R.string.error_email, Toast.LENGTH_LONG).show()
								}
							}
							3 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://getlocalization.com/voicenotify")))
							4 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pilot51/voicenotify")))
						}
					}.create()
				ID.DONATE -> AlertDialog.Builder(activity)
					.setTitle(R.string.donate)
					.setItems(R.array.donate_services) { _, item ->
						when (item) {
							0 -> show(fragmentManager, ID.WALLET)
							1 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.com/cgi-bin/webscr?"
								+ "cmd=_donations&business=pilota51%40gmail%2ecom&lc=US&item_name=Voice%20Notify&"
								+ "no_note=0&no_shipping=1&currency_code=USD")))
						}
					}.create()
				ID.WALLET -> {
					val walletIntent = walletIntent
					val dlg = AlertDialog.Builder(activity)
						.setTitle(R.string.donate_wallet_title)
						.setMessage(R.string.donate_wallet_message)
						.setNegativeButton(android.R.string.cancel, null)
					if (walletIntent != null) {
						dlg.setPositiveButton(R.string.donate_wallet_launch_app) { _, _ ->
							startActivity(walletIntent)
						}
					} else {
						dlg.setPositiveButton(R.string.donate_wallet_launch_web) { _, _ ->
							startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wallet.google.com")))
						}
					}
					dlg.create()
				}
			}
		}

		companion object {
			private const val KEY_ID = "id"
			fun show(fm: FragmentManager, id: ID) {
				val bundle = Bundle()
				bundle.putSerializable(KEY_ID, id)
				val dialogFragment = MyDialog()
				dialogFragment.arguments = bundle
				dialogFragment.show(fm, id.name)
			}
		}
	}
}