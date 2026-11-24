package app.rem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RemPalette {
    val Indigo = Color(0xFF5B4BFF)
    val Violet = Color(0xFFA855F7)
    val IndigoDeep = Color(0xFF1B1446)
    val Coral = Color(0xFFFF6B6B)
    val Amber = Color(0xFFF59E0B)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Sky = Color(0xFF3B82F6)
}

@Immutable
data class RemExtras(
    val heroGradient: Brush,
    val accentGradient: Brush,
    val alarmGradient: Brush,
    val cardShadow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val subtle: Color,
)

val LocalRemExtras = staticCompositionLocalOf {
    RemExtras(
        heroGradient = Brush.linearGradient(listOf(RemPalette.Indigo, RemPalette.Violet)),
        accentGradient = Brush.horizontalGradient(listOf(RemPalette.Indigo, RemPalette.Violet)),
        alarmGradient = Brush.verticalGradient(listOf(RemPalette.IndigoDeep, RemPalette.Indigo, RemPalette.Violet)),
        cardShadow = Color(0x1A3B2FCC),
        success = RemPalette.Green,
        warning = RemPalette.Amber,
        danger = RemPalette.Red,
        subtle = Color(0xFFEEEEF6),
    )
}

private val LightColors = lightColorScheme(
    primary = RemPalette.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E6FF),
    onPrimaryContainer = Color(0xFF1B1446),
    secondary = RemPalette.Violet,
    onSecondary = Color.White,
    tertiary = RemPalette.Coral,
    background = Color(0xFFF5F5FA),
    onBackground = Color(0xFF14141F),
    surface = Color.White,
    onSurface = Color(0xFF14141F),
    surfaceVariant = Color(0xFFF0F0F7),
    onSurfaceVariant = Color(0xFF6B6B80),
    surfaceContainerLow = Color(0xFFF8F8FC),
    surfaceContainer = Color(0xFFF2F2F8),
    surfaceContainerHigh = Color(0xFFECECF4),
    outline = Color(0xFFD9D9E6),
    outlineVariant = Color(0xFFE8E8F0),
    error = RemPalette.Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2366),
    onPrimaryContainer = Color(0xFFE9E6FF),
    secondary = Color(0xFFC084FC),
    onSecondary = Color(0xFF14141F),
    tertiary = RemPalette.Coral,
    background = Color(0xFF0D0D14),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF17171F),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF20202B),
    onSurfaceVariant = Color(0xFF9C9CB3),
    surfaceContainerLow = Color(0xFF14141C),
    surfaceContainer = Color(0xFF1B1B25),
    surfaceContainerHigh = Color(0xFF23232F),
    outline = Color(0xFF34344A),
    outlineVariant = Color(0xFF2A2A3A),
    error = Color(0xFFF87171),
)

private val base = Typography()
private val font = FontFamily.SansSerif

val RemTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = font, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
    displayMedium = base.displayMedium.copy(fontFamily = font, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = font, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = font),
    bodyMedium = base.bodyMedium.copy(fontFamily = font),
    bodySmall = base.bodySmall.copy(fontFamily = font),
    labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = font, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = font, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

val SectionLabel = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp)

val RemShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun RemTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extras = if (darkTheme) {
        RemExtras(
            heroGradient = Brush.linearGradient(listOf(Color(0xFF4A3BE0), Color(0xFF8B3FD9))),
            accentGradient = Brush.horizontalGradient(listOf(Color(0xFF6D5BFF), Color(0xFFA855F7))),
            alarmGradient = Brush.verticalGradient(listOf(Color(0xFF0B0820), RemPalette.IndigoDeep, Color(0xFF4A3BE0))),
            cardShadow = Color(0x40000000),
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            danger = Color(0xFFF87171),
            subtle = Color(0xFF20202B),
        )
    } else {
        LocalRemExtras.current
    }
    CompositionLocalProvider(LocalRemExtras provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = RemTypography,
            shapes = RemShapes,
            content = content,
        )
    }
}

object Rem {
    val extras: RemExtras @Composable get() = LocalRemExtras.current
}
