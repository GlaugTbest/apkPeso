package br.com.glaugtbest.gtheday

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF0E5A43), onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F1DB), onPrimaryContainer = Color(0xFF08382B),
    secondaryContainer = Color(0xFFDCE9DD), onSecondaryContainer = Color(0xFF183E30),
    background = Color(0xFFF6F8F3), onBackground = Color(0xFF14261D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14261D),
    surfaceVariant = Color(0xFFE8EEE6), onSurfaceVariant = Color(0xFF40564A),
    outline = Color(0xFF62796A), outlineVariant = Color(0xFFCED9CF)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA2E5C4), onPrimary = Color(0xFF0A3829),
    primaryContainer = Color(0xFF214F3D), onPrimaryContainer = Color(0xFFD1F6E2),
    secondaryContainer = Color(0xFF294637), onSecondaryContainer = Color(0xFFD2E8D9),
    background = Color(0xFF101714), onBackground = Color(0xFFE1EFE6),
    surface = Color(0xFF18231D), onSurface = Color(0xFFE1EFE6),
    surfaceVariant = Color(0xFF27362D), onSurfaceVariant = Color(0xFFB9CFC1),
    outline = Color(0xFF91AA9A), outlineVariant = Color(0xFF3D5246)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-1).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp)
)

@Composable
fun GtheDayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
