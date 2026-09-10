package org.pashri.bustimes

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.model.Freshness
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.model.VehicleDetail
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.ui.map.MapDecorations
import org.pashri.bustimes.ui.map.MapGeoJson
import org.pashri.bustimes.ui.map.SiblingRoute

class MapGeoJsonTest {

    private fun bus(
        id: Long,
        serviceId: Long? = 1L,
        colour: String? = null,
        css: String? = null,
    ) = Vehicle(
        id = id,
        coordinates = listOf(0.1, 52.2),
        serviceId = serviceId,
        vehicle = VehicleDetail(colour = colour, css = css),
    )

    @Test
    fun `a flat livery colour is used as it is`() {
        assertEquals("#1B5E20", MapGeoJson.liveryColour(bus(1, colour = "#1B5E20")))
    }

    @Test
    fun `a single-colour css value is accepted`() {
        assertEquals("#AABBCC", MapGeoJson.liveryColour(bus(1, css = "#AABBCC")))
    }

    @Test
    fun `a gradient css value is not mistaken for a colour`() {
        val gradient = "linear-gradient(to right,#fff 50%,#000 50%)"

        val colour = MapGeoJson.liveryColour(bus(1, css = gradient))

        assertFalse(colour == gradient)
        assertTrue(colour.startsWith("#"))
        assertEquals(7, colour.length)
    }

    @Test
    fun `a bus with no livery still gets a drawable colour`() {
        val colour = MapGeoJson.liveryColour(bus(1))

        assertTrue(colour.startsWith("#"))
        assertEquals(7, colour.length)
    }

    @Test
    fun `only buses off the focused service are flagged as dimmed`() {
        val buses = listOf(bus(1, serviceId = 10), bus(2, serviceId = 10), bus(3, serviceId = 99))

        val collection = MapGeoJson.vehicles(
            buses,
            selectedId = 1,
            nowMillis = 0L,
            dimOtherServices = 10,
        )
        val dimmed = collection.features()!!.map {
            it.getBooleanProperty(MapGeoJson.PROPERTY_DIMMED)
        }

        // Siblings on the focused service keep their colours; everything else
        // is played down so the route can be read.
        assertEquals(listOf(false, false, true), dimmed)
    }

    @Test
    fun `nothing is dimmed when no service is focused`() {
        val buses = listOf(bus(1, serviceId = 10), bus(2, serviceId = 99))

        val collection = MapGeoJson.vehicles(
            buses,
            selectedId = null,
            nowMillis = 0L,
            dimOtherServices = null,
        )

        assertTrue(
            collection.features()!!.none { it.getBooleanProperty(MapGeoJson.PROPERTY_DIMMED) },
        )
    }

    @Test
    fun `a stale position is faded and loses its arrow, a fresh one neither`() {
        val fresh = bus(1).copy(datetime = "2026-09-10T13:39:30+01:00")
        val stale = bus(2).copy(datetime = "2026-09-10T13:35:00+01:00")
        val now = OffsetDateTime.parse("2026-09-10T13:40:00+01:00")
            .toInstant().toEpochMilli()

        val features = MapGeoJson.vehicles(
            listOf(fresh, stale),
            selectedId = null,
            nowMillis = now,
        ).features()!!

        // 30s old, then 300s old: full opacity with an arrow, against the
        // five-minute point on the ramp with the arrow withdrawn.
        assertEquals(1.0f, features[0].getNumberProperty(MapGeoJson.PROPERTY_OPACITY).toFloat(), 0.005f)
        assertFalse(features[0].getBooleanProperty(MapGeoJson.PROPERTY_STALE))
        assertEquals(
            0.7562f,
            features[1].getNumberProperty(MapGeoJson.PROPERTY_OPACITY).toFloat(),
            0.005f,
        )
        assertTrue(features[1].getBooleanProperty(MapGeoJson.PROPERTY_STALE))
    }

    @Test
    fun `a bus with no timestamp is drawn as current`() {
        val now = OffsetDateTime.parse("2026-09-10T13:40:00+01:00")
            .toInstant().toEpochMilli()

        val features = MapGeoJson.vehicles(
            listOf(bus(1)),
            selectedId = null,
            nowMillis = now,
        ).features()!!

        // Never observed in a real response, but treating a missing timestamp
        // as ancient would fade out an entire operator on a feed change.
        assertEquals(1.0f, features[0].getNumberProperty(MapGeoJson.PROPERTY_OPACITY).toFloat(), 0.005f)
        assertFalse(features[0].getBooleanProperty(MapGeoJson.PROPERTY_STALE))
    }

