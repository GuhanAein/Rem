package app.rem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rem's design language: calm and minimal. One accent colour, white grouped cards on a soft grey
 * canvas, hairline separators, generous whitespace, big confident titles. Colour is used only to
 * carry meaning (overdue, done, category glyphs), never as decoration.
 */
@Immutable
data class RemColors(
    val accent: Color,
    val canvas: Color,
    val card: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

private val Light = RemColors(
    accent = Color(0xFF0A72E8),
    canvas = Color(0xFFF2F2F6),
    card = Color(0xFFFFFFFF),
    label = Color(0xFF111114),
    secondaryLabel = Color(0xFF8A8A8F),
    tertiaryLabel = Color(0xFFC2C2C7),
    separator = Color(0xFFE3E3E8),
    fill = Color(0xFFEDEDF1),
    success = Color(0xFF2FB24C),
    warning = Color(0xFFF08C00),
    danger = Color(0xFFE5483F),
)

private val Dark = RemColors(
    accent = Color(0xFF3D95F5),
    canvas = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color(0xFFF5F5F7),
    secondaryLabel = Color(0xFF8E8E93),
    tertiaryLabel = Color(0xFF48484A),
    separator = Color(0xFF2E2E31),
    fill = Color(0xFF2A2A2D),
    success = Color(0xFF3BC55A),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFFF5A4F),
)

val LocalRemColors = staticCompositionLocalOf { Light }

private val font = FontFamily.SansSerif
private val base = Typography()

val RemTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = font, fontWeight = FontWeight.Thin, letterSpacing = (-2).sp),
    displaySmall = base.displaySmall.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    // Large title (34sp bold), used once per screen.
    headlineLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    // Body (17sp regular) is the workhorse for list rows.
    bodyLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    bodySmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.2.sp),
)

/** Small grey caption above grouped sections. */
val SectionLabel = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.2.sp)

val RemShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun RemTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) Dark else Light
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White, primaryContainer = c.fill, onPrimaryContainer = c.label,
            secondary = c.accent, background = c.canvas, onBackground = c.label, surface = c.card, onSurface = c.label,
            surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel, surfaceContainer = c.card,
            surfaceContainerHigh = c.card, surfaceContainerLow = c.card, surfaceContainerHighest = c.fill,
            outline = c.tertiaryLabel, outlineVariant = c.separator, error = c.danger, scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White, primaryContainer = c.fill, onPrimaryContainer = c.label,
            secondary = c.accent, background = c.canvas, onBackground = c.label, surface = c.card, onSurface = c.label,
            surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel, surfaceContainer = c.card,
            surfaceContainerHigh = c.card, surfaceContainerLow = c.card, surfaceContainerHighest = c.fill,
            outline = c.tertiaryLabel, outlineVariant = c.separator, error = c.danger, scrim = Color.Black,
        )
    }
    CompositionLocalProvider(LocalRemColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = RemTypography, shapes = RemShapes, content = content)
    }
}

object Rem {
    val colors: RemColors @Composable get() = LocalRemColors.current
}
