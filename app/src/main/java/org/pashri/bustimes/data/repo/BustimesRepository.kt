package org.pashri.bustimes.data.repo

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.pashri.bustimes.data.model.DepartureBoard
import org.pashri.bustimes.data.model.ServicePage
import org.pashri.bustimes.data.model.StopCollection
import org.pashri.bustimes.data.model.StopFeature
import org.pashri.bustimes.data.model.Timetable
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.TripPage
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.data.net.Bustimes
import org.pashri.bustimes.data.parse.DeparturesParser
import org.pashri.bustimes.data.parse.TimetableCsvParser

/**
 * Every read from bustimes.org.
 *
 * There is no local mirror of upstream data: the OkHttp disk cache handles
 * revalidation and staleness, so this class only fetches and decodes. Room is
 * reserved for the user's own data.
 */
class BustimesRepository(
    private val client: OkHttpClient,
    private val json: Json = defaultJson,
) {

    /**
     * Live vehicle positions in a bounding box.
     *
     * Responses omit `delay` and `progress`; use [vehicleForTrip] once a bus
     * is selected to obtain those.
     *
     * @param box the viewport to fetch.
     * @return every tracked vehicle inside the box.
     * @throws IOException on network or HTTP failure.
     */
    suspend fun vehiclesInBox(box: BoundingBox): List<Vehicle> =
        get(Bustimes.vehiclesInBox(box)) { json.decodeFromString<List<Vehicle>>(it) }

    /**
     * The live position of the vehicle running a trip, with delay and progress.
     *
     * @return the vehicle, or null when the trip is not currently tracked.
     */
    suspend fun vehicleForTrip(serviceId: Long, tripId: Long): Vehicle? =
        get(Bustimes.vehiclesForTrip(serviceId, tripId)) {
            json.decodeFromString<List<Vehicle>>(it).firstOrNull()
        }

    /**
     * Stops in a bounding box.
     *
     * Served with `max-age=3600` and usually a CDN hit, so repeat calls are
     * answered from the OkHttp cache without touching the network.
     */
    suspend fun stopsInBox(box: BoundingBox): List<StopFeature> =
        get(Bustimes.stopsInBox(box)) { json.decodeFromString<StopCollection>(it).features }

    /**
     * One trip's schedule, live times and per-leg road geometry.
     *
     * @param tripId numeric trip id, as carried by `/vehicles.json`.
     */
    suspend fun trip(tripId: Long): Trip =
        get(Bustimes.trip(tripId)) { json.decodeFromString<Trip>(it) }

    /**
     * Trips on a service for a date, without stop times.
     *
     * Used to map timetable columns to trip ids by matching a column's first
     * departure against [Trip.start].
     *
     * Pages are followed, because one page is a hundred trips and is not
     * ordered by time: a frequent service returns a page starting near midday
     * and omitting the whole morning, so a single page leaves most of the
     * timetable unmatched.
     *
     * @param serviceId the service to list.
     * @param date the service date, as `YYYY-MM-DD`.
     * @param maxPages a stop so a pathological service cannot loop forever.
     * @return every trip found, across pages.
     */
    suspend fun tripsForService(
        serviceId: Long,
        date: String,
        maxPages: Int = MAX_TRIP_PAGES,
    ): List<Trip> {
        val trips = mutableListOf<Trip>()
        var url: String? = Bustimes.tripsForService(serviceId, date)
        var pages = 0
        while (url != null && pages < maxPages) {
            val page = get(url) { json.decodeFromString<TripPage>(it) }
            trips += page.results
            url = page.next
            pages++
        }
        return trips
    }

    /**
     * A stop's departure board.
     *
     * @throws org.pashri.bustimes.data.parse.DeparturesParseException if the
     *   upstream template has changed shape.
     */
    suspend fun departures(atcoCode: String): DepartureBoard =
        get(Bustimes.departures(atcoCode)) { DeparturesParser.parse(it) }

    /**
     * A service's full timetable.
     *
     * @param serviceId numeric service id; the slug form is not accepted here.
     */
    suspend fun timetable(serviceId: Long): Timetable =
        get(Bustimes.timetableCsv(serviceId)) { TimetableCsvParser.parse(it) }

    /**
     * Resolves service slugs to the numeric ids the timetable and geometry
     * endpoints require. Batched, so a whole departure board costs one call.
     */
    suspend fun serviceIdsBySlug(slugs: Collection<String>): Map<String, Long> {
        if (slugs.isEmpty()) return emptyMap()
        return get(Bustimes.servicesBySlug(slugs)) { body ->
            json.decodeFromString<ServicePage>(body).results
                .mapNotNull { summary -> summary.slug?.let { it to summary.id } }
                .toMap()
        }
    }

    private suspend fun <T> get(url: String, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for $url")
                }
                decode(response.body?.string().orEmpty())
            }
        }

    companion object {
        /**
         * Tolerates fields bustimes.org adds over time, and treats absent
         * fields as their defaults so a payload gaining a key never crashes.
         */
        /** Trip pages to follow before giving up. 100 trips per page. */
        const val MAX_TRIP_PAGES = 6

        val defaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
