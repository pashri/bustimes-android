package org.pashri.bustimes.ui.selection

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeParseException
import org.pashri.bustimes.data.model.StopTime

/** How a call is running relative to its schedule. */
sealed interface Lateness {

    /** No live data, so only the timetable is known. */
    data object Unknown : Lateness

    /** Running to time, within the rounding the source provides. */
    data object OnTime : Lateness

    /** Running behind by [minutes]. */
    data class Late(val minutes: Long) : Lateness

    /** Running ahead by [minutes]. */
    data class Early(val minutes: Long) : Lateness
}

/**
 * Compares aimed and live times.
 *
 * Times arrive as `HH:mm` strings with no date, so a comparison straddling
 * midnight would otherwise read as almost a full day out. Differences beyond
 * half a day are therefore interpreted as having wrapped.
 */
object LatenessCalculator {

    /**
     * Works out how a call is running.
     *
     * @param stopTime the call to assess.
     * @return the lateness, or [Lateness.Unknown] when there is no live time.
     */
    fun forStopTime(stopTime: StopTime): Lateness {
        val aimed = parse(stopTime.aimedDepartureTime ?: stopTime.aimedArrivalTime)
        val live = parse(
            stopTime.actualDepartureTime
                ?: stopTime.expectedDepartureTime
                ?: stopTime.actualArrivalTime
                ?: stopTime.expectedArrivalTime,
        )
        if (aimed == null || live == null) return Lateness.Unknown
        return fromMinutes(wrappedMinutesBetween(aimed, live))
    }

    /**
     * Works out how a departure is running.
     *
     * @param aimedText the scheduled time as `HH:mm`.
     * @param expectedText the live estimate as `HH:mm`, if any.
     * @return the lateness, or [Lateness.Unknown] when either time is missing.
     */
    fun forDeparture(aimedText: String?, expectedText: String?): Lateness {
        val aimed = parse(aimedText) ?: return Lateness.Unknown
        val expected = parse(expectedText) ?: return Lateness.Unknown
        return fromMinutes(wrappedMinutesBetween(aimed, expected))
    }

    private fun fromMinutes(minutes: Long): Lateness = when {
        minutes >= 1 -> Lateness.Late(minutes)
        minutes <= -1 -> Lateness.Early(-minutes)
        else -> Lateness.OnTime
    }

    /** Signed minutes from [aimed] to [live], corrected for a midnight wrap. */
    private fun wrappedMinutesBetween(aimed: LocalTime, live: LocalTime): Long {
        val raw = Duration.between(aimed, live).toMinutes()
        return when {
            raw > HALF_DAY_MINUTES -> raw - DAY_MINUTES
            raw < -HALF_DAY_MINUTES -> raw + DAY_MINUTES
            else -> raw
        }
    }

    private fun parse(text: String?): LocalTime? {
        val trimmed = text?.trim()?.take(HH_MM_LENGTH) ?: return null
        return try {
            LocalTime.parse(trimmed)
        } catch (error: DateTimeParseException) {
            null
        }
    }

    private const val HH_MM_LENGTH = 5
    private const val DAY_MINUTES = 24L * 60
    private const val HALF_DAY_MINUTES = DAY_MINUTES / 2
}
