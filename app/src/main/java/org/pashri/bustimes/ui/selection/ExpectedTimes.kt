package org.pashri.bustimes.ui.selection

import java.time.format.DateTimeFormatter
import org.pashri.bustimes.data.model.StopTime

/**
 * Estimates times at the stops a late bus has yet to reach.
 *
 * Most trips have no live times of their own, but the tracked vehicle reports
 * how far behind schedule it is. Carrying that delay forward onto the stops
 * still ahead is the same estimate the header's lateness already makes, just
 * spelt out per stop so a reader can see when the bus will reach theirs.
 */
object ExpectedTimes {

    /**
     * Fills in expected times from the vehicle's delay.
     *
     * Only stops from [nextStop] onwards are touched, since a delay says
     * nothing about stops already passed, and any time the feed supplied
     * itself is kept. Early running is not projected: a bus ahead of time
     * usually waits at timing points, so the gap does not carry forward.
     *
     * @param times the trip's calling points, in order.
     * @param delaySeconds the vehicle's delay; negative means early.
     * @param nextStop the ATCO code of the stop the vehicle is heading for.
     * @return [times], with expected departure times added where projected.
     */
    fun project(times: List<StopTime>, delaySeconds: Double?, nextStop: String?): List<StopTime> {
        val delay = delaySeconds?.toLong()?.takeIf { it >= SECONDS_PER_MINUTE }
            ?: return times
        val from = times.indexOfFirst { it.stop.atcoCode == nextStop }
        if (nextStop == null || from < 0) return times
        return times.mapIndexed { index, time ->
            if (index < from) time else withDelay(time, delay)
        }
    }

    private fun withDelay(time: StopTime, delaySeconds: Long): StopTime {
        if (time.expectedDepartureTime != null || time.actualDepartureTime != null) return time
        val aimed = LatenessCalculator.parse(time.aimedDepartureTime ?: time.aimedArrivalTime)
            ?: return time
        val expected = aimed.plusSeconds(delaySeconds).format(HH_MM)
        return time.copy(expectedDepartureTime = expected)
    }

    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val SECONDS_PER_MINUTE = 60L
}
