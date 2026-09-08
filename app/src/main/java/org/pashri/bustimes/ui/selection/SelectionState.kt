package org.pashri.bustimes.ui.selection

import org.pashri.bustimes.data.model.DepartureBoard
import org.pashri.bustimes.data.model.Trip
import org.pashri.bustimes.data.model.Vehicle

/**
 * What the bottom sheet is showing.
 *
 * The sheet is closed only in [None]; every other case has content, which is
 * why the sheet's presence can be derived from this rather than tracked
 * separately.
 */
sealed interface SelectionState {

    /** Nothing selected. The map fills the screen. */
    data object None : SelectionState

    /**
     * A journey, with its schedule.
     *
     * Keyed by trip rather than by vehicle. A departure board row or a
     * timetable journey gives a trip id and no vehicle at all — most
     * departures are not tracked — so a vehicle-keyed selection could only
     * ever represent buses already visible on the map.
     *
     * @property tripId the journey being shown.
     * @property trip the schedule, once loaded.
     * @property vehicle the vehicle running it, when one is tracked. Carries
     *   delay and progress, which the bounding-box poll omits, so it needs its
     *   own request.
     * @property loading true while the schedule is outstanding.
     * @property failed true when the schedule could not be fetched.
     */
    data class Journey(
        val tripId: Long,
        val trip: Trip? = null,
        val vehicle: Vehicle? = null,
        val loading: Boolean = true,
        val failed: Boolean = false,
    ) : SelectionState {

        /** The line number, from whichever source has arrived first. */
        val lineName: String?
            get() = trip?.service?.lineName ?: vehicle?.service?.lineName

        /** The numeric service id, needed to open the full timetable. */
        val serviceId: Long?
            get() = trip?.service?.id ?: vehicle?.serviceId

        /** Where the journey is going. */
        val headsign: String?
            get() = trip?.headsign ?: vehicle?.destination

        /**
         * True when no vehicle is reporting against this journey.
         *
         * Not an error: outside London and the largest operators most
         * journeys are never tracked, so the schedule alone is the norm.
         */
        val untracked: Boolean
            get() = !loading && vehicle == null
    }

    /**
     * A stop, with its departure board.
     *
     * @property atcoCode the stop.
     * @property name the stop's name, when known.
     * @property board the parsed board, once loaded.
     * @property loading true while the board is being fetched.
     * @property unreadable true when the board could not be parsed, which
     *   means bustimes.org has changed its template.
     */
    data class Stop(
        val atcoCode: String,
        val name: String? = null,
        val board: DepartureBoard? = null,
        val loading: Boolean = true,
        val unreadable: Boolean = false,
    ) : SelectionState
}
