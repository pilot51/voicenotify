package com.pilot51.voicenotify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    scrollBehavior: TopAppBarScrollBehavior,
    topBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(VoiceNotifyTheme.colors.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            topBar()
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding)
                .background(VoiceNotifyTheme.colorScheme.background)
        ) {
            content()
        }
    }
}