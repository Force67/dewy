package dev.dewy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.dewy.R

@Immutable
data class Palette(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val hairline: Color,
    val dew: Color,
    val dewDeep: Color,
    val dewWash: Color,
    val onDew: Color,
    val amber: Color,
    val coral: Color,
    val red: Color,
    val slate: Color,
    val code: Color,
    val isDark: Boolean,
)

private val Light = Palette(
    ground = Color(0xFFF3F5F7),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    ink = Color(0xFF0C1417),
    inkSoft = Color(0xFF5E6A72),
    inkFaint = Color(0xFF9EA8AF),
    hairline = Color(0xFFE2E6E9),
    dew = Color(0xFF0E9AA7),
    dewDeep = Color(0xFF087783),
    dewWash = Color(0xFFE3F5F6),
    onDew = Color.White,
    amber = Color(0xFFE8920B),
    coral = Color(0xFFF26A2E),
    red = Color(0xFFE5484D),
    slate = Color(0xFF93A0A8),
    code = Color(0xFFF0F3F5),
    isDark = false,
)

private val Dark = Palette(
    ground = Color(0xFF050607),
    surface = Color(0xFF131719),
    raised = Color(0xFF1B2023),
    ink = Color(0xFFF1F5F7),
    inkSoft = Color(0xFF8E9AA2),
    inkFaint = Color(0xFF5B666D),
    hairline = Color(0xFF262D31),
    dew = Color(0xFF3CCFD8),
    dewDeep = Color(0xFF14A3AE),
    dewWash = Color(0xFF0C2A2D),
    onDew = Color(0xFF02191B),
    amber = Color(0xFFFFB224),
    coral = Color(0xFFFF8552),
    red = Color(0xFFFF6369),
    slate = Color(0xFF6E7B83),
    code = Color(0xFF0D1113),
    isDark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }

private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

/** The type ramp, sized like iOS's so the app reads calm and dense. */
object Type {
    val largeTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.022).em)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.018).em)
    val headline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.011).em)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = (-0.009).em)
    val callout = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.009).em)
    val subhead = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.006).em)
    val footnote = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.003).em)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val sectionLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
fun DewyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.dew, onPrimary = p.onDew, primaryContainer = p.dewWash, onPrimaryContainer = p.dew,
            background = p.ground, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.raised, onSurfaceVariant = p.inkSoft, outline = p.hairline, outlineVariant = p.hairline,
            surfaceContainer = p.surface, surfaceContainerLow = p.surface, surfaceContainerHigh = p.raised,
            surfaceContainerHighest = p.raised, surfaceContainerLowest = p.ground, error = p.red,
        )
    } else {
        lightColorScheme(
            primary = p.dew, onPrimary = p.onDew, primaryContainer = p.dewWash, onPrimaryContainer = p.dewDeep,
            background = p.ground, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.ground, onSurfaceVariant = p.inkSoft, outline = p.hairline, outlineVariant = p.hairline,
            surfaceContainer = p.surface, surfaceContainerLow = p.surface, surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.ground, surfaceContainerLowest = p.surface, error = p.red,
        )
    }
    val base = Typography()
    val typography = Typography(
        displaySmall = Type.largeTitle,
        headlineMedium = Type.largeTitle,
        headlineSmall = Type.title,
        titleLarge = Type.title,
        titleMedium = Type.headline,
        titleSmall = Type.subhead,
        bodyLarge = Type.body,
        bodyMedium = Type.callout,
        bodySmall = Type.footnote,
        labelLarge = Type.subhead.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = Type.caption,
        labelSmall = base.labelSmall.copy(fontFamily = Inter),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
