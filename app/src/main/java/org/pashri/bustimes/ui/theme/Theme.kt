package org.pashri.bustimes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** bustimes.org's own accent yellow, used for selection and highlights. */
val BustimesYellow = Color(0xFFFFFF9E)

private val LightColours = lightColorScheme(
    primary = Color(0xFF1B5E20),
    secondary = Color(0xFF37474F),
    surface = Color(0xFFFAFAFA),
    background = Color(0xFFFFFFFF),
)

private val DarkColours = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFB0BEC5),
    surface = Color(0xFF1C1C1E),
    background = Color(0xFF121212),
)

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
        content = content,
    )
}
