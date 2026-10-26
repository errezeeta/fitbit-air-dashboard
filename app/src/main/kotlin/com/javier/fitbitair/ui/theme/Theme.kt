package com.javier.fitbitair.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javier.fitbitair.R

/**
 * The app owns its palette instead of borrowing the wallpaper's.
 *
 * Dynamic colour made every screen take the phone's Material You scheme, which
 * is why the app had no identity of its own and clashed with its icon. These
 * two schemes are the brand: deep petrol surfaces, one mint accent that matches
 * the launcher mark, and a warm sand reserved for sleep so rest and activity
 * never share a colour.
 */
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF4FE3C1),
    onPrimary = Color(0xFF04231C),
    primaryContainer = Color(0xFF0E3B33),
    onPrimaryContainer = Color(0xFFB9F7E7),
    secondary = Color(0xFF86C7DC),
    onSecondary = Color(0xFF05262F),
    secondaryContainer = Color(0xFF123543),
    onSecondaryContainer = Color(0xFFC5ECF6),
    tertiary = Color(0xFFD3B173),
    onTertiary = Color(0xFF2A1E05),
    tertiaryContainer = Color(0xFF3A3020),
    onTertiaryContainer = Color(0xFFF5E3BE),
    background = Color(0xFF07121A),
    onBackground = Color(0xFFE6F0EE),
    surface = Color(0xFF07121A),
    onSurface = Color(0xFFE6F0EE),
    surfaceVariant = Color(0xFF1D3442),
    onSurfaceVariant = Color(0xFFA8BDC4),
    surfaceContainerLowest = Color(0xFF030A10),
    surfaceContainerLow = Color(0xFF0D1F29),
    surfaceContainer = Color(0xFF122833),
    surfaceContainerHigh = Color(0xFF18313D),
    surfaceContainerHighest = Color(0xFF1E3A47),
    outline = Color(0xFF3C5560),
    outlineVariant = Color(0xFF22323B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6F0EE),
    inverseOnSurface = Color(0xFF0B1A24),
    inversePrimary = Color(0xFF0E7C6B),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF0E7C6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFF3E4),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF1F6A80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4E8F2),
    onSecondaryContainer = Color(0xFF001F27),
    tertiary = Color(0xFF7A5A1E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E3BE),
    onTertiaryContainer = Color(0xFF271A00),
    background = Color(0xFFF7FAF9),
    onBackground = Color(0xFF101B1D),
    surface = Color(0xFFF7FAF9),
    onSurface = Color(0xFF101B1D),
    surfaceVariant = Color(0xFFDBE5E4),
    onSurfaceVariant = Color(0xFF3F4A4C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F6F5),
    surfaceContainer = Color(0xFFEAF1F0),
    surfaceContainerHigh = Color(0xFFE3EBEA),
    surfaceContainerHighest = Color(0xFFDBE5E4),
    outline = Color(0xFF6F7B7D),
    outlineVariant = Color(0xFFC3CFCE),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF101B1D),
    inverseOnSurface = Color(0xFFF1F6F5),
    inversePrimary = Color(0xFF4FE3C1),
)

private fun numeric(
    base: TextStyle,
    size: Int,
    lineHeight: Int,
    tracking: Double,
    weight: FontWeight = FontWeight.SemiBold,
): TextStyle = base.copy(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    // Tabular figures: a counter that changes from 1 to 8 must not shift the
    // row it sits in. Proportional digits are the difference between a readout
    // that feels crafted and one that twitches.
    fontFeatureSettings = "tnum",
)

private fun text(
    base: TextStyle,
    size: Int,
    lineHeight: Int,
    tracking: Double,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = base.copy(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val AppTypography: Typography = with(Typography()) {
    Typography(
        displayLarge = numeric(displayLarge, 52, 58, -1.2),
        displayMedium = numeric(displayMedium, 44, 50, -1.0),
        displaySmall = numeric(displaySmall, 36, 42, -0.8),
        headlineLarge = numeric(headlineLarge, 32, 38, -0.6),
        headlineMedium = numeric(headlineMedium, 28, 34, -0.5),
        headlineSmall = numeric(headlineSmall, 22, 28, -0.3),
        titleLarge = numeric(titleLarge, 20, 26, -0.2, FontWeight.SemiBold),
        titleMedium = numeric(titleMedium, 16, 22, -0.1, FontWeight.SemiBold),
        titleSmall = text(titleSmall, 14, 20, 0.0, FontWeight.Medium),
        bodyLarge = text(bodyLarge, 16, 24, 0.0),
        bodyMedium = text(bodyMedium, 14, 20, 0.0),
        bodySmall = text(bodySmall, 13, 18, 0.05),
        labelLarge = text(labelLarge, 14, 20, 0.1, FontWeight.Medium),
        // Kickers are uppercase and letter-spaced; keeping the tracking in the
        // style means screens stop re-declaring it per Text.
        labelMedium = text(labelMedium, 12, 16, 0.9, FontWeight.SemiBold),
        labelSmall = text(labelSmall, 11, 14, 0.7, FontWeight.Medium),
    )
}

/** One rounded family, three steps. Anything else is a bug, not a choice. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FitbitAirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) BrandDark else BrandLight,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
