package org.pashri.bustimes.data.favourites

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.pashri.bustimes.data.db.FavouriteStop
import org.pashri.bustimes.data.db.FavouriteStopDao
import org.pashri.bustimes.data.location.DevicePosition
import kotlin.math.cos
import kotlin.math.hypot

/**
 * The user's starred stops.
 *
 * Ordering by distance happens here rather than in the query, because the
 * position to measure from is only known at the moment the menu is opened and
 * SQLite has no business doing trigonometry.
 */
class FavouritesRepository(private val dao: FavouriteStopDao) {

    /** Every starred stop, in the order they were added. */
    val favourites: Flow<List<FavouriteStop>> = dao.observeAll()

    /** The ATCO codes of starred stops, for deciding whether a star is filled. */
    val favouriteCodes: Flow<Set<String>> =
        dao.observeAll().map { stops -> stops.map { it.atcoCode }.toSet() }

    /**
     * Stars a stop.
     *
     * @param atcoCode the stop.
     * @param name its name as currently shown.
     * @param latitude its position.
     * @param longitude its position.
     */
    suspend fun add(atcoCode: String, name: String, latitude: Double, longitude: Double) {
        dao.insert(
            FavouriteStop(
                atcoCode = atcoCode,
                name = name,
                latitude = latitude,
                longitude = longitude,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Un-stars a stop.
     *
     * @param atcoCode the stop to remove.
     */
    suspend fun remove(atcoCode: String) {
        dao.delete(atcoCode)
    }

    companion object {

        /**
         * Puts favourites in the order they should appear in the menu.
         *
         * Nearest first, because the nearest stop is nearly always the one
         * being checked, and the menu draws the first entry closest to the
         * button so the least thumb travel reaches the most likely choice.
         *
         * Order is settled once when the menu opens rather than tracked
         * continuously: a list that reshuffles while it is open moves the
         * entry being reached for.
         *
         * @param favourites the starred stops.
         * @param from where the user is, or null when there is no fix.
         * @return the favourites ordered by distance, or unchanged when there
         *   is nothing to measure from.
         */
        fun orderedFrom(
            favourites: List<FavouriteStop>,
            from: DevicePosition?,
        ): List<FavouriteStop> {
            if (from == null) return favourites
            return favourites.sortedBy { stop -> metresBetween(from, stop) }
        }

        /** Rough distance in metres; only the ordering it implies is used. */
        private fun metresBetween(from: DevicePosition, stop: FavouriteStop): Double {
            val northing = (stop.latitude - from.latitude) * METRES_PER_DEGREE
            val easting = (stop.longitude - from.longitude) *
                METRES_PER_DEGREE * cos(Math.toRadians(from.latitude))
            return hypot(easting, northing)
        }

        private const val METRES_PER_DEGREE = 110_540.0
    }
}
