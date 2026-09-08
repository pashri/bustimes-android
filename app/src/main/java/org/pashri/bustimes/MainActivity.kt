package org.pashri.bustimes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import org.pashri.bustimes.ui.BustimesApp
import org.pashri.bustimes.ui.theme.BustimesTheme

/**
 * The single activity.
 *
 * The map is expensive to create and must survive configuration changes, so
 * rotation and UI-mode changes are handled in-process (see the manifest's
 * `configChanges`) rather than by recreating the activity.
 */
class MainActivity : ComponentActivity() {

    /**
     * Reads an optional launch position from the intent, in debug builds only.
     *
     * Verifying the map against real bus data needs the camera somewhere in
     * the UK, and an emulator's location stack cannot always be persuaded to
     * leave its default. Launching with `-e lat 52.2053 -e lon 0.1190` avoids
     * the problem entirely.
     *
     * @return the position to open at, or null when not supplied.
     */
    private fun debugOpenAt(): Pair<Double, Double>? {
        if (!BuildConfig.DEBUG) return null
        val latitude = intent?.getStringExtra("lat")?.toDoubleOrNull() ?: return null
        val longitude = intent?.getStringExtra("lon")?.toDoubleOrNull() ?: return null
        return latitude to longitude
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BustimesApplication).container
        setContent {
            val dark = isSystemInDarkTheme()
            BustimesTheme(darkTheme = dark) {
                BustimesApp(container = container, darkTheme = dark, openAt = debugOpenAt())
            }
        }
    }
}
