package com.pilot51.voicenotify.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.ui.theme.VoicenotifyTheme

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    text: String,
    placeholderText: String,
    onValueChange: (String) -> Unit,
) {
    val view = LocalView.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = VoicenotifyTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.background(
                color = VoicenotifyTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = onValueChange,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = VoicenotifyTheme.colorScheme.onSurfaceVariant
                    )
                },
                placeholder = { Text(text = placeholderText) },
                modifier = Modifier.weight(1f).height(40.dp),
                contentDescription = stringResource(id = R.string.test),
                singleLine = true
            )
        }
    }
}

@Preview
@Composable
private fun SearchBarPreviewEmpty() {
    var text by remember { mutableStateOf("") }
    AppTheme {
        Surface {
            SearchBar(
                text = text,
                placeholderText = "test",
            ) { text = it }
        }

    }
}

@Preview
@Composable
private fun SearchBarPreview() {
    var text by remember { mutableStateOf("some thing") }
    AppTheme {
        Surface {
            SearchBar(
                text = text,
                placeholderText = "test",
            ) { text = it }
        }

    }
}

