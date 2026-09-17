package com.curbme.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CurbMeExtendedColors(
    val bgDeep: Color = BgDeep,
    val bgCard: Color = BgCard,
    val bgElevated: Color = BgElevated,
    val bgOverlay: Color = BgOverlay,
    val accentBlue: Color = AccentBlue,
    val accentCyan: Color = AccentCyan,
    val accentSky: Color = AccentSky,
    val accentViolet: Color = AccentViolet,
    val accentPink: Color = AccentPink,
    val accentAmber: Color = AccentAmber,
    val accentRed: Color = AccentRed,
    val accentGreen: Color = AccentGreen,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textSubtle: Color = TextSubtle,
    val textMuted: Color = TextMuted,
    val divider: Color = DividerColor,
    val glassBg: Color = GlassBg,
    val glassBorder: Color = GlassBorder
)

object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
}

object AppShapes {
    val small = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val cardLarge = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(28.dp)
}

val LocalCurbMeColors = staticCompositionLocalOf { CurbMeExtendedColors() }

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentCyan,
    tertiary = AccentSky,
    background = BgDeep,
    surface = BgCard,
    surfaceVariant = BgElevated,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    outline = DividerColor
)

object CurbMeTheme {
    val colors: CurbMeExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCurbMeColors.current

    val spacing: Spacing
        get() = Spacing

    val shapes: AppShapes
        get() = AppShapes

    @Composable
    operator fun invoke(content: @Composable () -> Unit) {
        val extendedColors = CurbMeExtendedColors()
        CompositionLocalProvider(LocalCurbMeColors provides extendedColors) {
            MaterialTheme(
                colorScheme = DarkColors,
                content = content
            )
        }
    }
}
