package org.pashri.bustimes.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.pashri.bustimes.di.AppContainer
import org.pashri.bustimes.ui.map.MapScreen
import org.pashri.bustimes.ui.map.MapViewModel

/**
 * Root of the app's UI.
 *
 * The map is the whole screen in this version; the selection sheet and
 * timetable destinations are layered over it as they are built.
 *
 * @param container the application's dependencies.
 * @param darkTheme whether the system is in dark mode, used to pick the map style.
 * @param openAt an optional position to open at, supplied by debug launch extras.
 */
@Composable
fun BustimesApp(
    container: AppContainer,
    darkTheme: Boolean,
    openAt: Pair<Double, Double>? = null,
) {
    val viewModel: MapViewModel = viewModel(factory = container.mapViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(openAt) {
        if (openAt != null) {
            viewModel.moveTo(latitude = openAt.first, longitude = openAt.second)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            state = state,
            darkTheme = darkTheme,
            onCameraIdle = viewModel::onCameraIdle,
            onVehicleTapped = viewModel::onVehicleSelected,
            onStopTapped = viewModel::onStopSelected,
            onLocateRequested = viewModel::onLocateRequested,
            onPermissionGranted = viewModel::onLocationPermissionGranted,
            onCameraMoveHandled = viewModel::onCameraMoveHandled,
            onResumed = viewModel::onResumed,
            onPaused = viewModel::onPaused,
        )
    }
}
