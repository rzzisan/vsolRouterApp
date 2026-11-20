package com.zyrotech.vsolrouter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * One brand look on every phone (no wallpaper colours), so screens, gradients and status
 * colours always match. Light: soft grey background with white cards. Dark: deep navy.
 */
private val LightBrand = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF0B2475),
    secondary = Color(0xFF0FA5A0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F5F2),
    onSecondaryContainer = Color(0xFF00413E),
    tertiary = Color(0xFF7B4DFF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE5FF),
    onTertiaryContainer = Color(0xFF2C0E86),
    background = Color(0xFFF3F5FA),
    onBackground = Color(0xFF151A26),
    surface = Color(0xFFF3F5FA),
    onSurface = Color(0xFF151A26),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF5B6275),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F2F8),
    surfaceContainerHighest = Color(0xFFE8EBF3),
    outline = Color(0xFF8C93A6),
    outlineVariant = Color(0xFFE1E4EE),
    error = Color(0xFFE5484D),
)

private val DarkBrand = darkColorScheme(
    primary = Color(0xFF9DB1FF),
    onPrimary = Color(0xFF07206E),
    primaryContainer = Color(0xFF1E3A9E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFF5ED8CF),
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF0B4A47),
    onSecondaryContainer = Color(0xFFC7F4EF),
    tertiary = Color(0xFFC6B3FF),
    onTertiary = Color(0xFF2D0E84),
    tertiaryContainer = Color(0xFF3E2596),
    onTertiaryContainer = Color(0xFFEDE5FF),
    background = Color(0xFF0D111A),
    onBackground = Color(0xFFE4E7F0),
    surface = Color(0xFF0D111A),
    onSurface = Color(0xFFE4E7F0),
    surfaceVariant = Color(0xFF242A38),
    onSurfaceVariant = Color(0xFFA6ADBF),
    surfaceContainerLowest = Color(0xFF0A0D14),
    surfaceContainerLow = Color(0xFF151A25),
    surfaceContainer = Color(0xFF171C28),
    surfaceContainerHigh = Color(0xFF1F2533),
    surfaceContainerHighest = Color(0xFF2A303F),
    outline = Color(0xFF6E768A),
    outlineVariant = Color(0xFF262C3A),
    error = Color(0xFFFF6B70),
)

val Good = Color(0xFF1FA463)
val Warn = Color(0xFFF2A100)
val Bad = Color(0xFFE5484D)

/** The brand gradient used on hero cards, the login header and the main buttons. */
val BrandGradient: Brush
    @Composable get() = Brush.linearGradient(listOf(Color(0xFF2F5BEA), Color(0xFF6A4CFF), Color(0xFF8E44FF)))

private val AppType = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun VsolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkBrand else LightBrand,
        typography = AppType,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
