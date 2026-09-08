package org.pashri.bustimes.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.pashri.bustimes.di.AppContainer

/**
 * Root of the app's UI.
 *
 * @param container the application's dependencies.
 * @param darkTheme whether the system is in dark mode, used to pick the map style.
 */
@Composable
fun BustimesApp(container: AppContainer, darkTheme: Boolean) {
    Text(text = if (darkTheme) "Bustimes (dark)" else "Bustimes")
}
