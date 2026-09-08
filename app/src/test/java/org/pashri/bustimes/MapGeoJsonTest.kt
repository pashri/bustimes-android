package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pashri.bustimes.data.model.Vehicle
import org.pashri.bustimes.data.model.VehicleDetail
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

        val collection = MapGeoJson.vehicles(buses, selectedId = 1, dimOtherServices = 10)
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

        val collection = MapGeoJson.vehicles(buses, selectedId = null, dimOtherServices = null)

        assertTrue(
            collection.features()!!.none { it.getBooleanProperty(MapGeoJson.PROPERTY_DIMMED) },
        )
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

    private fun dimmableBus() = Vehicle(id = 1, coordinates = listOf(0.1, 52.2), serviceId = 1)
}
