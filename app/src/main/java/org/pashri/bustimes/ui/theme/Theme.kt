package org.pashri.bustimes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** bustimes.org's own accent yellow, reserved for map selection and highlights. */
val BustimesYellow = Color(0xFFFFFF9E)

/**
 * The line-badge / live-status accent.
 *
 * Kept separate from [BustimesYellow] so the two never compete: yellow marks
 * a selection on the map, this marks a line number wherever it appears.
 */
private val TransitBlue = Color(0xFF2D5DF0)
private val TransitBlueLight = Color(0xFF9FB2FF)

private val LightColours = lightColorScheme(
    primary = Color(0xFF1B5E20),
    secondary = Color(0xFF37474F),
    tertiary = TransitBlue,
    onTertiary = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    background = Color(0xFFFFFFFF),
)

private val DarkColours = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFB0BEC5),
    tertiary = TransitBlueLight,
    onTertiary = Color(0xFF00186B),
    surface = Color(0xFF1C1C1E),
    background = Color(0xFF121212),
)

/**
 * Shape scale for cards and badges.
 *
 * A single, more generous corner radius than Material3's defaults, applied
 * consistently to the line badge, panel cards and the favourites menu.
 */
val BustimesShapes: Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

private val BustimesTypography: Typography = Typography().let { base ->
    base.copy(
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * Applies the app theme, following the system light/dark setting.
 *
 * The map style is switched alongside this, so the basemap and the app chrome
 * never disagree about which mode they are in.
 *
 * @param darkTheme whether to use the dark palette; defaults to the system setting.
 * @param content the themed content.
 */
@Composable
fun BustimesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (LocalContext.current as? Activity)?.window
        SideEffect {
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColours else LightColours,
        shapes = BustimesShapes,
        typography = BustimesTypography,
        content = content,
    )
}
