package org.pashri.bustimes.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One trip from `/api/trips/<id>/`.
 *
 * On the detail endpoint [times] carries every stop, its aimed/expected/actual
 * times, and the road geometry of the leg leading into it. On the *list*
 * endpoint the field is declared but always null, so a grid timetable cannot
 * be assembled from list responses.
 */
@Serializable
data class Trip(
    val id: Long,
    @SerialName("vehicle_journey_code") val vehicleJourneyCode: String? = null,
    @SerialName("ticket_machine_code") val ticketMachineCode: String? = null,
    val block: String? = null,
    /** Scheduled start as `HH:mm:ss`. */
    val start: String? = null,
    val end: String? = null,
    val headsign: String? = null,
    val service: TripService? = null,
    val operator: TripOperator? = null,
    val notes: List<TripNote> = emptyList(),
    val times: List<StopTime>? = null,
)

@Serializable
data class TripService(
    val id: Long,
    @SerialName("line_name") val lineName: String = "",
    val slug: String? = null,
    val mode: String? = null,
)

@Serializable
data class TripOperator(
    val noc: String? = null,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class TripNote(
    val code: String? = null,
    val text: String? = null,
)

/**
 * A call at one stop.
 *
 * [expectedDepartureTime] comes from GTFS-R feeds and [actualDepartureTime] is
 * derived server-side by matching recorded vehicle positions to within 100 m
 * of the stop. Both are null when a trip has no live feed, which is the common
 * case outside London and the largest operators — so the UI must read well
 * with aimed times alone.
 *
 * [track] is the road geometry of the leg *into* this stop, as `[lon, lat]`
 * pairs. The first stop of a trip has none.
 */
@Serializable
data class StopTime(
    val id: Long? = null,
    val stop: TripStop,
    @SerialName("aimed_arrival_time") val aimedArrivalTime: String? = null,
    @SerialName("aimed_departure_time") val aimedDepartureTime: String? = null,
    @SerialName("timing_status") val timingStatus: String? = null,
    @SerialName("pick_up") val pickUp: Boolean = true,
    @SerialName("set_down") val setDown: Boolean = true,
    @SerialName("expected_arrival_time") val expectedArrivalTime: String? = null,
    @SerialName("expected_departure_time") val expectedDepartureTime: String? = null,
    @SerialName("actual_arrival_time") val actualArrivalTime: String? = null,
    @SerialName("actual_departure_time") val actualDepartureTime: String? = null,
    @SerialName("note_codes") val noteCodes: List<String>? = null,
    val track: List<List<Double>>? = null,
) {
    /** The time to show: actual if it happened, else expected, else aimed. */
    val bestDepartureTime: String?
        get() = actualDepartureTime ?: expectedDepartureTime ?: aimedDepartureTime

    /** True when a live or recorded time differs from the schedule. */
    val hasLiveTime: Boolean
        get() = expectedDepartureTime != null || actualDepartureTime != null
}

@Serializable
data class TripStop(
    @SerialName("atco_code") val atcoCode: String? = null,
    val name: String = "",
    /** `[lon, lat]`, or null for stops with no recorded position. */
    val location: List<Double>? = null,
    val bearing: Double? = null,
    val icon: String? = null,
)

/** A page of `/api/trips/?service=&date=`. Used to make timetable columns tappable. */
@Serializable
data class TripPage(
    val next: String? = null,
    val results: List<Trip> = emptyList(),
)

/** A page of `/api/services/?slug__in=`. */
@Serializable
data class ServicePage(
    val results: List<ServiceSummary> = emptyList(),
)

@Serializable
data class ServiceSummary(
    val id: Long,
    @SerialName("line_name") val lineName: String = "",
    val slug: String? = null,
    val description: String? = null,
    val mode: String? = null,
)
