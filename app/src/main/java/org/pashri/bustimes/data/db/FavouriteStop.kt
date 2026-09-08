package org.pashri.bustimes.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stop the user has starred.
 *
 * The name and position are copied in rather than looked up later, so the
 * favourites menu can be ordered by distance and labelled without a request:
 * the whole point of a favourite is that it is there the moment the app opens,
 * including with no signal.
 *
 * @property atcoCode the stop, and the natural primary key — a stop cannot be
 *   favourited twice.
 * @property name the stop's name as it read when starred.
 * @property latitude the stop's position, for ordering by distance.
 * @property longitude the stop's position, for ordering by distance.
 * @property addedAt when it was starred, used as a stable tiebreak and as the
 *   order to fall back on when there is no location fix.
 */
@Entity(tableName = "favourite_stops")
data class FavouriteStop(
    @PrimaryKey val atcoCode: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val addedAt: Long,
)
