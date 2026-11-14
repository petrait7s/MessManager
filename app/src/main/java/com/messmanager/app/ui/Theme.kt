package com.messmanager.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Emerald = Color(0xFF10B981)
val EmeraldLight = Color(0xFF34D399)
val Electric = Color(0xFF3B82F6)
val Danger = Color(0xFFF43F5E)
val Amber = Color(0xFFF59E0B)

val LocalReduceMotion = compositionLocalOf { false }
val LocalDark = compositionLocalOf { true }

private val DarkScheme = darkColorScheme(
    primary = EmeraldLight, onPrimary = Color(0xFF04231A), primaryContainer = Color(0xFF0F3D30), onPrimaryContainer = Color(0xFFB7F7DE),
    secondary = Color(0xFF60A5FA), onSecondary = Color(0xFF04162E), tertiary = Amber,
    background = Color(0xFF0A1020), onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF0F172A), onSurface = Color(0xFFE8EEF9),
    surfaceVariant = Color(0xFF18233D), onSurfaceVariant = Color(0xFFA9B8D4),
    outline = Color(0xFF34466B), error = Color(0xFFFB7185),
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF047857), onPrimary = Color.White, primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF022C22),
    secondary = Color(0xFF1D4ED8), onSecondary = Color.White, tertiary = Color(0xFFB45309),
    background = Color(0xFFF2F6FB), onBackground = Color(0xFF0B1426),
    surface = Color.White, onSurface = Color(0xFF0B1426),
    surfaceVariant = Color(0xFFE6ECF6), onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8), error = Color(0xFFBE123C),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun MessTheme(theme: Int, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Type, content = content)
    }
}

@Composable
fun cardBrush(accent: Color = Emerald): Brush {
    val dark = LocalDark.current
    return if (dark) Brush.linearGradient(listOf(Color(0xFF1A2745), Color(0xFF111B33), accent.copy(alpha = 0.14f)))
    else Brush.linearGradient(listOf(Color.White, Color(0xFFF1F5FB), accent.copy(alpha = 0.12f)))
}

val ScreenPad = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
