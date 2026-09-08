package org.pashri.bustimes.data.model

/**
 * One row of a stop's departure board.
 *
 * [tripId] is set for departures with no live tracking; [journeyId] is set for
 * tracked ones. bustimes.org links to `/trips/<id>` and `/journeys/<id>`
 * respectively, and only the former can be opened as a schedule via
 * `/api/trips/<id>/`.
 */
data class Departure(
    val lineName: String,
    val serviceSlug: String?,
    val destination: String,
    val vehicle: String?,
    val aimedTime: String?,
    val expectedTime: String?,
    val cancelled: Boolean,
    val tripId: Long?,
    val journeyId: Long?,
    /** Which stop of a stop area this call is at; null for single stops. */
    val indicator: String?,
    /** Heading for departures on a later date, e.g. "Wednesday 9 September". */
    val dateHeading: String?,
) {
    /** True when the board carried a live estimate for this departure. */
    val isTracked: Boolean get() = expectedTime != null

    /** The time a user should act on. */
    val bestTime: String? get() = expectedTime ?: aimedTime
}

/** A parsed departure board. */
data class DepartureBoard(
    val departures: List<Departure>,
    val hasLive: Boolean,
    val hasScheduled: Boolean,
)
