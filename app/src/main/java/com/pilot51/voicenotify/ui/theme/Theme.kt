package com.pilot51.voicenotify.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    // background color
    background = Color(0xFF010101),
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    surface = Color(0xFFf2f3f8),


    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val lightExtendedColors = ExtendedColors(
    neutralSurface = Color(0x99FFFFFF),
    colorOnCustom = Color(0xFFFFFFFF),
    colorOnCustomVariant = Color(0xB3FFFFFF),
    colorSurface1 = Color(0xFFF2F5F9),
    colorSurface2 = Color(0xFFEDF0F6),
    colorSurface3 = Color(0xFFE8ECF4),
    colorSurface4 = Color(0xFFE6EAF3),
    colorSurface5 = Color(0xFFE3E7F1),
    colorTransparent1 = Color(0x14FFFFFF),
    colorTransparent2 = Color(0x29FFFFFF),
    colorTransparent3 = Color(0x8FFFFFFF),
    colorTransparent4 = Color(0xB8FFFFFF),
    colorTransparent5 = Color(0xF5FFFFFF),
    colorNeutral = Color(0xFFFFFFFF),
    colorNeutralVariant = Color(0xB8FFFFFF),
    colorTransparentInverse1 = Color(0x0A000000),
    colorTransparentInverse2 = Color(0x14000000),
    colorTransparentInverse3 = Color(0x66000000),
    colorTransparentInverse4 = Color(0xB8000000),
    colorTransparentInverse5 = Color(0xE0000000),
    colorNeutralInverse = Color(0xFF121212),
    colorNeutralVariantInverse = Color(0xFF5C5C5C)
)

private val darkExtendedColors = ExtendedColors(
    neutralSurface = Color(0x14FFFFFF),
    colorOnCustom = Color(0xFFFFFFFF),
    colorOnCustomVariant = Color(0xB3FFFFFF),
    colorSurface1 = Color(0xFF23242A),
    colorSurface2 = Color(0xFF272A31),
    colorSurface3 = Color(0xFF2C2F37),
    colorSurface4 = Color(0xFF2E3039),
    colorSurface5 = Color(0xFF31343E),
    colorTransparent1 = Color(0x0AFFFFFF),
    colorTransparent2 = Color(0x1FFFFFFF),
    colorTransparent3 = Color(0x29FFFFFF),
    colorTransparent4 = Color(0x7AFFFFFF),
    colorTransparent5 = Color(0xB8FFFFFF),
    colorNeutral = Color(0xFF121212),
    colorNeutralVariant = Color(0xFF5C5C5C),
    colorTransparentInverse1 = Color(0x0A000000),
    colorTransparentInverse2 = Color(0x14000000),
    colorTransparentInverse3 = Color(0x29000000),
    colorTransparentInverse4 = Color(0xB8000000),
    colorTransparentInverse5 = Color(0xF5000000),
    colorNeutralInverse = Color(0xE0FFFFFF),
    colorNeutralVariantInverse = Color(0xA3FFFFFF)
)

@Composable
fun VoicenotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val extendedColors = if (darkTheme) darkExtendedColors else lightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}


object VoicenotifyTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}


