package org.pashri.bustimes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.pashri.bustimes.di.AppContainer
import org.pashri.bustimes.ui.diagnostics.DiagnosticsScreen
import org.pashri.bustimes.ui.map.MapScreen
import org.pashri.bustimes.ui.favourites.FavouritesMenu
import org.pashri.bustimes.ui.map.MapViewModel
import org.pashri.bustimes.ui.selection.JourneyPanel
import org.pashri.bustimes.ui.selection.SelectionState
import org.pashri.bustimes.ui.selection.StopPanel
import org.pashri.bustimes.ui.timetable.TimetableScreen
import org.pashri.bustimes.ui.timetable.TimetableViewModel

/** Navigation destinations. */
private object Routes {
    const val MAP = "map"
    const val TIMETABLE = "timetable/{serviceId}"
    const val DIAGNOSTICS = "diagnostics"

    fun timetable(serviceId: Long): String = "timetable/$serviceId"
}

/** Collapsed height for a journey: line, destination and how it is running. */
private val JOURNEY_PEEK_HEIGHT = 132.dp

/**
 * Collapsed height for a stop.
 *
 * A departure board is only useful if some departures are actually on screen,
 * so this is tall enough for the stop name plus roughly the next three, rather
 * than the name alone.
 */
private val STOP_PEEK_HEIGHT = 320.dp

/**
 * The collapsed height for whatever is selected.
 *
 * @param selection what the sheet is showing.
 * @return the peek height, or zero when the sheet should be hidden.
 */
private fun peekHeightFor(selection: SelectionState): Dp = when (selection) {
    SelectionState.None -> 0.dp
    is SelectionState.Journey -> JOURNEY_PEEK_HEIGHT
    is SelectionState.Stop -> STOP_PEEK_HEIGHT
}

