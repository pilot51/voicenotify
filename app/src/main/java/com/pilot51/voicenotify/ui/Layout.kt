package com.pilot51.voicenotify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme


/**
 * A layout with padding and background.
 */
@Composable
fun Layout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoiceNotifyTheme.colors.background)
            .padding(12.dp, 0.dp)
    ) {
        content()
    }
}



