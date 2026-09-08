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
     * A bus, with its schedule.
     *
     * @property vehicleId the tracked vehicle.
     * @property trip the schedule, once loaded.
     * @property vehicle the vehicle's live detail, which carries delay and
     *   progress; the bounding-box poll omits both, so this needs its own
     *   request.
     * @property loading true while either request is outstanding.
     * @property failed true when the schedule could not be fetched.
     */
    data class Bus(
        val vehicleId: Long,
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
    }

    /**
     * A stop, with its departure board.
     *
     * @property atcoCode the stop.
     * @property name the stop's name, when known from the map data.
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
