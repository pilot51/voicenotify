/*
 * Copyright 2011-2025 Mark Injerd
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
package com.pilot51.voicenotify.ui.dialog.tts

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.IPreferencesViewModel
import com.pilot51.voicenotify.ui.dialog.TextEditDialog

@Composable
fun TtsMaxLengthDialog(
	vm: IPreferencesViewModel,
	onDismiss: () -> Unit
) {
	val settings by vm.configuringSettingsState.collectAsState()
	val settingsCombo by vm.configuringSettingsComboState.collectAsState()
	TextEditDialog(
		titleRes = R.string.max_length,
		message = stringResource(R.string.max_length_dialog_msg, TextToSpeech.getMaxSpeechInputLength()),
		initialText = settingsCombo.ttsMaxLength?.takeIf { it > 0 }?.toString() ?: "",
		keyboardType = KeyboardType.Number,
		onDismiss = onDismiss
	) {
		vm.save(settings.copy(ttsMaxLength = it.toIntOrNull() ?: -1))
		true
	}
}
