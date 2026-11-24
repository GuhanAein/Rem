package app.rem.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rem.R

/*
 * Rem Noir: a quiet, premium, dark-first design language.
 *  - Deep black canvas, layered charcoal surfaces lit by a fine highlight along their top edge.
 *  - An editorial serif (Instrument Serif) for moments that matter: greetings, numbers, titles.
 *  - Inter for everything you read and tap.
 *  - Ivory for primary actions, champagne gold for progress and selection, and almost nothing else.
 */

@Immutable
data class RemColors(
    val canvas: Color,
    val surface: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val gold: Color,
    val goldDeep: Color,
    val ivory: Color,
    val onIvory: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean = true,
) {
    /** Hairlines and highlights: white light on dark surfaces, a soft shadow line on light ones. */
    val highlight: Color get() = if (isDark) Color.White else Color.Black

    // Compatibility names used across the app.
    val accent: Color get() = gold
    val card: Color get() = surface

    /** Fine highlight along a surface's top edge, fading downward: the "lit from above" look. */
    val hairline: Brush get() = if (isDark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
        else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.09f)))
    val goldSheen: Brush get() = if (isDark) Brush.linearGradient(listOf(Color(0xFFF1DDAE), gold, goldDeep)) else Brush.linearGradient(listOf(gold, goldDeep))
}

val Noir = RemColors(
    canvas = Color(0xFF09090B),
    surface = Color(0xFF131316),
    elevated = Color(0xFF1B1B1F),
    label = Color(0xFFF3F0E8),
    secondaryLabel = Color(0xFF8E8B84),
    tertiaryLabel = Color(0xFF4E4C48),
    separator = Color(0xFF232327),
    fill = Color(0xFF1E1E22),
    gold = Color(0xFFD9BC83),
    goldDeep = Color(0xFFB08D4F),
    ivory = Color(0xFFF3F0E8),
    onIvory = Color(0xFF0B0B0D),
    danger = Color(0xFFEC7B6B),
    success = Color(0xFF93CFA0),
    warning = Color(0xFFE9B872),
)

/**
 * Rem Ivory: the light counterpart. Warm paper canvas, white cards with a soft edge,
 * ink-black primary buttons, and the accent deepened so it stays readable on white.
 */
val Ivory = RemColors(
    canvas = Color(0xFFF4F2ED),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFFAF9F6),
    label = Color(0xFF1A1916),
    secondaryLabel = Color(0xFF6E6A62),
    tertiaryLabel = Color(0xFFB3AEA4),
    separator = Color(0xFFE8E4DB),
    fill = Color(0xFFECE9E2),
    gold = Color(0xFF94713A),
    goldDeep = Color(0xFF6F5226),
    ivory = Color(0xFF1A1916),
    onIvory = Color(0xFFF7F5F0),
    danger = Color(0xFFC2493A),
    success = Color(0xFF3B8A52),
    warning = Color(0xFFAF7420),
    isDark = false,
)

val LocalRemColors = staticCompositionLocalOf { Noir }

/**
 * The active palette for code that draws outside a composable scope (modifier helpers,
 * category colours). Snapshot state, so anything that reads it recomposes on a theme switch.
 */
object RemPalette {
    private val state = androidx.compose.runtime.mutableStateOf(Noir)
    var current: RemColors
        get() = state.value
        internal set(v) { state.value = v }
    val isLight: Boolean get() = !current.isDark

    /** Set before the first frame so the opening screen never flashes the wrong palette. */
    fun prime(dark: Boolean) { current = if (dark) Noir else Ivory }
}

@Composable
fun isDark(mode: app.rem.data.ThemeMode): Boolean = when (mode) {
    app.rem.data.ThemeMode.DARK -> true
    app.rem.data.ThemeMode.LIGHT -> false
    app.rem.data.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
}

/** Deepens an accent for use as text or icons on a light background. */
fun Color.onLight(amount: Float = 0.38f): Color = androidx.compose.ui.graphics.lerp(this, Color(0xFF1A1916), amount)

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val RemTypography = Typography(
    // Serif display: greetings, hero numbers, screen titles.
    displayLarge = TextStyle(fontFamily = Serif, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Serif, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Serif, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    // Sans UI.
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.1).sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp),
    // Small caps captions: "OVERDUE · 2"
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp),
)

val SectionLabel = RemTypography.labelSmall

val RemShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** Rem Noir (dark) by default; Rem Ivory when [darkTheme] is false. */
@Composable
fun RemTheme(darkTheme: Boolean = true, accent: app.rem.data.Accent = app.rem.data.Accent.GOLD, content: @Composable () -> Unit) {
    val c = if (darkTheme) Noir.copy(gold = Color(accent.argb), goldDeep = Color(accent.deep))
    else Ivory.copy(gold = Color(accent.deep).onLight(0.3f), goldDeep = Color(accent.deep).onLight(0.5f))
    // Publish the palette after composition; writing state mid-composition can leave lazy lists stale.
    androidx.compose.runtime.SideEffect { if (RemPalette.current != c) RemPalette.current = c }
    val base = if (darkTheme) darkColorScheme() else androidx.compose.material3.lightColorScheme()
    val scheme = base.copy(
        primary = c.gold, onPrimary = c.onIvory, primaryContainer = c.elevated, onPrimaryContainer = c.label,
        secondary = c.gold, background = c.canvas, onBackground = c.label, surface = c.surface, onSurface = c.label,
        surfaceVariant = c.elevated, onSurfaceVariant = c.secondaryLabel, surfaceContainer = c.surface,
        surfaceContainerHigh = c.elevated, surfaceContainerLow = c.surface, surfaceContainerHighest = c.elevated,
        surfaceContainerLowest = c.canvas, outline = c.tertiaryLabel, outlineVariant = c.separator,
        error = c.danger, scrim = Color.Black, inverseSurface = c.ivory, inverseOnSurface = c.onIvory,
        surfaceTint = Color.Transparent,
    )
    CompositionLocalProvider(LocalRemColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = RemTypography, shapes = RemShapes, content = content)
    }
}

object Rem {
    val colors: RemColors @Composable get() = LocalRemColors.current
}
