package org.pashri.bustimes.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.pashri.bustimes.di.AppContainer
import org.pashri.bustimes.ui.map.MapScreen
import org.pashri.bustimes.ui.map.MapViewModel
import org.pashri.bustimes.ui.selection.BusPanel
import org.pashri.bustimes.ui.selection.SelectionState
import org.pashri.bustimes.ui.selection.StopPanel
import org.pashri.bustimes.ui.timetable.TimetableScreen
import org.pashri.bustimes.ui.timetable.TimetableViewModel

/** Navigation destinations. */
private object Routes {
    const val MAP = "map"
    const val TIMETABLE = "timetable/{serviceId}"

    fun timetable(serviceId: Long): String = "timetable/$serviceId"
}

/**
 * Root of the app's UI.
 *
 * The map is a destination that stays composed, with the selection sheet
 * layered over it, while the timetable is a full screen of its own: a wide
 * timetable needs the whole width, and the map is not useful behind it.
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
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapDestination(
                container = container,
                darkTheme = darkTheme,
                openAt = openAt,
                onTimetableRequested = { navController.navigate(Routes.timetable(it)) },
            )
        }
        composable(
            route = Routes.TIMETABLE,
            arguments = listOf(navArgument("serviceId") { type = NavType.LongType }),
        ) { entry ->
            val serviceId = entry.arguments?.getLong("serviceId") ?: 0L
            TimetableDestination(
                container = container,
                serviceId = serviceId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDestination(
    container: AppContainer,
    darkTheme: Boolean,
    openAt: Pair<Double, Double>?,
    onTimetableRequested: (Long) -> Unit,
) {
    val viewModel: MapViewModel = viewModel(factory = container.mapViewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
        val selection = state.selection
        if (selection != SelectionState.None) {
            ModalBottomSheet(
                onDismissRequest = viewModel::onSelectionDismissed,
                sheetState = sheetState,
            ) {
                SelectionContent(
                    selection = selection,
                    onStopClicked = viewModel::onStopSelected,
                    onTimetableRequested = onTimetableRequested,
                    onServiceSlugClicked = { slug ->
                        viewModel.onServiceSlugRequested(slug, onTimetableRequested)
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionContent(
    selection: SelectionState,
    onStopClicked: (String) -> Unit,
    onTimetableRequested: (Long) -> Unit,
    onServiceSlugClicked: (String) -> Unit,
) {
    when (selection) {
        SelectionState.None -> Unit
        is SelectionState.Bus -> BusPanel(
            bus = selection,
            onStopClicked = onStopClicked,
            onLineClicked = onTimetableRequested,
        )
        is SelectionState.Stop -> StopPanel(
            stop = selection,
            onDepartureClicked = { /* opening a trip from the board comes next */ },
            onLineClicked = onServiceSlugClicked,
        )
    }
}

@Composable
private fun TimetableDestination(
    container: AppContainer,
    serviceId: Long,
    onBack: () -> Unit,
) {
    val viewModel: TimetableViewModel = viewModel(
        factory = container.timetableViewModelFactory(serviceId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimetableScreen(
        state = state,
        onBack = onBack,
        onGroupingSelected = viewModel::onGroupingSelected,
        onJourneyClicked = { /* opening a journey's live schedule comes next */ },
        onRetry = viewModel::onRetry,
    )
}
