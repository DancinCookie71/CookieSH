package com.cookie.sh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cookie.sh.data.prefs.ThemeMode
import com.cookie.sh.feature.settings.SettingsViewModel

val CookiePrimary = Color(0xFF7C4DFF)
val CookieSecondary = Color(0xFF3D5AFE)
val CookieBackground = Color(0xFF0D0D1A)
val CookieSurface = Color(0xFF12122A)
val CookieSurfaceElevated = Color(0xFF171733)
val CookieGreen = Color(0xFF00E676)
val CookieRed = Color(0xFFFF1744)
val CookieYellow = Color(0xFFFFC400)
val CookieTextPrimary = Color(0xFFFFFFFF)
val CookieTextSecondary = Color(0xFFB0B0CC)
val CookieTerminal = Color(0xFF000000)

private fun getDarkColorScheme(primaryColor: Color) = darkColorScheme(
    primary = primaryColor,
    secondary = primaryColor,
    background = CookieBackground,
    surface = CookieSurface,
    surfaceVariant = CookieSurfaceElevated,
    onPrimary = CookieTextPrimary,
    onSecondary = CookieTextPrimary,
    onBackground = CookieTextPrimary,
    onSurface = CookieTextPrimary,
    onSurfaceVariant = CookieTextSecondary,
    tertiary = CookieGreen,
    error = CookieRed,
    onError = CookieTextPrimary,
)

private fun getLightColorScheme(primaryColor: Color) = lightColorScheme(
    primary = primaryColor,
    secondary = primaryColor,
)

private val CookieTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = CookieTextSecondary,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

private val CookieShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

val TerminalTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    color = CookieGreen,
)

@Composable
fun CookieShTheme(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val accentColorHex by settingsViewModel.accentColor.collectAsState()

    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val primaryColor = Color(android.graphics.Color.parseColor(accentColorHex))

    val colorScheme = if (darkTheme) {
        getDarkColorScheme(primaryColor)
    } else {
        getLightColorScheme(primaryColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CookieTypography,
        shapes = CookieShapes,
        content = content,
    )
}
