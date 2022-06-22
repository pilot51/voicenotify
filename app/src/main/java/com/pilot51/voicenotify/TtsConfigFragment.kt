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

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.pilot51.voicenotify.TextReplacePreference.TextReplaceFragment

class TtsConfigFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
	private val prefs by lazy { Common.getPrefs(requireContext()) }
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

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.preferences_tts)
		val pTTS: Preference = findPreference(getString(R.string.key_ttsSettings))!!
		ttsIntent?.let {
			pTTS.intent = it
		} ?: run {
			pTTS.isEnabled = false
			pTTS.setSummary(R.string.tts_settings_summary_fail)
		}
		val pTtsString: EditTextPreference = findPreference(getString(R.string.key_ttsString))!!
		if (pTtsString.text!!.contains("%")) {
			Toast.makeText(context, R.string.tts_message_reset_default, Toast.LENGTH_LONG).show()
			pTtsString.text = getString(R.string.ttsString_default_value)
		}
	}

	override fun onResume() {
		super.onResume()
		prefs.registerOnSharedPreferenceChangeListener(this)
	}

	override fun onPause() {
		prefs.unregisterOnSharedPreferenceChangeListener(this)
		super.onPause()
	}

	override fun onDisplayPreferenceDialog(preference: Preference) {
		val textReplaceFragmentTag = TextReplaceFragment::class.simpleName
		if (parentFragmentManager.findFragmentByTag(textReplaceFragmentTag) != null) {
			return
		}
		if (preference is TextReplacePreference) {
			val f: DialogFragment = TextReplaceFragment.newInstance(preference.getKey())
			f.setTargetFragment(this, 0)
			f.show(parentFragmentManager, textReplaceFragmentTag)
		} else {
			super.onDisplayPreferenceDialog(preference)
		}
	}

	override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
		if (key == getString(R.string.key_ttsStream)) {
			Common.setVolumeStream(requireActivity())
		}
	}

	private fun checkActivityExist(name: String): Boolean {
		try {
			val pkgInfo = requireContext().packageManager.getPackageInfo(
				name.substring(0, name.lastIndexOf(".")), PackageManager.GET_ACTIVITIES)
			return pkgInfo.activities?.any { it.name == name } ?: false
		} catch (e: PackageManager.NameNotFoundException) {
			e.printStackTrace()
		}
		return false
	}
}