/**
 * Root of the app's UI.
 *
 * The map's view model is created here rather than inside the map destination
 * so the timetable can select a journey on it before navigating back. That
 * also guarantees one map and one polling loop for the whole app, however the
 * back stack is arranged.
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
    val mapViewModel: MapViewModel = viewModel(factory = container.mapViewModelFactory)

    LaunchedEffect(openAt) {
        if (openAt != null) {
            mapViewModel.moveTo(latitude = openAt.first, longitude = openAt.second)
        }
    }

    NavHost(navController = navController, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapDestination(
                viewModel = mapViewModel,
                darkTheme = darkTheme,
                onTimetableRequested = { navController.navigate(Routes.timetable(it)) },
                onDiagnosticsRequested = { navController.navigate(Routes.DIAGNOSTICS) },
            )
        }
        composable(Routes.DIAGNOSTICS) {
            var log by remember { mutableStateOf(container.crashLog.read()) }
            DiagnosticsScreen(
                log = log,
                onBack = { navController.popBackStack() },
                onClear = {
                    container.crashLog.clear()
                    log = null
                },
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
                onTripSelected = { tripId ->
                    // Journeys live on the map, so opening one returns there
                    // with the route drawn rather than stacking a second map.
                    mapViewModel.onTripSelected(tripId)
                    navController.popBackStack()
                },
            )
        }
    }
}

/**
 * The map, with the selection sheet over it.
 *
 * The sheet is a standard (non-modal) bottom sheet, so the map stays
 * interactive at every height and dragging the sheet down collapses it to
 * peek instead of dismissing the selection. That is what lets a route be
 * followed across the map with its schedule still to hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDestination(
    viewModel: MapViewModel,
    darkTheme: Boolean,
    onTimetableRequested: (Long) -> Unit,
    onDiagnosticsRequested: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val selection = state.selection

    // The sheet's presence is derived from the selection rather than tracked
    // separately, so the two can never disagree.
    LaunchedEffect(selection) {
        if (selection == SelectionState.None) {
            sheetState.hide()
        } else if (sheetState.currentValue == SheetValue.Hidden) {
            sheetState.partialExpand()
        }
    }

    // Dragging the sheet below peek puts it in Hidden, which the sheet does
    // without telling anyone. Treated as a dismissal, because otherwise the
    // selection stays alive with no sheet to show it: the route and the
    // dimming would sit on the map with nothing explaining them and no way
    // to get the panel back.
    LaunchedEffect(sheetState.currentValue, selection) {
        if (sheetState.currentValue == SheetValue.Hidden && selection != SelectionState.None) {
            viewModel.clearSelection()
        }
    }

    // Back means one step out: collapse an expanded sheet, then drop the
    // selection, then let the system handle it.
    BackHandler(enabled = state.hasSelection) {
        if (sheetState.currentValue == SheetValue.Expanded) {
            scope.launch { sheetState.partialExpand() }
        } else {
            viewModel.onBack()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeightFor(selection),
        sheetContent = {
            SelectionContent(
                selection = selection,
                nowMillis = state.decorations.nowMillis,
                onStopClicked = viewModel::onStopSelected,
                onDepartureClicked = viewModel::onDepartureSelected,
                onTimetableRequested = onTimetableRequested,
                onHeaderClicked = viewModel::onRecentreRequested,
                favouriteCodes = state.favouriteCodes,
                onToggleFavourite = viewModel::onToggleFavourite,
                onDeparturesRetryRequested = viewModel::onDeparturesRetryRequested,
            )
        },
    ) {
        MapScreen(
            state = state,
            darkTheme = darkTheme,
            // Shifts the camera's notion of centre up by the collapsed sheet,
            // so a selected bus is never left underneath it.
            bottomInset = peekHeightFor(selection),
            onCameraIdle = viewModel::onCameraIdle,
            onVehicleTapped = viewModel::onVehicleSelected,
            onStopTapped = viewModel::onStopSelected,
            onLocateRequested = viewModel::onLocateRequested,
            onPermissionGranted = viewModel::onLocationPermissionGranted,
            onCameraMoveHandled = viewModel::onCameraMoveHandled,
            onFitRouteHandled = viewModel::onFitRouteHandled,
            onResumed = viewModel::onResumed,
            onPaused = viewModel::onPaused,
            onDiagnosticsRequested = onDiagnosticsRequested,
            onFavouritesToggled = {
                if (state.favouritesMenuOpen) {
                    viewModel.onFavouritesMenuClosed()
                } else {
                    viewModel.onFavouritesMenuOpened()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Drawn over the map rather than inside the sheet, so it expands from
        // its own button and is not clipped by the sheet's bounds.
        FavouritesMenu(
            favourites = state.favouritesMenu,
            visible = state.favouritesMenuOpen,
            bottomPadding = FAVOURITES_MENU_BOTTOM + peekHeightFor(selection),
            onFavouriteClicked = viewModel::onFavouriteSelected,
            onDismiss = viewModel::onFavouritesMenuClosed,
        )
    }
}

/**
 * Space beneath the favourites menu.
 *
 * Clears the two stacked buttons and the margins around them, so the menu's
 * lowest entry sits just above the button that opened it.
 */
private val FAVOURITES_MENU_BOTTOM = 148.dp

@Composable
private fun SelectionContent(
    selection: SelectionState,
    nowMillis: Long,
    onStopClicked: (String) -> Unit,
    onDepartureClicked: (Long?, Long?) -> Unit,
    onTimetableRequested: (Long) -> Unit,
    onHeaderClicked: () -> Unit,
    favouriteCodes: Set<String>,
    onToggleFavourite: () -> Unit,
    onDeparturesRetryRequested: () -> Unit,
) {
    when (selection) {
        SelectionState.None -> Unit
        is SelectionState.Journey -> JourneyPanel(
            journey = selection,
            nowMillis = nowMillis,
            onStopClicked = onStopClicked,
            onLineClicked = onTimetableRequested,
            onHeaderClicked = onHeaderClicked,
        )
        is SelectionState.Stop -> StopPanel(
            stop = selection,
            onDepartureClicked = onDepartureClicked,
            isFavourite = selection.atcoCode in favouriteCodes,
            onToggleFavourite = onToggleFavourite,
            onRetryRequested = onDeparturesRetryRequested,
        )
    }
}

@Composable
private fun TimetableDestination(
    container: AppContainer,
    serviceId: Long,
    onBack: () -> Unit,
    onTripSelected: (Long) -> Unit,
) {
    val viewModel: TimetableViewModel = viewModel(
        factory = container.timetableViewModelFactory(serviceId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimetableScreen(
        state = state,
        onBack = onBack,
        onGroupingSelected = viewModel::onGroupingSelected,
        onJourneyClicked = { journey ->
            state.tripIdFor(journey)?.let(onTripSelected)
        },
        onRetry = viewModel::onRetry,
    )
}
