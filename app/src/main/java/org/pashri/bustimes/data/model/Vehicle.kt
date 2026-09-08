package org.pashri.bustimes.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A live vehicle position from `/vehicles.json`.
 *
 * [delay] and [progress] are present only in the filtered forms of the
 * endpoint (`?service=`, `?trip=`, `?id=`). The bounding-box form used by the
 * map omits them, so they are null for every vehicle drawn from a bbox poll.
 */
@Serializable
data class Vehicle(
    val id: Long,
    @SerialName("journey_id") val journeyId: Long? = null,
    val coordinates: List<Double>,
    val heading: Double? = null,
    val datetime: String? = null,
    val destination: String? = null,
    @SerialName("trip_id") val tripId: Long? = null,
    @SerialName("service_id") val serviceId: Long? = null,
    val service: VehicleService? = null,
    val vehicle: VehicleDetail? = null,
    val progress: VehicleProgress? = null,
    /** Seconds behind schedule; negative means early. */
    val delay: Int? = null,
    val seats: String? = null,
    val wheelchair: String? = null,
) {
    val longitude: Double get() = coordinates[0]
    val latitude: Double get() = coordinates[1]
}

@Serializable
data class VehicleService(
    val url: String? = null,
    @SerialName("line_name") val lineName: String = "",
) {
    /** The slug from a `/services/<slug>` url, or null. Needed to resolve numeric ids. */
    val slug: String? get() = url?.removePrefix("/services/")?.takeIf { it.isNotBlank() }
}

@Serializable
data class VehicleDetail(
    val url: String? = null,
    val name: String? = null,
    val features: String? = null,
    val livery: Int? = null,
    /** Livery colour as `#rrggbb`, when the operator's livery is a flat colour. */
    val colour: String? = null,
    @SerialName("text_colour") val textColour: String? = null,
    val css: String? = null,
)

/**
 * How far along the current leg a vehicle is.
 *
 * [progress] is a 0..1 fraction between [prevStop] and [nextStop], which maps
 * directly onto the matching leg's `track` from `/api/trips/<id>/` — that
 * pairing is what lets the selected bus slide along the real road.
 */
@Serializable
data class VehicleProgress(
    val id: Long? = null,
    val sequence: Int? = null,
    @SerialName("prev_stop") val prevStop: String? = null,
    @SerialName("next_stop") val nextStop: String? = null,
    val progress: Double = 0.0,
)
