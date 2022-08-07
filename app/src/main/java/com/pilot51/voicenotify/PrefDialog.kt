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

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.pilot51.voicenotify.PrefDialogID.*

class PrefDialog : DialogFragment() {
	private val prefs by lazy { Common.getPrefs(requireContext()) }

	private val sTimeSetListener = OnTimeSetListener { _, hourOfDay, minute ->
		prefs.edit()
			.putInt(getString(R.string.key_quietStart), hourOfDay * 60 + minute)
			.apply()
	}
	private val eTimeSetListener = OnTimeSetListener { _, hourOfDay, minute ->
		prefs.edit()
			.putInt(getString(R.string.key_quietEnd), hourOfDay * 60 + minute)
			.apply()
	}

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		return when (PrefDialogArgs.fromBundle(requireArguments()).id) {
			DEVICE_STATE -> {
				val items = resources.getStringArray(R.array.device_states)
				AlertDialog.Builder(activity)
					.setTitle(R.string.device_state_dialog_title)
					.setMultiChoiceItems(items, booleanArrayOf(
						prefs.getBoolean(Common.KEY_SPEAK_SCREEN_OFF, true),
						prefs.getBoolean(Common.KEY_SPEAK_SCREEN_ON, true),
						prefs.getBoolean(Common.KEY_SPEAK_HEADSET_OFF, true),
						prefs.getBoolean(Common.KEY_SPEAK_HEADSET_ON, true),
						prefs.getBoolean(Common.KEY_SPEAK_SILENT_ON, false)
					)) { _, which, isChecked ->
						prefs.edit().putBoolean(when (which) {
							0 -> Common.KEY_SPEAK_SCREEN_OFF
							1 -> Common.KEY_SPEAK_SCREEN_ON
							2 -> Common.KEY_SPEAK_HEADSET_OFF
							3 -> Common.KEY_SPEAK_HEADSET_ON
							4 -> Common.KEY_SPEAK_SILENT_ON
							else -> throw IndexOutOfBoundsException()
						}, isChecked).apply()
					}.create()
			}
			QUIET_START -> {
				val quietStart = prefs.getInt(getString(R.string.key_quietStart), 0)
				TimePickerDialog(activity, sTimeSetListener,
					quietStart / 60, quietStart % 60, false)
			}
			QUIET_END -> {
				val quietEnd = prefs.getInt(getString(R.string.key_quietEnd), 0)
				TimePickerDialog(activity, eTimeSetListener,
					quietEnd / 60, quietEnd % 60, false)
			}
			LOG -> AlertDialog.Builder(activity)
				.setTitle(R.string.notify_log)
				.setView(NotifyList(activity))
				.setNeutralButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
				.create()
			SUPPORT -> AlertDialog.Builder(activity)
				.setTitle(R.string.support)
				.setItems(R.array.support_items) { _, item ->
					when (resources.getStringArray(R.array.support_items)[item]) {
						getString(R.string.support_rate) -> {
							val iMarket = Intent(
								Intent.ACTION_VIEW,
								Uri.parse("market://details?id=com.pilot51.voicenotify")
							)
							iMarket.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
							try {
								startActivity(iMarket)
							} catch (e: ActivityNotFoundException) {
								e.printStackTrace()
								Toast.makeText(activity, R.string.error_market, Toast.LENGTH_LONG).show()
							}
						}
						getString(R.string.support_contact) -> {
							val activity = requireActivity()
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
									"${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}"))
							try {
								startActivity(iEmail)
							} catch (e: ActivityNotFoundException) {
								e.printStackTrace()
								Toast.makeText(
									activity, R.string.error_email, Toast.LENGTH_LONG
								).show()
							}
						}
						getString(R.string.support_translations) -> startActivity(
							Intent(
								Intent.ACTION_VIEW,
								Uri.parse("https://hosted.weblate.org/projects/voice-notify")
							)
						)
						getString(R.string.support_source) -> startActivity(
							Intent(
								Intent.ACTION_VIEW,
								Uri.parse("https://github.com/pilot51/voicenotify")
							)
						)
						getString(R.string.support_privacy) -> AlertDialog.Builder(activity)
							.setTitle(R.string.support_privacy)
							.setMessage(R.string.support_privacy_message)
							.setPositiveButton(android.R.string.ok, null)
							.show()
					}
				}.create()
		}
	}
}

enum class PrefDialogID {
	DEVICE_STATE,
	QUIET_START,
	QUIET_END,
	LOG,
	SUPPORT
}
