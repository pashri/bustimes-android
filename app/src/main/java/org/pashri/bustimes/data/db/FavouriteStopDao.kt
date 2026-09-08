package org.pashri.bustimes.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Reads and writes starred stops. */
@Dao
interface FavouriteStopDao {

    /**
     * Every starred stop, oldest first.
     *
     * Emitted as a flow so the star in a stop's panel and the favourites menu
     * cannot disagree about what is starred.
     *
     * @return the favourites, in the order they were added.
     */
    @Query("SELECT * FROM favourite_stops ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<FavouriteStop>>

    /**
     * Stars a stop, replacing any existing row for it.
     *
     * @param stop the stop to star.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stop: FavouriteStop)

    /**
     * Un-stars a stop.
     *
     * @param atcoCode the stop to remove.
     */
    @Query("DELETE FROM favourite_stops WHERE atcoCode = :atcoCode")
    suspend fun delete(atcoCode: String)
}
