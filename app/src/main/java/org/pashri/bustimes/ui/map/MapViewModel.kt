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
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.prefs.CameraStore
import org.pashri.bustimes.data.repo.BustimesRepository

/** Everything the map screen renders. */
data class MapUiState(
    val camera: CameraState? = null,
    val decorations: MapDecorations = MapDecorations(),
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

    /** Selects a vehicle, or clears the selection when [vehicleId] is null. */
    fun onVehicleSelected(vehicleId: Long?) {
        _state.update { current ->
            current.copy(
                decorations = current.decorations.copy(
                    selectedVehicleId = vehicleId,
                    selectedStopAtco = null,
                ),
            )
        }
    }

    /** Selects a stop, dimming any route already drawn rather than clearing it. */
    fun onStopSelected(atcoCode: String?) {
        _state.update { current ->
            current.copy(
                decorations = current.decorations.copy(
                    selectedStopAtco = atcoCode,
                    routeDimmed = atcoCode != null && current.decorations.routeLegs.isNotEmpty(),
                ),
            )
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
