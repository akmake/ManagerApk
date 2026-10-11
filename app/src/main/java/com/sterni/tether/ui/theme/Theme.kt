package com.sterni.tether.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sterni.tether.R

@OptIn(ExperimentalTextApi::class)
val UiFont = FontFamily(
    Font(R.font.assistant, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.assistant, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.assistant, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.assistant, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)
val ReadingHebrew = FontFamily(Font(R.font.sbl_hbrw))
// Legacy names now share the interface font; reading content has its own family above.
val SblHebrew = UiFont
val BaHaYetzira = UiFont
val HebrewFont = UiFont

val Primary = Color(0xFF345DDD)
val PrimaryLight = Color(0xFF5576E6)
val Ink = Color(0xFF171717)
val Muted = Color(0xFF6F6F6F)
val BgColor = Color.White
val CardBg = Color(0xFFF6F6F6)
val LineColor = Color(0xFFE9E9E9)
val Amber = Color(0xFFB8790B)
val TetherBlue = Primary
val TetherInk = Ink
val TetherMuted = Muted

object TetherLayout {
    val pagePadding = 20.dp
    val rowPadding = 16.dp
    val sectionGap = 24.dp
    val cardShape = RoundedCornerShape(24.dp)
    val controlShape = RoundedCornerShape(18.dp)
}

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = UiFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

val AppTypography = Typography(
    displayLarge = type(40, 48, FontWeight.SemiBold),
    displayMedium = type(34, 42, FontWeight.SemiBold),
    displaySmall = type(30, 38, FontWeight.SemiBold),
    headlineLarge = type(28, 36, FontWeight.SemiBold),
    headlineMedium = type(26, 34, FontWeight.SemiBold),
    headlineSmall = type(24, 32, FontWeight.SemiBold),
    titleLarge = type(20, 28, FontWeight.SemiBold),
    titleMedium = type(18, 26, FontWeight.Medium),
    titleSmall = type(16, 24, FontWeight.Medium),
    bodyLarge = type(17, 26), bodyMedium = type(16, 24), bodySmall = type(14, 21),
    labelLarge = type(16, 22, FontWeight.Medium), labelMedium = type(14, 20, FontWeight.Medium),
    labelSmall = type(12, 18, FontWeight.Medium)
)

private val LightPalette = lightColorScheme(
    primary = Primary, onPrimary = Color.White,
    primaryContainer = CardBg, onPrimaryContainer = Ink,
    secondary = Primary, onSecondary = Color.White,
    secondaryContainer = CardBg, onSecondaryContainer = Ink,
    tertiary = Primary, onTertiary = Color.White,
    tertiaryContainer = CardBg, onTertiaryContainer = Ink,
    background = BgColor, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = CardBg, onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White, surfaceContainerLow = CardBg,
    surfaceContainer = CardBg, surfaceContainerHigh = CardBg,
    surfaceContainerHighest = Color(0xFFEEEEEE),
    outline = Color(0xFFCBCBCB), outlineVariant = LineColor,
    error = Color(0xFFCB3541), onError = Color.White,
    errorContainer = Color(0xFFFFF0F1), onErrorContainer = Color(0xFF7C2028)
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF9DB2FF), onPrimary = Color(0xFF102258),
    primaryContainer = Color(0xFF25262C), onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFF9DB2FF), onSecondary = Color(0xFF102258),
    secondaryContainer = Color(0xFF25262C), onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFF9DB2FF), tertiaryContainer = Color(0xFF25262C),
    onTertiaryContainer = Color(0xFFF5F5F5),
    background = Color(0xFF17181B), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF17181B), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF25262C), onSurfaceVariant = Color(0xFFBABBC3),
    surfaceContainerLowest = Color(0xFF17181B), surfaceContainerLow = Color(0xFF25262C),
    surfaceContainer = Color(0xFF25262C), surfaceContainerHigh = Color(0xFF2E3037),
    surfaceContainerHighest = Color(0xFF35373F), outline = Color(0xFF666874),
    outlineVariant = Color(0xFF3B3D46), error = Color(0xFFFF9DA4)
)

@Composable
fun TetherTheme(darkTheme: Boolean = false, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    // A fixed palette is intentional, so controls look consistent on every managed device.
    val palette = if (darkTheme) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = palette, typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = palette.background) { content() }
        }
    }
}
