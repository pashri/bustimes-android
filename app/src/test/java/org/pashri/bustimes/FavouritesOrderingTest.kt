package org.pashri.bustimes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pashri.bustimes.data.db.FavouriteStop
import org.pashri.bustimes.data.favourites.FavouritesRepository
import org.pashri.bustimes.data.location.DevicePosition

class FavouritesOrderingTest {

    private fun stop(name: String, lat: Double, lon: Double, added: Long = 0) =
        FavouriteStop(atcoCode = name, name = name, latitude = lat, longitude = lon, addedAt = added)

    private val cambridge = DevicePosition(latitude = 52.2053, longitude = 0.1190)

    @Test
    fun `the nearest stop comes first`() {
        val far = stop("Ely", 52.3990, 0.2620)
        val near = stop("Bene't Street", 52.2043, 0.1180)
        val middling = stop("Trumpington", 52.1700, 0.1100)

        val ordered = FavouritesRepository.orderedFrom(listOf(far, middling, near), cambridge)

        assertEquals(listOf("Bene't Street", "Trumpington", "Ely"), ordered.map { it.name })
    }

    @Test
    fun `without a fix the order they were added is kept`() {
        val first = stop("first", 52.4, 0.3, added = 1)
        val second = stop("second", 52.2, 0.1, added = 2)

        val ordered = FavouritesRepository.orderedFrom(listOf(first, second), from = null)

        // Falling back to distance-free order means the menu still works with
        // location refused or unavailable, just without the nearest-first help.
        assertEquals(listOf("first", "second"), ordered.map { it.name })
    }

    @Test
    fun `ordering does not lose or duplicate anything`() {
        val stops = listOf(
            stop("a", 52.30, 0.20),
            stop("b", 52.10, 0.05),
            stop("c", 52.21, 0.12),
        )

        val ordered = FavouritesRepository.orderedFrom(stops, cambridge)

        assertEquals(stops.size, ordered.size)
        assertEquals(stops.map { it.name }.toSet(), ordered.map { it.name }.toSet())
    }

    @Test
    fun `longitude is scaled by latitude so east-west distance is not overstated`() {
        // A degree of longitude is much shorter than a degree of latitude at
        // this latitude; without the cosine term the eastern stop would be
        // judged the further away.
        val east = stop("east", 52.2053, 0.2390)   // 0.12 deg of longitude
        val north = stop("north", 52.3053, 0.1190) // 0.10 deg of latitude

        val ordered = FavouritesRepository.orderedFrom(listOf(north, east), cambridge)

        assertEquals(listOf("east", "north"), ordered.map { it.name })
    }

    @Test
    fun `an empty list orders to an empty list`() {
        assertEquals(emptyList<FavouriteStop>(), FavouritesRepository.orderedFrom(emptyList(), cambridge))
    }

    @Test
    fun `a single favourite is returned unchanged`() {
        val only = listOf(stop("only", 52.0, 0.0))

        assertEquals(only, FavouritesRepository.orderedFrom(only, cambridge))
    }
}
