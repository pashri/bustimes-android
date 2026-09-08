package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pashri.bustimes.data.model.StopTime
import org.pashri.bustimes.data.model.TripStop
import org.pashri.bustimes.ui.selection.Lateness
import org.pashri.bustimes.ui.selection.LatenessCalculator

class LatenessCalculatorTest {

    @Test
    fun `a bus running behind is late by the difference`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "09:38", expectedText = "10:13")

        assertEquals(Lateness.Late(35), lateness)
    }

    @Test
    fun `a bus running ahead is early`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "10:05", expectedText = "10:02")

        assertEquals(Lateness.Early(3), lateness)
    }

    @Test
    fun `matching times are on time`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "10:01", expectedText = "10:01")

        assertEquals(Lateness.OnTime, lateness)
    }

    @Test
    fun `no live estimate is unknown rather than on time`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "10:07", expectedText = null)

        // Most services outside London have no live feed at all; claiming they
        // are on time would be inventing information.
        assertEquals(Lateness.Unknown, lateness)
    }

    @Test
    fun `lateness across midnight does not read as most of a day`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "23:58", expectedText = "00:04")

        assertEquals(Lateness.Late(6), lateness)
    }

    @Test
    fun `earliness across midnight does not read as most of a day`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "00:03", expectedText = "23:57")

        assertEquals(Lateness.Early(6), lateness)
    }

    @Test
    fun `a stop time prefers actual over expected`() {
        val stopTime = StopTime(
            stop = TripStop(name = "Cambridge"),
            aimedDepartureTime = "10:00",
            expectedDepartureTime = "10:05",
            actualDepartureTime = "10:09",
        )

        assertEquals(Lateness.Late(9), LatenessCalculator.forStopTime(stopTime))
    }

    @Test
    fun `a stop time falls back to expected when nothing was recorded`() {
        val stopTime = StopTime(
            stop = TripStop(name = "Cambridge"),
            aimedDepartureTime = "10:00",
            expectedDepartureTime = "10:05",
        )

        assertEquals(Lateness.Late(5), LatenessCalculator.forStopTime(stopTime))
    }

    @Test
    fun `a stop time with only a schedule is unknown`() {
        val stopTime = StopTime(
            stop = TripStop(name = "Cambridge"),
            aimedDepartureTime = "10:00",
        )

        assertEquals(Lateness.Unknown, LatenessCalculator.forStopTime(stopTime))
    }

    @Test
    fun `seconds in a time are tolerated`() {
        val lateness = LatenessCalculator.forDeparture(aimedText = "09:38:00", expectedText = "09:45:00")

        assertEquals(Lateness.Late(7), lateness)
    }

    @Test
    fun `unparseable times are unknown rather than throwing`() {
        assertEquals(
            Lateness.Unknown,
            LatenessCalculator.forDeparture(aimedText = "soon", expectedText = "10:00"),
        )
    }
}
