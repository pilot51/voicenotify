package com.pilot51.voicenotify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.ui.theme.VoicenotifyTheme


@Composable
fun Layout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoicenotifyTheme.colors.background)
            .padding(12.dp, 0.dp)
    ) {
        content()
    }
}



