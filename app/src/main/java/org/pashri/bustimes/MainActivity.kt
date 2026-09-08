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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BustimesApplication).container
        setContent {
            val dark = isSystemInDarkTheme()
            BustimesTheme(darkTheme = dark) {
                BustimesApp(container = container, darkTheme = dark)
            }
        }
    }
}
