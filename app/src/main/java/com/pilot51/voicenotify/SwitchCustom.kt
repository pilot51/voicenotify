package com.pilot51.voicenotify
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Composable
fun SwitchCustom(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = CircleShape
) {
    Switch(
        checked = isChecked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = switchColor,
            uncheckedThumbColor = backgroundColor,
            checkedTrackColor = switchColor.copy(alpha = 0.5f),
            uncheckedTrackColor = backgroundColor.copy(alpha = 0.5f)
        ),
        modifier = modifier ?: Modifier
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SwitchCustomPreview() {
    AppTheme {
        var isChecked by remember { mutableStateOf(false) }
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewSwitchCustomChecked() {
    AppTheme {
        var isChecked by remember { mutableStateOf(true) }
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}