    @Test
    fun `every bus in a real bbox response can be aged`() {
        val vehicles = BustimesRepository.defaultJson
            .decodeFromString<List<Vehicle>>(fixture("vehicles_bbox.json"))
        val now = OffsetDateTime.parse("2026-09-10T13:40:00+01:00")
            .toInstant().toEpochMilli()

        val features = MapGeoJson.vehicles(vehicles, selectedId = null, nowMillis = now)
            .features()!!

        // The whole map half of this feature rests on the bbox form carrying
        // a timestamp, so an upstream change that dropped it should fail here
        // rather than quietly drawing a stale fleet at full confidence.
        assertEquals(95, features.size)
        assertTrue(vehicles.all { Freshness.ageSeconds(it.datetime, now) != null })
    }

    @Test
    fun `each sibling route carries its own colour`() {
        val routes = listOf(
            SiblingRoute(1, listOf(listOf(listOf(0.1, 52.2), listOf(0.2, 52.3))), "#FF0000"),
            SiblingRoute(2, listOf(listOf(listOf(0.3, 52.4), listOf(0.4, 52.5))), "#00FF00"),
        )

        val collection = MapGeoJson.siblingRoutes(routes)
        val colours = collection.features()!!.map {
            it.getStringProperty(MapGeoJson.PROPERTY_COLOUR)
        }

        assertEquals(listOf("#FF0000", "#00FF00"), colours)
    }

    @Test
    fun `a sibling leg with a single point is not drawn`() {
        val routes = listOf(SiblingRoute(1, listOf(listOf(listOf(0.1, 52.2))), "#FF0000"))

        assertTrue(MapGeoJson.siblingRoutes(routes).features()!!.isEmpty())
    }

    @Test
    fun `no siblings means nothing to draw`() {
        assertTrue(MapGeoJson.siblingRoutes(emptyList()).features()!!.isEmpty())
    }
}

class MapDecorationsTest {

    @Test
    fun `clearing a selection removes everything drawn for it`() {
        val decorated = MapDecorations(
            stops = emptyList(),
            vehicles = emptyList(),
            routeLegs = listOf(listOf(listOf(0.1, 52.2), listOf(0.2, 52.3))),
            routeIsApproximate = true,
            routeDimmed = true,
            selectedVehicleId = 7L,
            selectedStopAtco = "0500CCITY001",
            focusedServiceId = 8007L,
            siblingRoutes = listOf(SiblingRoute(1, emptyList(), "#FF0000")),
        )

        val cleared = decorated.withoutSelection()

        // Every one of these was a chance to leave something behind. Leaving
        // focusedServiceId set greyed out every bus with nothing selected.
        assertTrue(cleared.routeLegs.isEmpty())
        assertFalse(cleared.routeIsApproximate)
        assertFalse(cleared.routeDimmed)
        assertEquals(null, cleared.selectedVehicleId)
        assertEquals(null, cleared.selectedStopAtco)
        assertEquals(null, cleared.focusedServiceId)
        assertTrue(cleared.siblingRoutes.isEmpty())
        assertTrue(cleared.routeStops.isEmpty())
    }

    @Test
    fun `clearing a selection keeps the stops and buses on the map`() {
        val decorated = MapDecorations(vehicles = listOf(dimmableBus()), focusedServiceId = 1L)

        val cleared = decorated.withoutSelection()

        assertEquals(1, cleared.vehicles.size)
        assertEquals(null, cleared.focusedServiceId)
    }

    @Test
    fun `clearing a selection keeps the clock the buses are aged against`() {
        // Dropping it would reset every position to an unknown age, so
        // closing the sheet would redraw a stale fleet as current until the
        // next tick — the same class of bug as the fields above.
        val decorated = MapDecorations(
            vehicles = listOf(dimmableBus()),
            selectedVehicleId = 1L,
            nowMillis = 1_757_509_200_000L,
        )

        assertEquals(1_757_509_200_000L, decorated.withoutSelection().nowMillis)
    }

    private fun dimmableBus() = Vehicle(id = 1, coordinates = listOf(0.1, 52.2), serviceId = 1)
}
