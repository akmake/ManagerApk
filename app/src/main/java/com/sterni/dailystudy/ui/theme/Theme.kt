package com.sterni.dailystudy.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.sterni.dailystudy.R

val HebrewFont = FontFamily(Font(R.font.sbl_hbrw))
val SblHebrew = HebrewFont
val BaHaYetzira = HebrewFont

// Tether color palette
val TetherBlue        = Color(0xFF1A237E)
val Primary           = TetherBlue
val TetherBlueMid     = Color(0xFF283593)
val TetherBlueLight   = Color(0xFF3F51B5)
val TetherGreen       = Color(0xFF2E7D32)
val TetherGreenLight  = Color(0xFF43A047)
val TetherOrange      = Color(0xFFE65100)
val TetherRed         = Color(0xFFB71C1C)
val TetherBg          = Color(0xFFF5F5F5)
val TetherSurface     = Color(0xFFFFFFFF)
val TetherInk         = Color(0xFF212121)
val Ink               = TetherInk
val TetherMuted       = Color(0xFF757575)
val Muted             = TetherMuted
val TetherLine        = Color(0xFFE0E0E0)

private val LightColorScheme = lightColorScheme(
    primary             = TetherBlue,
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFFE8EAF6),
    onPrimaryContainer  = TetherBlue,
    secondary           = TetherGreen,
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFE8F5E9),
    onSecondaryContainer= TetherGreen,
    background          = TetherBg,
    onBackground        = TetherInk,
    surface             = TetherSurface,
    onSurface           = TetherInk,
    surfaceVariant      = Color(0xFFF5F5F5),
    onSurfaceVariant    = TetherMuted,
    outline             = TetherLine,
    error               = TetherRed,
    onError             = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary             = Color(0xFF7986CB),
    onPrimary           = Color(0xFF1A237E),
    primaryContainer    = Color(0xFF283593),
    onPrimaryContainer  = Color(0xFFE8EAF6),
    secondary           = Color(0xFF66BB6A),
    onSecondary         = Color(0xFF1B5E20),
    background          = Color(0xFF121212),
    onBackground        = Color(0xFFE0E0E0),
    surface             = Color(0xFF1E1E1E),
    onSurface           = Color(0xFFE0E0E0),
    surfaceVariant      = Color(0xFF2C2C2C),
    onSurfaceVariant    = Color(0xFFBDBDBD),
    outline             = Color(0xFF424242),
    error               = Color(0xFFEF9A9A),
    onError             = Color(0xFF7F0000)
)

val AppTypography = Typography(
    displayLarge   = TextStyle(fontFamily = HebrewFont),
    displayMedium  = TextStyle(fontFamily = HebrewFont),
    displaySmall   = TextStyle(fontFamily = HebrewFont),
    headlineLarge  = TextStyle(fontFamily = HebrewFont),
    headlineMedium = TextStyle(fontFamily = HebrewFont),
    headlineSmall  = TextStyle(fontFamily = HebrewFont),
    titleLarge     = TextStyle(fontFamily = HebrewFont),
    titleMedium    = TextStyle(fontFamily = HebrewFont),
    titleSmall     = TextStyle(fontFamily = HebrewFont),
    bodyLarge      = TextStyle(fontFamily = HebrewFont),
    bodyMedium     = TextStyle(fontFamily = HebrewFont),
    bodySmall      = TextStyle(fontFamily = HebrewFont),
    labelLarge     = TextStyle(fontFamily = HebrewFont),
    labelMedium    = TextStyle(fontFamily = HebrewFont),
    labelSmall     = TextStyle(fontFamily = HebrewFont)
)

@Composable
fun DailyStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
