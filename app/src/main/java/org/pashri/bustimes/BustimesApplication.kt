package org.pashri.bustimes

import android.app.Application
import org.pashri.bustimes.data.diagnostics.installCrashLogging
import org.pashri.bustimes.di.AppContainer

/**
 * Application entry point. Owns the single [AppContainer]; there is no DI
 * framework because manual construction is legible at this size.
 */
class BustimesApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashLogging(container.crashLog)
    }
}
