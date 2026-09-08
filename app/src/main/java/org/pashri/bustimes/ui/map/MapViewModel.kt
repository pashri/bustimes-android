package org.pashri.bustimes.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.pashri.bustimes.data.location.LocationProvider
import org.pashri.bustimes.data.location.DevicePosition
import org.pashri.bustimes.data.model.RouteGeometry
import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.parse.DeparturesParseException
import org.pashri.bustimes.data.prefs.CameraStore
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.ui.selection.SelectionState

/** Everything the map screen renders. */
data class MapUiState(
    val camera: CameraState? = null,
    val decorations: MapDecorations = MapDecorations(),
    val selection: SelectionState = SelectionState.None,
    val loadingVehicles: Boolean = false,
    val offline: Boolean = false,
    /** Set when something asks the camera to move to a position. */
    val moveCameraTo: DevicePosition? = null,
    /** Set when the camera should frame the whole selected route. */
    val fitRoute: Boolean = false,
) {
    /** True when the zoom is too low to fetch vehicles, so a hint is shown. */
    val zoomedOutForVehicles: Boolean get() = camera != null && !camera.showsVehicles

    /** True when a journey or stop is selected, so the sheet has content. */
    val hasSelection: Boolean get() = selection != SelectionState.None
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
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())

    /** The map screen's state. */
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val highWaterMark = VehicleHighWaterMark()
    private var viewport: BoundingBox? = null
    private var pollJob: Job? = null
    private var stopsJob: Job? = null
    private var selectionJob: Job? = null
    private var pinnedJob: Job? = null
    private var fetchedStopsFor: BoundingBox? = null

    /** Bbox vehicles, before the pinned selection is merged in. */
    private var bboxVehicles: List<Vehicle> = emptyList()

    /**
     * The selected journey's own vehicle, polled separately.
     *
     * It is kept outside [bboxVehicles] and merged on top, so the selected bus
     * stays on the map when the user pans away from it and its delay stays
     * current — neither of which the bounding-box poll can provide.
     */
    private var pinnedVehicle: Vehicle? = null

    /**
     * Geometry for trips other buses on the focused service are running.
     *
     * Cached by trip id and never invalidated, because a trip's route does
     * not change while it is running — only the bus's position along it does.
     */
    private val siblingLegsByTrip = mutableMapOf<Long, List<List<List<Double>>>>()
    private var siblingJob: Job? = null

    /** The journey to return to when backing out of a stop opened from one. */
    private var previousJourney: SelectionState.Journey? = null

    /**
     * Set once something has explicitly asked for a camera position.
     *
     * Auto-centring on the user's location resolves asynchronously, so without
     * this it can land after an explicit request and quietly undo it.
     */
    private var hasExplicitTarget = false


    init {
        viewModelScope.launch {
            // The remembered camera is only there to give the first frame
            // something real to show: a fix can take seconds, and a grey
            // rectangle for that long reads as a broken app. The map then
            // moves to where the user actually is.
            val stored = cameraStore.read()
            _state.update { it.copy(camera = stored ?: CameraState.UnitedKingdom) }
            centreOnUser()
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
        startPolling(
            initialDelayMillis = if (needsFetch) {
                MapDefaults.PAN_DEBOUNCE_MILLIS
            } else {
                MapDefaults.VEHICLE_POLL_MILLIS
            },
        )
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
        restartPinnedPolling()
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
        pinnedJob?.cancel()
        pinnedJob = null
    }

    /**
     * Selects the journey a tapped bus is running.
     *
     * @param vehicleId the tracked vehicle that was tapped.
     */
    fun onVehicleSelected(vehicleId: Long) {
        val vehicle = drawnVehicles().firstOrNull { it.id == vehicleId }
        val tripId = vehicle?.tripId
        if (tripId == null) {
            // A tracked bus with no trip cannot be resolved to a schedule.
            return
        }
        selectJourney(tripId = tripId, vehicle = vehicle, frameRoute = false)
    }

    /**
     * Selects a journey by trip id, from a departure board or a timetable.
     *
     * @param tripId the journey to show.
     */
    fun onTripSelected(tripId: Long) {
        selectJourney(tripId = tripId, vehicle = null, frameRoute = true)
    }

    /**
     * Opens a departure from a stop's board.
     *
     * A tracked departure is linked by journey id rather than trip id, so it
     * needs resolving before there is a schedule to show. At a busy stop every
     * departure can be tracked, which previously left the whole board inert.
     *
     * @param tripId the departure's trip, when it has one.
     * @param journeyId the departure's journey, when it is tracked.
     */
    fun onDepartureSelected(tripId: Long?, journeyId: Long?) {
        if (tripId != null) {
            onTripSelected(tripId)
            return
        }
        if (journeyId == null) return
        viewModelScope.launch {
            val resolved = try {
                repository.tripIdForJourney(journeyId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
            resolved?.let(::onTripSelected)
        }
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
        previousJourney = _state.value.selection as? SelectionState.Journey
        _state.update { current ->
            current.copy(
                selection = SelectionState.Stop(atcoCode = atcoCode, name = stopName(atcoCode)),
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
     * Clears the current selection, one layer at a time.
     *
     * Backing out of a stop that was reached from a journey restores the
     * journey and un-dims its route, rather than dropping straight to a bare
     * map and discarding the route the user was following.
     *
     * @return true when something was cleared, so back was consumed.
     */
    fun onBack(): Boolean {
        val selection = _state.value.selection
        if (selection is SelectionState.Stop && previousJourney != null) {
            restoreJourney()
            return true
        }
        if (selection == SelectionState.None) {
            return false
        }
        clearSelection()
        return true
    }

    /** Drops the selection and everything drawn for it. */
    fun clearSelection() {
        selectionJob?.cancel()
        pinnedJob?.cancel()
        pinnedJob = null
        siblingJob?.cancel()
        siblingJob = null
        pinnedVehicle = null
        previousJourney = null
        _state.update { current ->
            current.copy(
                selection = SelectionState.None,
                decorations = current.decorations.withoutSelection().copy(
                    vehicles = bboxVehicles,
                ),
            )
        }
    }

    /**
     * Recentres on the selected journey.
     *
     * Bound to a tap on the sheet header, which is the natural way back to a
     * bus after panning along its route.
     */
    fun onRecentreRequested() {
        val vehicle = pinnedVehicle ?: (_state.value.selection as? SelectionState.Journey)?.vehicle
        if (vehicle != null) {
            _state.update {
                it.copy(moveCameraTo = DevicePosition(vehicle.latitude, vehicle.longitude))
            }
            return
        }
        if (_state.value.decorations.routeLegs.isNotEmpty()) {
            _state.update { it.copy(fitRoute = true) }
        }
    }

    /** Acknowledges a route-framing request so it is not repeated. */
    fun onFitRouteHandled() {
        _state.update { it.copy(fitRoute = false) }
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Nothing to open; leave the panel as it is.
            }
        }
    }

    /**
     * Moves the map to where the user is now.
     *
     * The cached position is used first so the button responds immediately,
     * then a fresh fix is requested and the camera moved again if it turns out
     * somewhere else. Preferring the cache outright, as this did, meant the
     * button kept returning to whatever stale fix the app started with.
     */
    fun onLocateRequested() {
        hasExplicitTarget = true
        viewModelScope.launch {
            val cached = locationProvider.lastKnown()
            if (cached != null) {
                _state.update { it.copy(moveCameraTo = cached) }
            }
            val fresh = locationProvider.current() ?: return@launch
            if (cached == null || fresh.isFurtherThanAStopFrom(cached)) {
                _state.update { it.copy(moveCameraTo = fresh) }
            }
        }
    }

    /** Centres on the user once permission has just been granted. */
    fun onLocationPermissionGranted() {
        viewModelScope.launch { centreOnUser() }
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
        _state.update { it.copy(moveCameraTo = DevicePosition(latitude, longitude)) }
    }

    /** Acknowledges a camera move so it is not repeated. */
    fun onCameraMoveHandled() {
        _state.update { it.copy(moveCameraTo = null) }
    }

    private fun selectJourney(tripId: Long, vehicle: Vehicle?, frameRoute: Boolean) {
        previousJourney = null
        pinnedVehicle = vehicle
        _state.update { current ->
            current.copy(
                selection = SelectionState.Journey(tripId = tripId, vehicle = vehicle),
                decorations = current.decorations.copy(
                    selectedVehicleId = vehicle?.id,
                    selectedStopAtco = null,
                    routeDimmed = false,
                ),
            )
        }
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch { loadTrip(tripId, frameRoute) }
    }

    private fun restoreJourney() {
        val journey = previousJourney ?: return
        previousJourney = null
        _state.update { current ->
            current.copy(
                selection = journey.copy(vehicle = pinnedVehicle ?: journey.vehicle),
                decorations = current.decorations.copy(
                    selectedStopAtco = null,
                    routeDimmed = false,
                ),
            )
        }
    }

    private suspend fun loadTrip(tripId: Long, frameRoute: Boolean) {
        try {
            val trip = repository.trip(tripId)
            applyTrip(tripId, trip, frameRoute)
            restartPinnedPolling()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Deliberately broad. A malformed payload from one operator, or
            // anything else unexpected, should leave a message in the sheet
            // rather than taking the whole app down; the crash log keeps the
            // detail so the cause can still be found.
            markJourneyFailed(tripId)
        }
    }

    /**
     * Loads and draws the routes of other buses on the same service.
     *
     * Each sibling runs a different trip, so its shape needs its own request;
     * they are cached by trip id since a route does not change. Failures are
     * silent: sibling lines are context, and losing them costs nothing that
     * the selected route does not already show.
     */
    private fun refreshSiblingRoutes(serviceId: Long, selectedVehicleId: Long?) {
        siblingJob?.cancel()
        siblingJob = viewModelScope.launch {
            val siblings = bboxVehicles.filter { vehicle ->
                vehicle.serviceId == serviceId &&
                    vehicle.id != selectedVehicleId &&
                    vehicle.tripId != null
            }
            for (sibling in siblings.take(MAX_SIBLINGS)) {
                val tripId = sibling.tripId ?: continue
                val legs = siblingLegsByTrip[tripId] ?: fetchSiblingLegs(tripId) ?: continue
                siblingLegsByTrip[tripId] = legs
                addSiblingRoute(serviceId, sibling, legs)
            }
        }
    }

    private suspend fun fetchSiblingLegs(tripId: Long): List<List<List<Double>>>? = try {
        RouteGeometry.forTimes(repository.trip(tripId).times ?: emptyList()).legs
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private fun addSiblingRoute(
        serviceId: Long,
        sibling: Vehicle,
        legs: List<List<List<Double>>>,
    ) {
        if (legs.isEmpty()) return
        _state.update { current ->
            if (current.decorations.focusedServiceId != serviceId) return@update current
            val route = SiblingRoute(
                vehicleId = sibling.id,
                legs = legs,
                colour = MapGeoJson.liveryColour(sibling),
            )
            val kept = current.decorations.siblingRoutes.filterNot { it.vehicleId == route.vehicleId }
            current.copy(decorations = current.decorations.copy(siblingRoutes = kept + route))
        }
    }

    private fun applyTrip(tripId: Long, trip: Trip, frameRoute: Boolean) {
        val times = trip.times ?: emptyList()
        val geometry = RouteGeometry.forTimes(times)
        _state.update { current ->
            val selection = current.selection
            if (selection !is SelectionState.Journey || selection.tripId != tripId) {
                return@update current
            }
            current.copy(
                selection = selection.copy(trip = trip, loading = false),
                fitRoute = current.fitRoute || frameRoute,
                decorations = current.decorations.copy(
                    routeLegs = geometry.legs,
                    routeIsApproximate = geometry.approximate,
                    routeStops = times,
                    focusedServiceId = trip.service?.id,
                ),
            )
        }
        trip.service?.id?.let { serviceId ->
            refreshSiblingRoutes(serviceId, _state.value.decorations.selectedVehicleId)
        }
    }

    private fun markJourneyFailed(tripId: Long) {
        _state.update { current ->
            val selection = current.selection
            if (selection !is SelectionState.Journey || selection.tripId != tripId) {
                return@update current
            }
            current.copy(selection = selection.copy(loading = false, failed = true))
        }
    }

    /**
     * Keeps the selected journey's vehicle fresh.
     *
     * `delay` and `progress` exist only in the filtered form of the vehicles
     * endpoint, so without this the sheet's lateness would be whatever it was
     * at the moment of selection and would never move again.
     */
    private fun restartPinnedPolling() {
        pinnedJob?.cancel()
        val journey = _state.value.selection as? SelectionState.Journey ?: return
        val serviceId = journey.serviceId ?: return
        pinnedJob = viewModelScope.launch {
            while (true) {
                pollPinnedOnce(serviceId = serviceId, tripId = journey.tripId)
                delay(MapDefaults.VEHICLE_POLL_MILLIS)
            }
        }
    }

    private suspend fun pollPinnedOnce(serviceId: Long, tripId: Long) {
        try {
            val vehicle = repository.vehicleForTrip(serviceId = serviceId, tripId = tripId)
            pinnedVehicle = vehicle
            _state.update { current ->
                val selection = current.selection
                if (selection !is SelectionState.Journey || selection.tripId != tripId) {
                    return@update current
                }
                current.copy(
                    selection = selection.copy(vehicle = vehicle, loading = false),
                    decorations = current.decorations.copy(
                        vehicles = mergePinned(bboxVehicles, vehicle),
                        selectedVehicleId = vehicle?.id,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The schedule is still useful; leave the last known vehicle alone.
        }
    }

    private suspend fun loadDepartures(atcoCode: String) {
        try {
            val board = repository.departures(atcoCode)
            updateStopSelection(atcoCode) { it.copy(board = board, loading = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: DeparturesParseException) {
            // The upstream template has changed shape. Say so rather than
            // showing an empty board, which would look like "no more buses".
            updateStopSelection(atcoCode) { it.copy(loading = false, unreadable = true) }
        } catch (error: Exception) {
            updateStopSelection(atcoCode) { it.copy(loading = false, unreadable = false) }
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

    /**
     * Finds a stop's name.
     *
     * Searches the selected route's calling points as well as the stops loaded
     * for the viewport: a stop tapped in a journey's schedule is very often
     * outside the current viewport, or below the zoom at which stops load at
     * all, in which case only the route carries its name.
     */
    private fun stopName(atcoCode: String): String? {
        val decorations = _state.value.decorations
        val onRoute = decorations.routeStops
            .firstOrNull { it.stop.atcoCode == atcoCode }?.stop?.name
        if (!onRoute.isNullOrBlank()) return onRoute
        return decorations.stops
            .firstOrNull { it.atcoCode == atcoCode }?.properties?.name
            ?.takeIf { it.isNotBlank() }
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
            bboxVehicles = vehicles
            applyVehicles()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(loadingVehicles = false, offline = true) }
        }
    }

    private fun applyVehicles() {
        _state.update { current ->
            current.copy(
                loadingVehicles = false,
                offline = false,
                decorations = current.decorations.copy(
                    vehicles = mergePinned(bboxVehicles, pinnedVehicle),
                ),
            )
        }
    }

    /** The vehicles actually drawn: the viewport's, plus the pinned selection. */
    private fun drawnVehicles(): List<Vehicle> = _state.value.decorations.vehicles

    /**
     * Merges the pinned vehicle into the bbox set.
     *
     * The pinned entry wins, because it is the fresher of the two and the only
     * one carrying delay and progress.
     */
    private fun mergePinned(bbox: List<Vehicle>, pinned: Vehicle?): List<Vehicle> {
        if (pinned == null) return bbox
        return bbox.filterNot { it.id == pinned.id } + pinned
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(offline = true) }
        }
    }

    private fun applyStops(stops: List<StopFeature>) {
        _state.update { current ->
            current.copy(decorations = current.decorations.copy(stops = stops))
        }
    }

    /**
     * Moves the map to the user, refining once a fresh fix arrives.
     *
     * A cached fix can be old and some distance away, so it is treated as a
     * first approximation to show something immediately rather than as the
     * answer.
     */
    private suspend fun centreOnUser() {
        if (hasExplicitTarget) return
        val cached = locationProvider.lastKnown()
        if (cached != null) {
            _state.update { it.copy(moveCameraTo = cached) }
        }
        val fresh = locationProvider.current() ?: return
        if (hasExplicitTarget) return
        if (cached == null || fresh.isFurtherThanAStopFrom(cached)) {
            _state.update { it.copy(moveCameraTo = fresh) }
        }
    }

    private companion object {
        /**
         * How many other buses' routes to draw.
         *
         * Each costs a request, and a corridor with more than a handful of
         * lines on it stops being readable anyway.
         */
        const val MAX_SIBLINGS = 6
    }

    /** Creates [MapViewModel] instances with their dependencies. */
    class Factory(
        private val repository: BustimesRepository,
        private val cameraStore: CameraStore,
        private val locationProvider: LocationProvider,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(repository, cameraStore, locationProvider) as T
    }
}
