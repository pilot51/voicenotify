package com.pilot51.voicenotify.ui
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.ui.theme.VoicenotifyTheme


@Composable
fun SwitchCustom(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = VoicenotifyTheme.colors.colorOnCustom,
            uncheckedThumbColor = VoicenotifyTheme.colors.colorThumbOffCustom,
            checkedTrackColor = VoicenotifyTheme.colors.colorPrimary,
            uncheckedTrackColor = VoicenotifyTheme.colors.colorOffCustom,
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent
        ),
        modifier = (modifier?: Modifier).focusable(false),

    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SwitchCustomPreview() {
    AppTheme {
        var isChecked by remember { mutableStateOf(false) }
        SwitchCustom(
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
        SwitchCustom(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}
