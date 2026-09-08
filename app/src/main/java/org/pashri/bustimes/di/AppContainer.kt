package org.pashri.bustimes.di

import android.content.Context
import org.pashri.bustimes.data.net.buildHttpClient
import org.pashri.bustimes.data.repo.BustimesRepository

/**
 * Manually constructed dependencies.
 *
 * A DI framework would add build time and indirection without buying anything
 * at this size; there is one client and one repository.
 */
class AppContainer(context: Context) {

    private val httpClient = buildHttpClient(context.cacheDir)

    val repository = BustimesRepository(httpClient)
}
