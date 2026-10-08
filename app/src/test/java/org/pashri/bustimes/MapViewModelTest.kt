package org.pashri.bustimes

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.pashri.bustimes.data.db.FavouriteStop
import org.pashri.bustimes.data.favourites.FavouritesRepository
import org.pashri.bustimes.data.model.StopTime
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.TripService
import org.pashri.bustimes.data.model.TripStop
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.ui.map.CameraState
import org.pashri.bustimes.ui.map.MapDefaults.DEPARTURES_REFRESH_MILLIS
import org.pashri.bustimes.ui.map.MapDefaults.PAN_DEBOUNCE_MILLIS
import org.pashri.bustimes.ui.map.MapDefaults.VEHICLE_POLL_MILLIS
import org.pashri.bustimes.ui.map.MapViewModel
import org.pashri.bustimes.ui.selection.SelectionState

/**
 * Drives [MapViewModel] on virtual time.
 *
 * The polling loops never finish by design, so tests step the scheduler to
 * exact poll boundaries rather than running it until idle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val repository = FakeBustimesRepository()
    private val cameraStore = FakeCameraStore()
    private val favouritesDao = FakeFavouriteStopDao()
    private var clock = 1_000_000L

    private val city = BoundingBox(west = 0.0, south = 52.0, east = 0.2, north = 52.3)
    private val insideCity = BoundingBox(west = 0.05, south = 52.1, east = 0.15, north = 52.2)
    private val busZoom = CameraState(latitude = 52.2, longitude = 0.1, zoom = 12.0)
    private val countryZoom = busZoom.copy(zoom = 5.0)

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): MapViewModel = MapViewModel(
        repository = repository,
        cameraStore = cameraStore,
        locationProvider = FakeLocationProvider(),
        favourites = FavouritesRepository(favouritesDao),
        now = { clock },
    ).also { scheduler.runCurrent() }

    private fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    /** A view model already polling the city, with its first fetch done. */
    private fun pollingViewModel(): MapViewModel = viewModel().apply {
        onResumed()
        onCameraIdle(camera = busZoom, bounds = city, userGesture = true)
        advance(PAN_DEBOUNCE_MILLIS)
    }

    private fun tripOn(serviceId: Long, tripId: Long) = Trip(
        id = tripId,
        service = TripService(id = serviceId, lineName = "4"),
        times = listOf(StopTime(stop = TripStop(atcoCode = "A", name = "Market Square"))),
    )

    @Test
    fun `a stored camera is restored on launch`() {
        cameraStore.stored = busZoom

        assertEquals(busZoom, viewModel().state.value.camera)
    }

    @Test
    fun `a first launch opens on the whole country`() {
        assertEquals(CameraState.UnitedKingdom, viewModel().state.value.camera)
    }

    @Test
    fun `starred stops flow into the state`() {
        val star = FavouriteStop("A", "Market Square", 52.2, 0.1, addedAt = 0L)
        favouritesDao.insertBlocking(star)

        assertEquals(setOf("A"), viewModel().state.value.favouriteCodes)
    }

    @Test
    fun `a new area is fetched once the pan settles, not before`() {
        val model = viewModel()

        model.onCameraIdle(camera = busZoom, bounds = city, userGesture = true)
        advance(PAN_DEBOUNCE_MILLIS - 1)
        assertEquals(0, repository.vehiclesInBoxCalls)
        assertTrue(model.state.value.loadingVehicles)

        advance(1)
        assertEquals(1, repository.vehiclesInBoxCalls)
        assertFalse(model.state.value.loadingVehicles)
    }

    @Test
    fun `the next poll is armed a full interval after the last response`() {
        pollingViewModel()

        advance(VEHICLE_POLL_MILLIS - 1)
        assertEquals(1, repository.vehiclesInBoxCalls)

        advance(1)
        assertEquals(2, repository.vehiclesInBoxCalls)
    }

    @Test
    fun `panning within the last fetch waits for the regular poll`() {
        val model = pollingViewModel()

        model.onCameraIdle(camera = busZoom, bounds = insideCity, userGesture = true)
        advance(PAN_DEBOUNCE_MILLIS)
        assertEquals(1, repository.vehiclesInBoxCalls)

        advance(VEHICLE_POLL_MILLIS - PAN_DEBOUNCE_MILLIS)
        assertEquals(2, repository.vehiclesInBoxCalls)
    }

    @Test
    fun `a pan cancels the poll it supersedes`() {
        val model = viewModel()

        model.onCameraIdle(camera = busZoom, bounds = city, userGesture = true)
        advance(PAN_DEBOUNCE_MILLIS / 2)
        model.onCameraIdle(camera = busZoom, bounds = city, userGesture = true)
        advance(PAN_DEBOUNCE_MILLIS)

        assertEquals(1, repository.vehiclesInBoxCalls)
    }

    @Test
    fun `zooming out past the vehicle threshold stops polling`() {
        val model = pollingViewModel()

        model.onCameraIdle(camera = countryZoom, bounds = city, userGesture = true)
        advance(VEHICLE_POLL_MILLIS * 3)

        assertEquals(1, repository.vehiclesInBoxCalls)
    }

    @Test
    fun `pausing stops vehicle polling`() {
        val model = pollingViewModel()

        model.onPaused()
        advance(VEHICLE_POLL_MILLIS * 5)

        assertEquals(1, repository.vehiclesInBoxCalls)
    }

    @Test
    fun `resuming polls straight away rather than waiting an interval`() {
        val model = pollingViewModel()
        model.onPaused()
        advance(VEHICLE_POLL_MILLIS * 5)

        model.onResumed()
        scheduler.runCurrent()

        assertEquals(2, repository.vehiclesInBoxCalls)
    }

    @Test
    fun `a network failure reads as offline`() {
        repository.vehiclesFailure = IOException("no route to host")

        val state = pollingViewModel().state.value

        assertTrue(state.offline)
        assertFalse(state.appError)
    }

    @Test
    fun `any other failure reads as an app error`() {
        repository.vehiclesFailure = IllegalStateException("unexpected payload")

        val state = pollingViewModel().state.value

        assertFalse(state.offline)
        assertTrue(state.appError)
    }

    @Test
    fun `a successful poll clears an earlier failure`() {
        repository.vehiclesFailure = IOException("no route to host")
        val model = pollingViewModel()

        repository.vehiclesFailure = null
        advance(VEHICLE_POLL_MILLIS)

        assertFalse(model.state.value.offline)
    }

    @Test
    fun `a selected stop's board refreshes on its own interval`() {
        val model = viewModel()

        model.onStopSelected(atcoCode = "A", knownName = "Market Square")
        scheduler.runCurrent()
        assertEquals(1, repository.departuresCalls)

        advance(DEPARTURES_REFRESH_MILLIS)
        assertEquals(2, repository.departuresCalls)
    }

    @Test
    fun `pausing and resuming stops then restarts the board refresh`() {
        val model = viewModel()
        model.onStopSelected(atcoCode = "A", knownName = "Market Square")
        scheduler.runCurrent()

        model.onPaused()
        advance(DEPARTURES_REFRESH_MILLIS * 3)
        assertEquals(1, repository.departuresCalls)

        model.onResumed()
        advance(DEPARTURES_REFRESH_MILLIS)
        assertEquals(2, repository.departuresCalls)
    }

    @Test
    fun `a selected journey polls its own vehicle until paused`() {
        repository.trips[7L] = tripOn(serviceId = 3L, tripId = 7L)
        repository.pinned = Vehicle(id = 99L, coordinates = listOf(0.1, 52.2), delay = 120.0)
        val model = viewModel()

        model.onTripSelected(7L)
        scheduler.runCurrent()
        advance(VEHICLE_POLL_MILLIS)
        assertEquals(2, repository.vehicleForTripCalls)
        val journey = model.state.value.selection as SelectionState.Journey
        assertEquals(99L, journey.vehicle?.id)

        model.onPaused()
        advance(VEHICLE_POLL_MILLIS * 3)
        assertEquals(2, repository.vehicleForTripCalls)
    }

    @Test
    fun `clearing the selection stops its vehicle poll`() {
        repository.trips[7L] = tripOn(serviceId = 3L, tripId = 7L)
        val model = viewModel()
        model.onTripSelected(7L)
        scheduler.runCurrent()

        model.clearSelection()
        advance(VEHICLE_POLL_MILLIS * 3)

        assertEquals(1, repository.vehicleForTripCalls)
        assertEquals(SelectionState.None, model.state.value.selection)
    }

    @Test
    fun `drawn positions are aged against the injected clock`() {
        val model = viewModel()

        model.onResumed()
        scheduler.runCurrent()
        assertEquals(clock, model.state.value.decorations.nowMillis)

        clock += VEHICLE_POLL_MILLIS
        advance(VEHICLE_POLL_MILLIS)
        assertEquals(clock, model.state.value.decorations.nowMillis)
    }

    @Test
    fun `pausing stops the ageing ticker`() {
        val model = viewModel()
        model.onResumed()
        scheduler.runCurrent()
        val aged = model.state.value.decorations.nowMillis

        model.onPaused()
        clock += VEHICLE_POLL_MILLIS * 3
        advance(VEHICLE_POLL_MILLIS * 3)

        assertEquals(aged, model.state.value.decorations.nowMillis)
    }

    private fun FakeFavouriteStopDao.insertBlocking(stop: FavouriteStop) =
        kotlinx.coroutines.runBlocking { insert(stop) }
}
