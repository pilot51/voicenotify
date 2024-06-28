package com.pilot51.voicenotify.ui
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch as OSwitch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme


@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    OSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = VoiceNotifyTheme.colors.colorOnCustom,
            uncheckedThumbColor = VoiceNotifyTheme.colors.colorThumbOffCustom,
            checkedTrackColor = VoiceNotifyTheme.colors.colorPrimary,
            uncheckedTrackColor = VoiceNotifyTheme.colors.colorOffCustom,
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent
        ),
        modifier = (modifier?: Modifier).scale(scale = 0.75f).focusable(false),
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SwitchPreview() {
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
private fun PreviewSwitchChecked() {
    AppTheme {
        var isChecked by remember { mutableStateOf(true) }
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}
