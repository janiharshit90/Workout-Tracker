package com.example.mentzertracker.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.R

/**
 * "Iron": near-black, one acid-lime accent, orange-red reserved for failure / warnings,
 * and a tall condensed display face. Barlow Condensed is bundled (SIL Open Font License).
 */
object Iron {
    val Ink = Color(0xFF0A0A0A)
    val Panel = Color(0xFF151515)
    val Line = Color(0xFF2A2A2A)
    val LineStrong = Color(0xFF3A3A3A)
    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFFA6A6A6)
    val Lime = Color(0xFFD7FF3F)
    val LimeDeep = Color(0xFF2A3300)
    val Fail = Color(0xFFFF5A36)
}

/** Tight corners everywhere: the look is blocky, not bubbly. */
val IronShape = RoundedCornerShape(4.dp)

val DisplayFont = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)

private val Dark = darkColorScheme(
    primary = Iron.Lime, onPrimary = Iron.Ink,
    primaryContainer = Iron.LimeDeep, onPrimaryContainer = Iron.Lime,
    secondary = Iron.Lime, onSecondary = Iron.Ink,
    secondaryContainer = Iron.LimeDeep, onSecondaryContainer = Iron.Lime,
    tertiary = Iron.Fail, onTertiary = Iron.Ink,
    error = Iron.Fail, onError = Iron.Ink,
    errorContainer = Color(0xFF3A1409), onErrorContainer = Color(0xFFFFB4A0),
    background = Iron.Ink, onBackground = Iron.Text,
    surface = Iron.Panel, onSurface = Iron.Text,
    surfaceVariant = Color(0xFF1E1E1E), onSurfaceVariant = Iron.Muted,
    surfaceTint = Color.Transparent,
    outline = Iron.LineStrong, outlineVariant = Iron.Line,
    surfaceContainerLowest = Iron.Ink,
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Iron.Panel,
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF1E1E1E)
)

private fun display(size: Int, weight: FontWeight = FontWeight.Bold, line: Int = size, spacing: Double = 0.0) =
    TextStyle(
        fontFamily = DisplayFont, fontWeight = weight,
        fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp
    )

private val IronTypography = Typography(
    // Condensed faces read smaller than Roboto at the same size, so these run large.
    headlineLarge = display(56, line = 54),
    headlineMedium = display(40, line = 40),
    headlineSmall = display(34, line = 34),
    titleLarge = display(30, line = 32),
    titleMedium = display(22, line = 24, spacing = 0.3),
    titleSmall = display(18, FontWeight.SemiBold, line = 20, spacing = 0.4)
)

private val IronShapes = Shapes(
    extraSmall = IronShape, small = IronShape, medium = IronShape,
    large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun MentzerTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = IronTypography, shapes = IronShapes, content = content)
}
