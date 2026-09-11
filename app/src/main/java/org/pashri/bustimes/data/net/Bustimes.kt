package org.pashri.bustimes.data.net

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Endpoint construction for bustimes.org. */
object Bustimes {

    const val BASE_URL = "https://bustimes.org"

    /**
     * Identifies this client honestly, with a contact URL, so the operator of
     * bustimes.org can see who is making the requests and get in touch.
     */
    const val USER_AGENT =
        "bustimes-android/0.1 (personal project; https://github.com/pashri/bustimes-android)"

    /** Live vehicle positions inside a bounding box. */
    fun vehiclesInBox(box: BoundingBox): String =
        "$BASE_URL/vehicles.json?ymax=${box.north}&xmax=${box.east}" +
            "&ymin=${box.south}&xmin=${box.west}"

    /**
     * Live position of one vehicle, filtered by service and trip.
     *
     * The bounding-box form omits `delay` and `progress`; only the filtered
     * forms carry them, which is why selecting a bus costs a second request.
     */
    fun vehiclesForTrip(serviceId: Long, tripId: Long): String =
        "$BASE_URL/vehicles.json?service=$serviceId&trip=$tripId"

    /** Stops inside a bounding box, as GeoJSON. Served with `max-age=3600`. */
    fun stopsInBox(box: BoundingBox): String =
        "$BASE_URL/stops.json?ymax=${box.north}&xmax=${box.east}" +
            "&ymin=${box.south}&xmin=${box.west}"

    /** One trip: stop times, aimed/expected/actual, and per-leg road geometry. */
    fun trip(tripId: Long): String = "$BASE_URL/api/trips/$tripId/?format=json"

    /** Trips on a service for a date. Carries no stop times — ids and start/end only. */
    fun tripsForService(serviceId: Long, date: String): String =
        "$BASE_URL/api/trips/?service=$serviceId&date=$date&format=json"

    /** One tracked journey, which is how a departure board links a live departure. */
    fun vehicleJourney(journeyId: Long): String =
        "$BASE_URL/api/vehiclejourneys/$journeyId/?format=json"

    /** A stop's departure board. HTML fragment; there is no JSON equivalent. */
    fun departures(atcoCode: String): String = "$BASE_URL/stops/$atcoCode/departures"

    /** A service's full timetable as CSV. */
    fun timetableCsv(serviceId: Long): String = "$BASE_URL/services/$serviceId/timetable.csv"

    /** Resolves service slugs to numeric ids, which the geometry and CSV endpoints need. */
    fun servicesBySlug(slugs: Collection<String>): String =
        "$BASE_URL/api/services/?slug__in=${slugs.joinToString(",")}&format=json"

    /** Route geometry and stops for a service, by numeric id (not slug). */
    fun serviceGeometry(serviceId: Long): String = "$BASE_URL/services/$serviceId.json"
}

/** A geographic bounding box in WGS84 degrees. */
data class BoundingBox(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    /** True when [other] lies entirely inside this box. */
    fun contains(other: BoundingBox): Boolean =
        other.west >= west &&
            other.south >= south &&
            other.east <= east &&
            other.north <= north
}

/** Adds the project's User-Agent to every request. */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request().newBuilder()
            .header("User-Agent", Bustimes.USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Builds the shared HTTP client.
 *
 * The disk [Cache] is what honours `max-age` on `/stops.json` and revalidates
 * route geometry and timetables with `If-Modified-Since` / `If-None-Match`,
 * so most of the politeness measures cost no application code at all.
 */
fun buildHttpClient(cacheDir: File): OkHttpClient = OkHttpClient.Builder()
    .cache(Cache(File(cacheDir, "http"), CACHE_BYTES))
    .addInterceptor(UserAgentInterceptor())
    .addNetworkInterceptor(ClockSkewInterceptor())
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

private const val CACHE_BYTES = 20L * 1024 * 1024
