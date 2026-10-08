package org.pashri.bustimes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.pashri.bustimes.data.db.FavouriteStop
import org.pashri.bustimes.data.db.FavouriteStopDao
import org.pashri.bustimes.data.location.DevicePosition
import org.pashri.bustimes.data.location.LocationProvider
import org.pashri.bustimes.data.model.DepartureBoard
import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.Timetable
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.prefs.CameraStore
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.ui.map.CameraState

/**
 * A [BustimesRepository] that answers from fields and counts its calls.
 *
 * Set [vehiclesFailure] to make the bbox poll throw instead of answering.
 */
class FakeBustimesRepository : BustimesRepository {

    var vehicles: List<Vehicle> = emptyList()
    var vehiclesFailure: Exception? = null
    var pinned: Vehicle? = null
    val trips = mutableMapOf<Long, Trip>()

    var vehiclesInBoxCalls = 0
        private set
    var vehicleForTripCalls = 0
        private set
    var departuresCalls = 0
        private set

    override suspend fun vehiclesInBox(box: BoundingBox): List<Vehicle> {
        vehiclesInBoxCalls++
        vehiclesFailure?.let { throw it }
        return vehicles
    }

    override suspend fun vehicleForTrip(serviceId: Long, tripId: Long): Vehicle? {
        vehicleForTripCalls++
        return pinned
    }

    override suspend fun stopsInBox(box: BoundingBox): List<StopFeature> = emptyList()

    override suspend fun trip(tripId: Long): Trip =
        trips[tripId] ?: throw NoSuchElementException("no trip $tripId")

    override suspend fun tripsForService(serviceId: Long, date: String): List<Trip> =
        emptyList()

    override suspend fun tripIdForJourney(journeyId: Long): Long? = null

    override suspend fun departures(atcoCode: String): DepartureBoard {
        departuresCalls++
        return DepartureBoard(departures = emptyList(), hasLive = false, hasScheduled = true)
    }

    override suspend fun timetable(serviceId: Long): Timetable =
        throw UnsupportedOperationException("not used by the map")

    override suspend fun serviceIdsBySlug(slugs: Collection<String>): Map<String, Long> =
        emptyMap()
}

/** A [CameraStore] holding the camera in memory. */
class FakeCameraStore(var stored: CameraState? = null) : CameraStore {

    override suspend fun read(): CameraState? = stored

    override suspend fun write(camera: CameraState) {
        stored = camera
    }
}

/** A [LocationProvider] with fixed answers. */
class FakeLocationProvider(
    private val lastKnown: DevicePosition? = null,
    private val current: DevicePosition? = null,
) : LocationProvider {

    override suspend fun lastKnown(): DevicePosition? = lastKnown

    override suspend fun current(): DevicePosition? = current
}

/** A [FavouriteStopDao] backed by a flow, standing in for Room. */
class FakeFavouriteStopDao(initial: List<FavouriteStop> = emptyList()) : FavouriteStopDao {

    private val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<FavouriteStop>> = rows

    override suspend fun insert(stop: FavouriteStop) {
        rows.update { current -> current.filterNot { it.atcoCode == stop.atcoCode } + stop }
    }

    override suspend fun delete(atcoCode: String) {
        rows.update { current -> current.filterNot { it.atcoCode == atcoCode } }
    }
}
