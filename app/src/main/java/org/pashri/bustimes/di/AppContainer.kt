package org.pashri.bustimes.di

import android.content.Context
import org.pashri.bustimes.data.location.CoarseLocationProvider
import org.pashri.bustimes.data.net.buildHttpClient
import org.pashri.bustimes.data.prefs.CameraStore
import org.pashri.bustimes.data.repo.BustimesRepository
import org.pashri.bustimes.ui.map.MapViewModel

/**
 * Manually constructed dependencies.
 *
 * A DI framework would add build time and indirection without buying anything
 * at this size; there is one client and one repository.
 */
class AppContainer(context: Context) {

    private val httpClient = buildHttpClient(context.cacheDir)

    /** Reads from bustimes.org. */
    val repository = BustimesRepository(httpClient)

    /** Remembers where the map was last looking. */
    val cameraStore = CameraStore(context)

    /** Supplies the user's approximate position. */
    val locationProvider = CoarseLocationProvider(context)

    /** Builds the map screen's view model. */
    val mapViewModelFactory = MapViewModel.Factory(repository, cameraStore, locationProvider)
}
