package dev.xenoncolt.jellyflix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JellyflixColorScheme = darkColorScheme(
    primary = Tokens.Brand500,
    onPrimary = Color.White,
    primaryContainer = Tokens.Brand500,
    onPrimaryContainer = Color.White,
    inversePrimary = Tokens.Brand300,

    secondary = Tokens.Series500,
    onSecondary = Color.White,
    secondaryContainer = Tokens.Ink700,
    onSecondaryContainer = Tokens.Mist200,

    tertiary = Tokens.Accent500,
    onTertiary = Tokens.Ink950,

    background = Tokens.Ink950,
    onBackground = Tokens.Mist200,

    surface = Tokens.Ink900,
    onSurface = Tokens.Mist200,
    surfaceVariant = Tokens.Ink800,
    onSurfaceVariant = Tokens.Mist400,
    surfaceContainerLowest = Tokens.Ink950,
    surfaceContainerLow = Tokens.Ink900,
    surfaceContainer = Tokens.Ink850,
    surfaceContainerHigh = Tokens.Ink800,
    surfaceContainerHighest = Tokens.Ink700,

    outline = Tokens.Ink600,
    outlineVariant = Tokens.Divider,

    error = Tokens.Danger500,
    onError = Color.White,

    scrim = Color(0xCC000000),
)

private val JellyflixShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    // Handoff: cards and surfaces use a 12px radius.
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * Page background: a radial highlight at the top centre over a vertical
 * `#16223c -> ink-900 -> ink-950` ramp.
 *
 * A Brush rather than stacked Boxes, so it's one draw call and doesn't
 * invalidate while content scrolls over it.
 */
fun pageBackgroundBrush(size: Size): Brush {
    val vertical = Brush.verticalGradient(
        0f to Tokens.GradientMid,
        0.42f to Tokens.Ink900,
        1f to Tokens.Ink950,
        startY = 0f,
        endY = size.height,
    )
    return vertical
}

/** The radial highlight layered on top of [pageBackgroundBrush]. */
fun pageHighlightBrush(size: Size): Brush = Brush.radialGradient(
    0f to Tokens.GradientHighlight.copy(alpha = 0.55f),
    1f to Color.Transparent,
    center = Offset(size.width / 2f, size.height * 0.02f),
    radius = size.width.coerceAtLeast(1f) * 0.95f,
)

/** Card surface fill: ink-800 at ~82% over the page, per the handoff. */
val SurfaceFill: Color = Tokens.Ink800.copy(alpha = 0.82f)

@Composable
fun JellyflixTheme(
    // Parameter exists so previews/tests can force it; the app is always dark.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = JellyflixColorScheme,
        typography = JellyflixTypography,
        shapes = JellyflixShapes,
        content = content,
    )
}
