package com.pilot51.voicenotify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    headlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.let {
            it?.let {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    it()
                }
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 8.dp)
                .bottomBorder(2f, VoiceNotifyTheme.colors.divider)
                .padding(0.dp, 8.dp, 0.dp, 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                headlineContent?.invoke()
                supportingContent?.invoke()
            }
            trailingContent?.invoke()
        }

    }
}