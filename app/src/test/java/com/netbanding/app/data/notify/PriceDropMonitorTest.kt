package com.netbanding.app.data.notify

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.netbanding.app.data.local.FavoriteEntity
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.prefs.UserPrefs
import com.netbanding.app.data.sync.SeedImporter
import com.netbanding.app.domain.usecase.CalculateTrueCost
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PriceDropMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db: NetbandingDb = Room.inMemoryDatabaseBuilder(context, NetbandingDb::class.java)
        .allowMainThreadQueries().build()

    @After fun close() { db.close() }

    private suspend fun seedAndFavorite() {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        db.packageDao().addFavorite(FavoriteEntity("biznet-home-150", "t"))
    }

    private suspend fun setTotal(id: String, total: Long) {
        db.openHelper.writableDatabase.execSQL(
            "UPDATE packages SET monthly_total = ? WHERE id = ?",
            arrayOf<Any>(total, id),
        )
    }

    @Test fun firstRun_recordsBaselineWithoutNotify() = runTest {
        seedAndFavorite()
        val seen = mutableListOf<List<PriceDrop>>()
        val drops = PriceDropMonitor(context, db, UserPrefs(context)) { seen.add(it) }
            .checkAndNotify()
        assertTrue(drops.isEmpty() && seen.isEmpty())
    }

    @Test fun drop_notifiesOnce_thenResetsBaseline() = runTest {
        seedAndFavorite()
        val prefs = UserPrefs(context)
        val seen = mutableListOf<List<PriceDrop>>()
        val monitor = PriceDropMonitor(context, db, prefs) { seen.add(it) }
        monitor.checkAndNotify()
        setTotal("biznet-home-150", 300_000) // was 466_250 (375rb ex-PPN + 50rb rental)
        val drops = monitor.checkAndNotify()
        assertEquals(1, drops.size)
        assertEquals(1, seen.size)
        assertEquals(466_250, drops.single().oldTotal)
        assertEquals(300_000, drops.single().newTotal)
        assertTrue(monitor.checkAndNotify().isEmpty())
        assertEquals(1, seen.size)
    }

    @Test fun increase_neverNotifies() = runTest {
        seedAndFavorite()
        val seen = mutableListOf<List<PriceDrop>>()
        val monitor = PriceDropMonitor(context, db, UserPrefs(context)) { seen.add(it) }
        monitor.checkAndNotify()
        setTotal("biznet-home-150", 999_999)
        assertTrue(monitor.checkAndNotify().isEmpty() && seen.isEmpty())
    }
}
