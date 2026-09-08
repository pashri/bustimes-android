package org.pashri.bustimes.di

import android.content.Context
import org.pashri.bustimes.data.db.BustimesDatabase
import org.pashri.bustimes.data.diagnostics.CrashLog
import org.pashri.bustimes.data.favourites.FavouritesRepository
import org.pashri.bustimes.data.location.LocationProvider
import org.pashri.bustimes.data.net.buildHttpClient
import org.pashri.bustimes.data.prefs.CameraStore
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.ui.map.MapViewModel
import org.pashri.bustimes.ui.timetable.TimetableViewModel

/**
 * Manually constructed dependencies.
 *
 * A DI framework would add build time and indirection without buying anything
 * at this size; there is one client and one repository.
 */
class AppContainer(context: Context) {

    private val httpClient = buildHttpClient(context.cacheDir)

    private val database = BustimesDatabase.open(context)

    /** The user's starred stops. */
    val favourites = FavouritesRepository(database.favouriteStops())

    /** Records crashes so a rare one can be read after it happens. */
    val crashLog = CrashLog(context.filesDir)

    /** Reads from bustimes.org. */
    val repository = BustimesRepository(httpClient)

    /** Remembers where the map was last looking. */
    val cameraStore = CameraStore(context)

    /** Supplies the user's approximate position. */
    val locationProvider = LocationProvider(context)

    /** Builds the map screen's view model. */
    val mapViewModelFactory =
        MapViewModel.Factory(repository, cameraStore, locationProvider, favourites)

    /**
     * Builds the timetable screen's view model.
     *
     * @param serviceId the service whose timetable to load.
     * @return a factory bound to that service.
     */
    fun timetableViewModelFactory(serviceId: Long): TimetableViewModel.Factory =
        TimetableViewModel.Factory(repository, serviceId)
}
