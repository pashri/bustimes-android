package org.pashri.bustimes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.net.BoundingBox
import org.pashri.bustimes.ui.map.VehicleHighWaterMark

class VehicleHighWaterMarkTest {

    private val cambridge = BoundingBox(west = 0.10, south = 52.19, east = 0.14, north = 52.22)

    @Test
    fun `first viewport always needs a fetch`() {
        assertTrue(VehicleHighWaterMark().needsFetch(cambridge))
    }

    @Test
    fun `zooming in reuses what was already fetched`() {
        val mark = VehicleHighWaterMark()
        mark.record(cambridge, count = 12)

        val zoomedIn = BoundingBox(west = 0.11, south = 52.20, east = 0.13, north = 52.21)

        assertFalse("a contained viewport is already covered", mark.needsFetch(zoomedIn))
    }

    @Test
    fun `panning outside the fetched area needs a fetch`() {
        val mark = VehicleHighWaterMark()
        mark.record(cambridge, count = 12)

        val pannedEast = BoundingBox(west = 0.15, south = 52.19, east = 0.19, north = 52.22)

        assertTrue(mark.needsFetch(pannedEast))
    }

    @Test
    fun `a partially overlapping viewport needs a fetch`() {
        val mark = VehicleHighWaterMark()
        mark.record(cambridge, count = 12)

        val straddling = BoundingBox(west = 0.13, south = 52.19, east = 0.17, north = 52.22)

        assertTrue(mark.needsFetch(straddling))
    }

    @Test
    fun `a dense response is always refetched even when contained`() {
        val mark = VehicleHighWaterMark()
        mark.record(cambridge, count = 1_000)

        val zoomedIn = BoundingBox(west = 0.11, south = 52.20, east = 0.13, north = 52.21)

        // A very large response means the area is dense enough that containment
        // is no longer a safe reason to skip a refresh.
        assertTrue(mark.needsFetch(zoomedIn))
    }

    @Test
    fun `resetting forces the next viewport to refetch`() {
        val mark = VehicleHighWaterMark()
        mark.record(cambridge, count = 5)
        mark.reset()

        assertTrue(mark.needsFetch(cambridge))
    }

    @Test
    fun `a box contains itself`() {
        assertTrue(cambridge.contains(cambridge))
    }
}
