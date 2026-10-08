package org.pashri.bustimes.data.repo

import java.io.IOException
import org.pashri.bustimes.data.model.DepartureBoard
import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.Timetable
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox

/**
 * Every read from bustimes.org.
 *
 * An interface so view models can be driven by a fake in JVM tests; the app
 * uses [HttpBustimesRepository].
 */
interface BustimesRepository {

    /**
     * Live vehicle positions in a bounding box, without delay or progress.
     *
     * @param box the viewport to fetch.
     * @return every tracked vehicle inside the box.
     * @throws IOException on network or HTTP failure.
     */
    suspend fun vehiclesInBox(box: BoundingBox): List<Vehicle>

    /**
     * The live position of the vehicle running a trip, with delay and progress.
     *
     * @param serviceId the trip's service.
     * @param tripId the trip.
     * @return the vehicle, or null when the trip is not currently tracked.
     */
    suspend fun vehicleForTrip(serviceId: Long, tripId: Long): Vehicle?

    /**
     * Stops in a bounding box.
     *
     * @param box the viewport to fetch.
     * @return every stop inside the box.
     */
    suspend fun stopsInBox(box: BoundingBox): List<StopFeature>

    /**
     * One trip's schedule, live times and per-leg road geometry.
     *
     * @param tripId numeric trip id, as carried by `/vehicles.json`.
     * @return the trip.
     */
    suspend fun trip(tripId: Long): Trip

    /**
     * Trips on a service for a date, without stop times.
     *
     * @param serviceId the service to list.
     * @param date the service date, as `YYYY-MM-DD`.
     * @return every trip found.
     */
    suspend fun tripsForService(serviceId: Long, date: String): List<Trip>

    /**
     * Finds the trip behind a tracked journey.
     *
     * @param journeyId the journey from a departure board link.
     * @return the trip id, or null when upstream has not matched one.
     */
    suspend fun tripIdForJourney(journeyId: Long): Long?

    /**
     * A stop's departure board.
     *
     * @param atcoCode the stop.
     * @return the parsed board.
     */
    suspend fun departures(atcoCode: String): DepartureBoard

    /**
     * A service's full timetable.
     *
     * @param serviceId numeric service id.
     * @return the parsed timetable.
     */
    suspend fun timetable(serviceId: Long): Timetable

    /**
     * Resolves service slugs to numeric ids.
     *
     * @param slugs the slugs to resolve.
     * @return the ids found, keyed by slug.
     */
    suspend fun serviceIdsBySlug(slugs: Collection<String>): Map<String, Long>
}
