package com.zyrotech.vsolrouter.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Brand colours, used on phones without Material You (Android 11 and older). */
private val LightBrand = lightColorScheme(
    primary = Color(0xFF1E5EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF001550),
    secondary = Color(0xFF00A7A0),
    secondaryContainer = Color(0xFFC9F4EF),
    tertiary = Color(0xFF7A4DFF),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceVariant = Color(0xFFE3E6F0),
)

private val DarkBrand = darkColorScheme(
    primary = Color(0xFFB6C6FF),
    onPrimary = Color(0xFF00267D),
    primaryContainer = Color(0xFF0B3BB0),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF6FDCD3),
    secondaryContainer = Color(0xFF00504C),
    tertiary = Color(0xFFCDB9FF),
    background = Color(0xFF0F1117),
    surface = Color(0xFF0F1117),
    surfaceVariant = Color(0xFF2A2E3A),
)

val Good = Color(0xFF1FA463)
val Warn = Color(0xFFF2A100)
val Bad = Color(0xFFE5484D)

@Composable
fun VsolTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        // Material You: follow the colours of the user's wallpaper.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkBrand
        else -> LightBrand
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
