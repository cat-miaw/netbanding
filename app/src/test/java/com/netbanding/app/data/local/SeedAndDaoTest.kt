package com.netbanding.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.netbanding.app.data.sync.SeedImporter
import com.netbanding.app.domain.usecase.CalculateTrueCost
import kotlinx.coroutines.flow.first
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
class SeedAndDaoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db: NetbandingDb = Room.inMemoryDatabaseBuilder(context, NetbandingDb::class.java)
        .allowMainThreadQueries().build()

    @After fun close() { db.close() }

    @Test fun seedImporter_loads18Packages() = runTest {
        val ok = SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        assertTrue(ok)
        assertEquals(18, db.packageDao().activeCount())
    }

    @Test fun sqlSort_cheapestFirst_andBudgetFilter() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        val all = dao.observePackages("JAVA_ALL", null, null, null, emptyList(), 0, "cheapest").first()
        assertEquals(18, all.size)
        val totals = all.map { it.monthly_total }
        assertEquals(totals.sorted(), totals)
        // Cheapest = FirstMedia Starter: 185_000 * 1.11 = 205_350 + install not monthly
        assertEquals(205_350, totals.first())
        val budget = dao.observePackages("JAVA_ALL", 300_000, null, null, emptyList(), 0, "cheapest").first()
        assertTrue(budget.isNotEmpty() && budget.all { it.monthly_total <= 300_000 })
    }

    @Test fun history_seededWithInitialPoint() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val pts = db.packageDao().observeHistory("biznet-home-150").first()
        assertEquals(1, pts.size)
        assertEquals(375000, pts.single().price)
    }

    @Test fun favorites_addRemoveReflectedInFlow() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        assertTrue(dao.observeFavorites().first().isEmpty())
        dao.addFavorite(FavoriteEntity("biznet-home-150", "2026-10-07T00:00:00Z"))
        assertEquals(listOf("biznet-home-150"), dao.observeFavorites().first().map { it.id })
        dao.removeFavorite("biznet-home-150")
        assertTrue(dao.observeFavorites().first().isEmpty())
    }
}
