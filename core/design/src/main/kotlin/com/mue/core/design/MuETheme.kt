package com.mue.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Outfit font for display, wordmark, section headers.
 */
val OutfitSemiBold = FontFamily(
    Font(R.font.outfit_semibold, FontWeight.SemiBold)
)

/**
 * MuE-Air typography scale.
 */
val MuETypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.05.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.3.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

private val DarkColorScheme = darkColorScheme(
    primary = SystemAccentBlue,
    onPrimary = PureWhite,
    secondary = SystemAccentBlue,
    onSecondary = PureWhite,
    background = PureBlack,
    onBackground = PureWhite,
    surface = DarkSurface,
    onSurface = PureWhite,
    surfaceVariant = DarkGrey,
    onSurfaceVariant = MutedGrey,
    outline = SurfaceElevated
)

private val SlateColorScheme = darkColorScheme(
    primary = SystemAccentBlue,
    onPrimary = PureWhite,
    secondary = SystemAccentBlue,
    onSecondary = PureWhite,
    background = Color(0xFF16181D),
    onBackground = PureWhite,
    surface = Color(0xFF1F222A),
    onSurface = PureWhite,
    surfaceVariant = Color(0xFF282C37),
    onSurfaceVariant = MutedGrey,
    outline = Color(0xFF383E4D)
)

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = SystemAccentBlue,
    onPrimary = PureWhite,
    secondary = SystemAccentBlue,
    onSecondary = PureWhite,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = PureWhite,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFD1D1D6)
)

@Composable
fun MuETheme(
    darkTheme: Boolean = true,
    theme: String = "Pure Black OLED",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        !darkTheme || theme == "Light Mode" || theme == "Light" -> LightColorScheme
        theme == "Slate" -> SlateColorScheme
        else -> DarkColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MuETypography,
        content = content
    )
}
