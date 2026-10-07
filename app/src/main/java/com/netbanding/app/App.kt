package com.netbanding.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.netbanding.app.di.AppContainer
import com.netbanding.app.di.DefaultAppContainer

/**
 * Still does nothing at startup: WorkManager is configured lazily via
 * [Configuration.Provider] and every dependency builds on first use.
 */
class App : Application(), Configuration.Provider {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultAppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory((container as DefaultAppContainer).workerFactory)
            .build()
}

/** Convenience accessor. Cheap: no work happens until a property is touched. */
val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
