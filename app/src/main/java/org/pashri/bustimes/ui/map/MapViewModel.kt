package org.pashri.bustimes.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pashri.bustimes.data.location.CoarseLocationProvider
import org.pashri.bustimes.data.location.CoarsePosition
import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.prefs.CameraStore
import org.pashri.bustimes.data.parse.DeparturesParseException
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.ui.selection.SelectionState

/** Everything the map screen renders. */
data class MapUiState(
    val camera: CameraState? = null,
    val decorations: MapDecorations = MapDecorations(),
    val selection: SelectionState = SelectionState.None,
    val loadingVehicles: Boolean = false,
    val offline: Boolean = false,
    /** Set when the user asks to be located and a position is found. */
    val moveCameraTo: CoarsePosition? = null,
) {
    /** True when the zoom is too low to fetch vehicles, so a hint is shown. */
    val zoomedOutForVehicles: Boolean get() = camera != null && !camera.showsVehicles
}

/**
 * Drives the map: polling, selection and camera.
 *
 * Vehicle polling follows bustimes.org's own scheme. The next poll is
 * scheduled after the previous response lands rather than on a fixed timer,
 * so a slow network throttles itself instead of queueing requests, and a
 * viewport already covered by the last fetch is not refetched at all.
 */
class MapViewModel(
    private val repository: BustimesRepository,
    private val cameraStore: CameraStore,
    private val locationProvider: CoarseLocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())

    /** The map screen's state. */
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val highWaterMark = VehicleHighWaterMark()
    private var viewport: BoundingBox? = null
    private var pollJob: Job? = null
    private var stopsJob: Job? = null
    private var selectionJob: Job? = null

    /** The last loaded trip, kept so going back to a bus need not refetch it. */
    private var lastTrip: Trip? = null
    private var fetchedStopsFor: BoundingBox? = null

    /**
     * Set once something has explicitly asked for a camera position.
     *
     * Auto-centring on the user's location resolves asynchronously, so without
     * this it can land after an explicit request and quietly undo it.
     */
    private var hasExplicitTarget = false

    /**
     * Whether a camera position was restored from a previous session.
     *
     * A remembered camera is the user's own last view and must not be
     * overridden by auto-centring, or every launch would drag them back to
     * wherever they happen to be standing.
     */
    private var hadRememberedCamera = false

    init {
        viewModelScope.launch {
            val stored = cameraStore.read()
            hadRememberedCamera = stored != null
            _state.update { it.copy(camera = stored ?: CameraState.UnitedKingdom) }
            if (!hadRememberedCamera) {
                centreOnUserIfPermitted()
            }
        }
    }

    /**
     * Records a settled camera and decides what to fetch.
     *
     * @param camera the new camera position.
     * @param bounds the viewport's bounding box.
     * @param userGesture true when the move came from the user rather than code.
     */
    fun onCameraIdle(camera: CameraState, bounds: BoundingBox, userGesture: Boolean) {
        viewport = bounds
        _state.update { it.copy(camera = camera) }
        viewModelScope.launch { cameraStore.write(camera) }
        refreshStops(camera, bounds)
        if (!camera.showsVehicles) {
            stopPolling()
            return
        }
        val needsFetch = highWaterMark.needsFetch(bounds)
        startPolling(initialDelayMillis = if (needsFetch) MapDefaults.PAN_DEBOUNCE_MILLIS else MapDefaults.VEHICLE_POLL_MILLIS)
        if (needsFetch && userGesture) {
            _state.update { it.copy(loadingVehicles = true) }
        }
    }

    /** Starts or resumes polling; called when the screen becomes visible. */
    fun onResumed() {
        val camera = _state.value.camera ?: return
        if (camera.showsVehicles) {
            startPolling(initialDelayMillis = 0)
        }
    }

    /**
     * Stops all polling.
     *
     * Called when the screen is no longer visible. There is no background
     * polling at all: a network wake every twelve seconds would be ruinous for
     * battery, and this app is for a quick check rather than to sit open.
     */
    fun onPaused() {
        stopPolling()
    }

    /**
     * Selects a bus and loads its schedule and route.
     *
     * @param vehicleId the tracked vehicle to select.
     */
    fun onVehicleSelected(vehicleId: Long) {
        val vehicle = _state.value.decorations.vehicles.firstOrNull { it.id == vehicleId }
        _state.update { current ->
            current.copy(
                selection = SelectionState.Bus(vehicleId = vehicleId, vehicle = vehicle),
                decorations = current.decorations.copy(
                    selectedVehicleId = vehicleId,
                    selectedStopAtco = null,
                    routeDimmed = false,
                ),
            )
        }
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch { loadTrip(vehicleId, vehicle?.tripId) }
    }

    /**
     * Selects a stop and loads its departure board.
     *
     * Any route already drawn is dimmed rather than removed: the stop was
     * tapped because of that route, so throwing it away would discard the
     * reason the user was looking.
     *
     * @param atcoCode the stop to select.
     */
    fun onStopSelected(atcoCode: String) {
        val name = _state.value.decorations.stops
            .firstOrNull { it.atcoCode == atcoCode }?.properties?.name
        _state.update { current ->
            current.copy(
                selection = SelectionState.Stop(atcoCode = atcoCode, name = name),
                decorations = current.decorations.copy(
                    selectedStopAtco = atcoCode,
                    routeDimmed = current.decorations.routeLegs.isNotEmpty(),
                ),
            )
        }
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch { loadDepartures(atcoCode) }
    }

    /**
     * Resolves a service slug to its numeric id and opens the timetable.
     *
     * The departure board links services by slug, but the timetable and
     * geometry endpoints are keyed by numeric id, so a lookup is needed.
     *
     * @param slug the service slug from a departure board.
     * @param onResolved called with the numeric id once known.
     */
    fun onServiceSlugRequested(slug: String, onResolved: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                repository.serviceIdsBySlug(listOf(slug))[slug]?.let(onResolved)
            } catch (error: IOException) {
                // Nothing to open; leave the panel as it is.
            }
        }
    }

    /**
     * Clears the current selection.
     *
     * Going back from a stop that was reached from a bus restores the bus
     * panel and un-dims its route, rather than dropping to a bare map.
     */
    fun onSelectionDismissed() {
        selectionJob?.cancel()
        val previous = _state.value.selection
        val bus = previous as? SelectionState.Stop
        if (bus != null && _state.value.decorations.routeLegs.isNotEmpty()) {
            restoreBusSelection()
            return
        }
        _state.update { current ->
            current.copy(
                selection = SelectionState.None,
                decorations = current.decorations.copy(
                    selectedVehicleId = null,
                    selectedStopAtco = null,
                    routeLegs = emptyList(),
                    routeStops = emptyList(),
                    routeDimmed = false,
                ),
            )
        }
    }

    private fun restoreBusSelection() {
        val vehicleId = _state.value.decorations.selectedVehicleId
        if (vehicleId == null) {
            _state.update { it.copy(selection = SelectionState.None) }
            return
        }
        _state.update { current ->
            current.copy(
                selection = SelectionState.Bus(
                    vehicleId = vehicleId,
                    trip = lastTrip,
                    vehicle = current.decorations.vehicles.firstOrNull { it.id == vehicleId },
                    loading = false,
                ),
                decorations = current.decorations.copy(
                    selectedStopAtco = null,
                    routeDimmed = false,
                ),
            )
        }
    }

    private suspend fun loadTrip(vehicleId: Long, knownTripId: Long?) {
        val tripId = knownTripId ?: resolveTripId(vehicleId)
        if (tripId == null) {
            markBusFailed(vehicleId)
            return
        }
        try {
            val trip = repository.trip(tripId)
            lastTrip = trip
            applyTrip(vehicleId, trip)
            refreshVehicleDetail(vehicleId, trip)
        } catch (error: IOException) {
            markBusFailed(vehicleId)
        }
    }

    private suspend fun resolveTripId(vehicleId: Long): Long? =
        _state.value.decorations.vehicles.firstOrNull { it.id == vehicleId }?.tripId

    private fun applyTrip(vehicleId: Long, trip: Trip) {
        val times = trip.times ?: emptyList()
        _state.update { current ->
            val selection = current.selection
            if (selection !is SelectionState.Bus || selection.vehicleId != vehicleId) {
                return@update current
            }
            current.copy(
                selection = selection.copy(trip = trip, loading = false),
                decorations = current.decorations.copy(
                    routeLegs = times.mapNotNull { it.track },
                    routeStops = times,
                ),
            )
        }
    }

    /**
     * Fetches the selected vehicle's own record, for delay and progress.
     *
     * The bounding-box poll omits both fields, so they are only available from
     * the filtered form of the endpoint.
     */
    private suspend fun refreshVehicleDetail(vehicleId: Long, trip: Trip) {
        val serviceId = trip.service?.id ?: return
        try {
            val detail = repository.vehicleForTrip(serviceId = serviceId, tripId = trip.id)
                ?: return
            _state.update { current ->
                val selection = current.selection
                if (selection !is SelectionState.Bus || selection.vehicleId != vehicleId) {
                    return@update current
                }
                current.copy(selection = selection.copy(vehicle = detail))
            }
        } catch (error: IOException) {
            // Delay and progress are enrichment; the schedule alone is useful.
        }
    }

    private fun markBusFailed(vehicleId: Long) {
        _state.update { current ->
            val selection = current.selection
            if (selection !is SelectionState.Bus || selection.vehicleId != vehicleId) {
                return@update current
            }
            current.copy(selection = selection.copy(loading = false, failed = true))
        }
    }

    private suspend fun loadDepartures(atcoCode: String) {
        try {
            val board = repository.departures(atcoCode)
            updateStopSelection(atcoCode) { it.copy(board = board, loading = false) }
        } catch (error: IOException) {
            updateStopSelection(atcoCode) { it.copy(loading = false, unreadable = false) }
        } catch (error: DeparturesParseException) {
            // The upstream template has changed shape. Say so rather than
            // showing an empty board, which would look like "no more buses".
            updateStopSelection(atcoCode) { it.copy(loading = false, unreadable = true) }
        }
    }

    private fun updateStopSelection(
        atcoCode: String,
        transform: (SelectionState.Stop) -> SelectionState.Stop,
    ) {
        _state.update { current ->
            val selection = current.selection
            if (selection !is SelectionState.Stop || selection.atcoCode != atcoCode) {
                return@update current
            }
            current.copy(selection = transform(selection))
        }
    }

    /** Requests a fresh position and asks the map to move there. */
    fun onLocateRequested() {
        viewModelScope.launch {
            val position = locationProvider.lastKnown() ?: locationProvider.current() ?: return@launch
            hasExplicitTarget = true
            _state.update { it.copy(moveCameraTo = position) }
        }
    }

    /**
     * Moves the camera to an arbitrary position.
     *
     * Used by the debug launch extras to open the map somewhere specific
     * without a real location fix, and the natural hook for opening on a
     * favourite stop once favourites exist.
     *
     * @param latitude target latitude in degrees.
     * @param longitude target longitude in degrees.
     */
    fun moveTo(latitude: Double, longitude: Double) {
        hasExplicitTarget = true
        _state.update { it.copy(moveCameraTo = CoarsePosition(latitude, longitude)) }
    }

    /** Acknowledges a camera move so it is not repeated. */
    fun onCameraMoveHandled() {
        _state.update { it.copy(moveCameraTo = null) }
    }

    /**
     * Called once the user grants location permission.
     *
     * Granting permission is not itself a request to be taken somewhere, so
     * this only centres the map when there was no remembered position to
     * honour and nothing else has asked for a specific view.
     */
    fun onLocationPermissionGranted() {
        if (hadRememberedCamera || hasExplicitTarget) return
        viewModelScope.launch { centreOnUserIfPermitted() }
    }

    private suspend fun centreOnUserIfPermitted() {
        if (hasExplicitTarget) return
        val position = locationProvider.lastKnown() ?: return
        _state.update { it.copy(moveCameraTo = position) }
    }

    private fun startPolling(initialDelayMillis: Long) {
        // Cancelling first is what stops a pan from stacking requests, and is
        // the equivalent of the AbortController the web map uses.
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            delay(initialDelayMillis)
            while (true) {
                pollVehiclesOnce()
                delay(MapDefaults.VEHICLE_POLL_MILLIS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(loadingVehicles = false) }
    }

    private suspend fun pollVehiclesOnce() {
        val bounds = viewport ?: return
        try {
            val vehicles = repository.vehiclesInBox(bounds)
            highWaterMark.record(bounds, vehicles.size)
            applyVehicles(vehicles)
        } catch (error: IOException) {
            _state.update { it.copy(loadingVehicles = false, offline = true) }
        }
    }

    private fun applyVehicles(vehicles: List<Vehicle>) {
        _state.update { current ->
            current.copy(
                loadingVehicles = false,
                offline = false,
                decorations = current.decorations.copy(vehicles = vehicles),
            )
        }
    }

    private fun refreshStops(camera: CameraState, bounds: BoundingBox) {
        if (!camera.showsStops) {
            fetchedStopsFor = null
            _state.update { it.copy(decorations = it.decorations.copy(stops = emptyList())) }
            return
        }
        if (fetchedStopsFor?.contains(bounds) == true) return
        stopsJob?.cancel()
        stopsJob = viewModelScope.launch { fetchStops(bounds) }
    }

    private suspend fun fetchStops(bounds: BoundingBox) {
        try {
            val stops = repository.stopsInBox(bounds)
            fetchedStopsFor = bounds
            applyStops(stops)
        } catch (error: IOException) {
            _state.update { it.copy(offline = true) }
        }
    }

    private fun applyStops(stops: List<StopFeature>) {
        _state.update { current ->
            current.copy(decorations = current.decorations.copy(stops = stops))
        }
    }

    /** Creates [MapViewModel] instances with their dependencies. */
    class Factory(
        private val repository: BustimesRepository,
        private val cameraStore: CameraStore,
        private val locationProvider: CoarseLocationProvider,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(repository, cameraStore, locationProvider) as T
    }
}
