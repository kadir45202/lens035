package com.lens035.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lens035.app.R

/**
 * Lens035 design tokens. A true-black, flat interface where colour belongs to the photo:
 * chrome stays neutral, one warm "halation" accent marks the selected thing and the primary
 * action, and film stocks carry their own identity tints.
 */
object LensColors {
    val Ground = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0F)
    val Raised = Color(0xFF18181A)
    val Active = Color(0xFF26262A)
    val Line = Color(0xFF232326)
    val Outline = Color(0xFF3B3B3F)
    val Text = Color(0xFFF3F1ED)
    val Muted = Color(0xFFB5B3AF)
    val Faint = Color(0xFF8F8D89)
    val Accent = Color(0xFFFF6A2B)
    val AccentInk = Color(0xFF1A0900)
    val AccentSoft = Color(0x33FF6A2B)
    val Positive = Color(0xFF4CC584)
    val Danger = Color(0xFFFF5D52)
    val Scrim = Color(0xB3000000)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun sans(weight: Int) = Font(
    R.font.instrument_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun mono(weight: Int) = Font(
    R.font.jetbrains_mono,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

object LensFonts {
    val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700))
    val Mono = FontFamily(mono(400), mono(500), mono(700))
}

/** Type scale: 11 / 12 / 14 / 16 / 20 / 28. Numbers that change use tabular figures. */
object LensType {
    val Meta = TextStyle(fontFamily = LensFonts.Sans, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
    val Label = TextStyle(fontFamily = LensFonts.Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(fontFamily = LensFonts.Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Title = TextStyle(fontFamily = LensFonts.Sans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Heading = TextStyle(fontFamily = LensFonts.Sans, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val Display = TextStyle(fontFamily = LensFonts.Sans, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
    val Value = TextStyle(fontFamily = LensFonts.Mono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val Code = TextStyle(fontFamily = LensFonts.Mono, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
}

object LensShapes {
    val Thumb = RoundedCornerShape(10.dp)
    val Card = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Small = RoundedCornerShape(6.dp)
}

private val scheme = darkColorScheme(
    primary = LensColors.Accent,
    onPrimary = LensColors.AccentInk,
    secondary = LensColors.Muted,
    onSecondary = LensColors.Ground,
    background = LensColors.Ground,
    onBackground = LensColors.Text,
    surface = LensColors.Surface,
    onSurface = LensColors.Text,
    surfaceVariant = LensColors.Raised,
    onSurfaceVariant = LensColors.Muted,
    surfaceContainer = LensColors.Surface,
    surfaceContainerHigh = LensColors.Raised,
    surfaceContainerHighest = LensColors.Active,
    outline = LensColors.Outline,
    outlineVariant = LensColors.Line,
    error = LensColors.Danger,
    onError = LensColors.Ground,
    scrim = LensColors.Scrim,
)

@Composable
fun Lens035Theme(content: @Composable () -> Unit) {
    val typography = Typography(
        bodyLarge = LensType.Body,
        bodyMedium = LensType.Body,
        bodySmall = LensType.Label,
        labelLarge = LensType.BodyStrong,
        labelMedium = LensType.Label,
        labelSmall = LensType.Meta,
        titleLarge = LensType.Heading,
        titleMedium = LensType.Title,
        titleSmall = LensType.BodyStrong,
        headlineSmall = LensType.Heading,
        headlineMedium = LensType.Display,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = Shapes(
            small = LensShapes.Small,
            medium = LensShapes.Thumb,
            large = LensShapes.Card,
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
