/*
 * Copyright 2011-2024 Mark Injerd
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
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.ui.Layout
import com.pilot51.voicenotify.ui.ListBox
import com.pilot51.voicenotify.ui.overScrollVertical
import com.pilot51.voicenotify.ui.rememberOverscrollFlingBehavior
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme

@Composable
fun TtsConfigScreen(vm: IPreferencesViewModel) {
    val context = LocalContext.current
    val configApp by vm.configuringAppState.collectAsState()
    val settings by vm.configuringSettingsState.collectAsState()
    var ttsEnabled by remember { mutableStateOf(true) }
    var ttsSummary by remember { mutableStateOf("") }
    var ttsIntent by remember { mutableStateOf<Intent?>(null) }
    Intent("com.android.settings.TTS_SETTINGS").let {
        if (it.resolveActivity(context.packageManager) != null) {
            ttsIntent = it
            ttsSummary = stringResource(R.string.tts_settings_summary)
        } else {
            ttsEnabled = false
            ttsSummary = stringResource(R.string.tts_settings_summary_fail)
        }
    }
    var showTtsMessage by remember { mutableStateOf(false) }
    var showTextReplaceDialog by remember { mutableStateOf(false) }
    var showMaxMessage by remember { mutableStateOf(false) }
    var showTtsStream by remember { mutableStateOf(false) }
    var showTtsDelay by remember { mutableStateOf(false) }
    var showTtsRepeat by remember { mutableStateOf(false) }
    var scrollState = rememberScrollState()

    Layout(
        modifier = Modifier
            .padding(12.dp, 0.dp)
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(state = scrollState, flingBehavior = rememberOverscrollFlingBehavior { scrollState })
    ) {
        ListBox(
            modifier = Modifier
        ) {
            if (settings.isGlobal) {
                PreferenceRowLink(
                    titleRes = R.string.tts_settings,
                    summary = ttsSummary,
                    enabled = ttsEnabled,
                    onClick = { ttsIntent?.let { context.startActivity(it) } }
                )
            }
            PreferenceRowLink(
                titleRes = R.string.tts_message,
                summaryRes = R.string.tts_message_summary,
                app = configApp,
                showRemove = !settings.isGlobal && settings.ttsString != null,
                onRemove = {
                    vm.save(settings.copy(ttsString = null))
                },
                onClick = { showTtsMessage = true }
            )
            PreferenceRowLink(
                titleRes = R.string.tts_text_replace,
                summaryRes = R.string.tts_text_replace_summary,
                app = configApp,
                showRemove = !settings.isGlobal && settings.ttsTextReplace != null,
                onRemove = {
                    vm.save(settings.copy(ttsTextReplace = null))
                },
                onClick = { showTextReplaceDialog = true }
            )
            PreferenceRowLink(
                titleRes = R.string.max_length,
                summaryRes = R.string.max_length_summary,
                app = configApp,
                showRemove = !settings.isGlobal && settings.ttsMaxLength != null,
                onRemove = {
                    vm.save(settings.copy(ttsMaxLength = null))
                },
                onClick = { showMaxMessage = true }
            )
            PreferenceRowLink(
                titleRes = R.string.tts_stream,
                summaryRes = R.string.tts_stream_summary,
                app = configApp,
                showRemove = !settings.isGlobal && settings.ttsStream != null,
                onRemove = {
                    vm.save(settings.copy(ttsStream = null))
                },
                onClick = { showTtsStream = true }
            )
            PreferenceRowLink(
                titleRes = R.string.tts_delay,
                summaryRes = R.string.tts_delay_summary,
                app = configApp,
                showRemove = !settings.isGlobal && settings.ttsDelay != null,
                onRemove = {
                    vm.save(settings.copy(ttsDelay = null))
                },
                onClick = { showTtsDelay = true }
            )
            PreferenceRowLink(
                titleRes = R.string.tts_repeat,
                summaryRes = R.string.tts_repeat_summary,
                app = configApp,
                showRemove = !settings.isGlobal && settings.ttsRepeat != null,
                onRemove = {
                    vm.save(settings.copy(ttsRepeat = null))
                },
                onClick = { showTtsRepeat = true }
            )
        }
    }
    if (showTtsMessage) {
        TtsMessageDialog(vm) { showTtsMessage = false }
    }
    if (showTextReplaceDialog) {
        TextReplaceDialog(vm) { showTextReplaceDialog = false }
    }
    if (showMaxMessage) {
        TtsMaxLengthDialog(vm) { showMaxMessage = false }
    }
    if (showTtsStream) {
        TtsStreamDialog(vm) { showTtsStream = false }
    }
    if (showTtsDelay) {
        TtsDelayDialog(vm) { showTtsDelay = false }
    }
    if (showTtsRepeat) {
        TtsRepeatDialog(vm) { showTtsRepeat = false }
    }
}

@VNPreview
@Composable
private fun TtsConfigScreenPreview() {
    AppTheme {
        TtsConfigScreen(PreferencesPreviewVM)
    }
}
