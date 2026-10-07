package com.netbanding.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.prefs.UserPrefs
import com.netbanding.app.data.remote.DEFAULT_BASE_URL
import com.netbanding.app.data.remote.NetbandingApi
import com.netbanding.app.data.repo.PackageRepository
import com.netbanding.app.data.sync.SeedImporter
import com.netbanding.app.data.sync.SyncRepository
import com.netbanding.app.data.sync.SyncWorker
import com.netbanding.app.domain.usecase.CalculateTrueCost
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Manual dependency container. No Hilt: annotation processing costs build time
 * and a reflection-based container costs startup time. All properties are
 * `by lazy` so nothing is built before first use (cold-start lever).
 */
interface AppContainer {
    val calculateTrueCost: CalculateTrueCost
    val db: NetbandingDb
    val packageRepository: PackageRepository
    val userPrefs: UserPrefs
    val syncRepository: SyncRepository
    fun scheduleSync()
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val calculateTrueCost: CalculateTrueCost by lazy {
        CalculateTrueCost(ppnRate = 0.11)
    }
    override val db: NetbandingDb by lazy {
        Room.databaseBuilder(context.applicationContext, NetbandingDb::class.java, "netbanding.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    private val seedImporter: SeedImporter by lazy {
        SeedImporter(context.applicationContext, db, calculateTrueCost)
    }
    override val packageRepository: PackageRepository by lazy {
        PackageRepository(db, seedImporter)
    }
    override val userPrefs: UserPrefs by lazy {
        UserPrefs(context.applicationContext)
    }
    private val api: NetbandingApi by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .build()
            .create(NetbandingApi::class.java)
    }
    override val syncRepository: SyncRepository by lazy {
        val prefs = userPrefs
        SyncRepository(
            api = api,
            db = db,
            getState = { prefs.snapshot() },
            putState = { v, g, c, h -> prefs.updateAfterSync(v, g, c, h) },
            markChecked = { prefs.markChecked() },
        )
    }

    val workerFactory: SyncWorker.Factory by lazy {
        SyncWorker.Factory { syncRepository }
    }

    override fun scheduleSync() {
        SyncWorker.schedule(context.applicationContext)
    }
}
