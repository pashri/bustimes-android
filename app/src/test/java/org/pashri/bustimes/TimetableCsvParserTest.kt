package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.parse.TimetableCsvParser
import org.pashri.bustimes.data.parse.TimetableParseException

class TimetableCsvParserTest {

    @Test
    fun `parses an hourly service into groupings`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_80370.csv"))

        assertTrue(timetable.groupings.isNotEmpty())
        val grouping = timetable.groupings.first()
        assertEquals("T3", grouping.lineNames.first())
        assertTrue("expected many stops", grouping.rows.size > 10)
    }

    @Test
    fun `keeps ATCO codes so timetable rows can open a stop`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_80370.csv"))
        val row = timetable.groupings.first().rows.first()

        assertNotNull(row.atcoCode)
        assertTrue(row.atcoCode!!.startsWith("0500"))
    }

    @Test
    fun `handles quoted stop names containing commas`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_80370.csv"))
        val names = timetable.groupings.flatMap { it.rows }.map { it.stopName }

        // "Fulbourn, opp Capital Park" is quoted in the CSV; splitting on
        // commas rather than using a CSV reader would shear it in half.
        val comma = names.first { it.contains(",") }
        assertTrue(comma.isNotBlank())
        assertFalse(comma.endsWith(","))
    }

    @Test
    fun `expands every cell rather than emitting then-every prose`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_8092.csv"))
        val times = timetable.groupings.flatMap { it.rows }.flatMap { it.times }.filterNotNull()

        assertTrue("high-frequency service should expand to many times", times.size > 500)
        assertTrue(times.all { it.hour in 0..29 && it.minute in 0..59 })
    }

    @Test
    fun `marks post-midnight times as next day`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_8092.csv"))
        val nextDay = timetable.groupings.flatMap { it.rows }
            .flatMap { it.times }.filterNotNull().filter { it.nextDay }

        assertTrue("the busway runs past midnight", nextDay.isNotEmpty())
        // A 00:34 marked next-day must sort after a 23:50, not before 06:00.
        assertTrue(nextDay.all { it.minutesSinceMidnight >= 24 * 60 })
    }

    @Test
    fun `parses the next-day marker on a bare cell`() {
        val time = TimetableCsvParser.parseTime("00:34⁺¹")

        assertNotNull(time)
        assertEquals(0, time!!.hour)
        assertEquals(34, time.minute)
        assertTrue(time.nextDay)
        assertEquals(24 * 60 + 34, time.minutesSinceMidnight)
    }

    @Test
    fun `a plain time is not next day`() {
        val time = TimetableCsvParser.parseTime("09:03")

        assertEquals(9 * 60 + 3, time!!.minutesSinceMidnight)
        assertFalse(time.nextDay)
    }

    @Test
    fun `a blank cell means the journey does not call here`() {
        assertNull(TimetableCsvParser.parseTime(""))
        assertNull(TimetableCsvParser.parseTime("   "))
    }

    @Test
    fun `transposes the grid into journeys for the phone layout`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_80370.csv"))
        val grouping = timetable.groupings.first()
        val journeys = grouping.journeys()

        assertEquals(grouping.columnCount, journeys.size)
        val first = journeys.first()
        assertTrue(first.calls.isNotEmpty())
        assertNotNull(first.departureTime)
        // Calls within a journey run forwards in time down the route.
        val minutes = first.calls.map { it.time.minutesSinceMidnight }
        assertEquals(minutes.sorted(), minutes)
    }

    @Test
    fun `splits directions into separate groupings`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_80370.csv"))

        // Commons CSV discards the blank separator row, so groupings are found
        // by their header row instead. Getting this wrong silently merged both
        // directions into one grouping whose times ran backwards mid-route.
        assertEquals(2, timetable.groupings.size)
        val (outbound, inbound) = timetable.groupings
        assertNotEquals(outbound.origin, inbound.origin)
        assertTrue(outbound.rows.none { it.stopName.equals("stop", ignoreCase = true) })
    }

    @Test
    fun `every journey in every grouping runs forwards in time`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_8092.csv"))

        for (grouping in timetable.groupings) {
            for (journey in grouping.journeys()) {
                val minutes = journey.calls.map { it.time.minutesSinceMidnight }
                assertEquals(
                    "journey on ${grouping.origin} runs backwards",
                    minutes.sorted(),
                    minutes,
                )
            }
        }
    }

    @Test
    fun `journeys are returned in departure order`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_8092.csv"))

        for (grouping in timetable.groupings) {
            val departures = grouping.journeys()
                .mapNotNull { it.departureTime?.minutesSinceMidnight }
            // CSV column order groups journeys by stopping pattern, not time,
            // so a list rendered in column order jumps back and forth.
            assertEquals(departures.sorted(), departures)
        }
    }

    @Test
    fun `raw journeys keep the printed timetable's column order`() {
        val timetable = TimetableCsvParser.parse(fixture("timetable_80370.csv"))
        val grouping = timetable.groupings.first()

        assertEquals(grouping.columnCount, grouping.rawJourneys().size)
        assertEquals(grouping.rawJourneys().size, grouping.journeys().size)
    }

    @Test(expected = TimetableParseException::class)
    fun `an empty csv throws`() {
        TimetableCsvParser.parse("")
    }
}
