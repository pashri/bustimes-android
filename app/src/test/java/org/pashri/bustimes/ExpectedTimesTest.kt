package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.pashri.bustimes.data.model.StopTime
import org.pashri.bustimes.data.model.TripStop
import org.pashri.bustimes.ui.selection.ExpectedTimes

class ExpectedTimesTest {

    private fun call(atco: String, aimed: String, expected: String? = null) = StopTime(
        stop = TripStop(atcoCode = atco, name = atco),
        aimedDepartureTime = aimed,
        expectedDepartureTime = expected,
    )

    private val trip = listOf(
        call("A", "10:00"),
        call("B", "10:05"),
        call("C", "10:10"),
    )

    private fun expected(times: List<StopTime>) = times.map { it.expectedDepartureTime }

    @Test
    fun `a late bus gets expected times at the stops still ahead`() {
        val times = ExpectedTimes.project(trip, delaySeconds = 240.0, nextStop = "B")

        assertEquals(listOf(null, "10:09", "10:14"), expected(times))
    }

    @Test
    fun `a delay under a minute adds nothing`() {
        val times = ExpectedTimes.project(trip, delaySeconds = 59.0, nextStop = "A")

        assertEquals(listOf(null, null, null), expected(times))
    }

    @Test
    fun `an early bus is not projected forward`() {
        val times = ExpectedTimes.project(trip, delaySeconds = -180.0, nextStop = "A")

        // An early bus normally waits at timing points, so the gap does not
        // carry forward the way a delay does.
        assertEquals(listOf(null, null, null), expected(times))
    }

    @Test
    fun `a time from the feed is kept over the projection`() {
        val withFeed = listOf(call("A", "10:00"), call("B", "10:05", expected = "10:07"))

        val times = ExpectedTimes.project(withFeed, delaySeconds = 300.0, nextStop = "A")

        assertEquals(listOf("10:05", "10:07"), expected(times))
    }

    @Test
    fun `nothing is projected when the next stop is unknown`() {
        val unmatched = ExpectedTimes.project(trip, delaySeconds = 300.0, nextStop = "Z")
        val missing = ExpectedTimes.project(trip, delaySeconds = 300.0, nextStop = null)

        assertEquals(listOf(null, null, null), expected(unmatched))
        assertEquals(listOf(null, null, null), expected(missing))
    }

    @Test
    fun `aimed times with seconds still project to hours and minutes`() {
        val times = ExpectedTimes.project(
            listOf(call("A", "10:00:00")),
            delaySeconds = 150.0,
            nextStop = "A",
        )

        assertEquals("10:02", times.single().expectedDepartureTime)
    }

    @Test
    fun `a delay carried past midnight wraps to the next day`() {
        val times = ExpectedTimes.project(
            listOf(call("A", "23:58")),
            delaySeconds = 360.0,
            nextStop = "A",
        )

        assertEquals("00:04", times.single().expectedDepartureTime)
    }

    @Test
    fun `a call with no aimed time is left alone`() {
        val bare = listOf(StopTime(stop = TripStop(atcoCode = "A")))

        val times = ExpectedTimes.project(bare, delaySeconds = 300.0, nextStop = "A")

        assertNull(times.single().expectedDepartureTime)
    }
}
