package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.model.RouteGeometry
import org.pashri.bustimes.data.model.StopTime
import org.pashri.bustimes.data.model.TripStop

class RouteGeometryTest {

    private fun call(
        name: String,
        location: List<Double>? = listOf(0.1, 52.2),
        track: List<List<Double>>? = null,
    ) = StopTime(stop = TripStop(name = name, atcoCode = name, location = location), track = track)

    @Test
    fun `road geometry is preferred and is not approximate`() {
        val times = listOf(
            call("first"),
            call("second", track = listOf(listOf(0.10, 52.20), listOf(0.11, 52.21))),
            call("third", track = listOf(listOf(0.11, 52.21), listOf(0.12, 52.22))),
        )

        val geometry = RouteGeometry.forTimes(times)

        assertEquals(2, geometry.legs.size)
        assertFalse(geometry.approximate)
    }

    @Test
    fun `a service with no road geometry falls back to its calling points`() {
        // 18A, 100 and X3 carry no track on any stop time, and bustimes has no
        // road shape for them either, so stop order is all that can be drawn.
        val times = listOf(
            call("first", location = listOf(0.10, 52.20)),
            call("second", location = listOf(0.11, 52.21)),
            call("third", location = listOf(0.12, 52.22)),
        )

        val geometry = RouteGeometry.forTimes(times)

        assertTrue("the fallback must be flagged so it can be drawn dashed", geometry.approximate)
        assertEquals("one leg through every stop", 1, geometry.legs.size)
        assertEquals(3, geometry.legs.first().size)
    }

    @Test
    fun `a single known stop is not a line`() {
        val times = listOf(call("only", location = listOf(0.1, 52.2)))

        assertTrue(RouteGeometry.forTimes(times).isEmpty)
    }

    @Test
    fun `stops with no position are skipped`() {
        val times = listOf(
            call("placed", location = listOf(0.10, 52.20)),
            call("unplaced", location = null),
            call("placed too", location = listOf(0.12, 52.22)),
        )

        val geometry = RouteGeometry.forTimes(times)

        assertEquals(2, geometry.legs.first().size)
        assertTrue(geometry.approximate)
    }

    @Test
    fun `a malformed coordinate pair cannot reach the map`() {
        // The map indexes these directly, so a one-element pair would throw.
        val times = listOf(
            call("bad", location = listOf(0.10)),
            call("good", location = listOf(0.12, 52.22)),
            call("also good", location = listOf(0.13, 52.23)),
        )

        val geometry = RouteGeometry.forTimes(times)

        assertEquals(2, geometry.legs.first().size)
        assertTrue(geometry.legs.flatten().all { it.size >= 2 })
    }

    @Test
    fun `a malformed track leg is rejected rather than drawn`() {
        val times = listOf(
            call("first", location = listOf(0.10, 52.20)),
            call("second", location = listOf(0.11, 52.21), track = listOf(listOf(0.10))),
        )

        val geometry = RouteGeometry.forTimes(times)

        // Falls through to the calling points instead of indexing past the end.
        assertTrue(geometry.approximate)
        assertTrue(geometry.legs.flatten().all { it.size >= 2 })
    }

    @Test
    fun `an empty trip draws nothing`() {
        assertTrue(RouteGeometry.forTimes(emptyList()).isEmpty)
        assertTrue(RouteGeometry.None.isEmpty)
    }
}
