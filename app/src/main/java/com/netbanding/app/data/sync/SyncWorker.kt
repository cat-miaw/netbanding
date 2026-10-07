package com.netbanding.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.netbanding.app.data.notify.PriceDropMonitor
import java.util.concurrent.TimeUnit

/** Weekly background refresh, network required (blueprint 7.4). Deferred off the startup path. */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repo: SyncRepository,
    private val monitor: PriceDropMonitor,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return when (val r = repo.sync()) {
            is SyncResult.Updated -> {
                runCatching { monitor.checkAndNotify() }
                Result.success()
            }
            SyncResult.NoChange -> Result.success()
            SyncResult.NeedsAppUpdate -> Result.success()
            is SyncResult.Failed -> Result.retry()
        }
    }

    class Factory(
        private val repo: () -> SyncRepository,
        private val monitor: () -> PriceDropMonitor,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ) = if (workerClassName == SyncWorker::class.java.name) {
            SyncWorker(appContext, workerParameters, repo(), monitor())
        } else {
            null
        }
    }

    companion object {
        const val NAME = "weekly-sync"
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
