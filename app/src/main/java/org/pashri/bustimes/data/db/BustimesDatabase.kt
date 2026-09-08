package org.pashri.bustimes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's own data.
 *
 * Only ever holds what the user created. Everything fetched from bustimes.org
 * lives in the HTTP cache instead, so there is one answer to "is this stale"
 * rather than two caches with different rules.
 */
@Database(entities = [FavouriteStop::class], version = 1, exportSchema = false)
abstract class BustimesDatabase : RoomDatabase() {

    /** Starred stops. */
    abstract fun favouriteStops(): FavouriteStopDao

    companion object {

        /**
         * Opens the database.
         *
         * @param context used to locate the database file.
         * @return the database.
         */
        fun open(context: Context): BustimesDatabase = Room.databaseBuilder(
            context,
            BustimesDatabase::class.java,
            "bustimes.db",
        ).build()
    }
}
