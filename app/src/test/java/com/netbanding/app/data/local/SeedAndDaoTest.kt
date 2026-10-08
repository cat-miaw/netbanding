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
        assertEquals(41, db.packageDao().activeCount())
    }

    @Test fun sqlSort_cheapestFirst_andBudgetFilter() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        val all = dao.observePackages("JAVA_ALL", null, null, null, null, emptyList(), 0, emptyList(), 0, "cheapest").first()
        assertEquals(41, all.size)
        val totals = all.map { it.monthly_total }
        assertEquals(totals.sorted(), totals)
        // Cheapest overall = XL Xtra Kuota 2GB: Rp5.600 incl. PPN.
        assertEquals(5_600, totals.first())
        val budget = dao.observePackages("JAVA_ALL", null, 300_000, null, null, emptyList(), 0, emptyList(), 0, "cheapest").first()
        assertTrue(budget.isNotEmpty() && budget.all { it.monthly_total <= 300_000 })
    }

    @Test fun typeFilter_broadbandVsCellular() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        val bb = dao.observePackages("JAVA_ALL", "broadband", null, null, null, emptyList(), 0, emptyList(), 0, "cheapest").first()
        val cell = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, emptyList(), 0, "cheapest").first()
        assertEquals(12, bb.size)
        assertEquals(29, cell.size)
        assertTrue(cell.all { it.monthly_total >= 5_000 })
    }

    @Test fun perGbSort_ordersByPricePerQuota() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        val rows = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, emptyList(), 0, "pergb").first()
        assertEquals(29, rows.size)
        val ratios = rows.map { it.monthly_total.toDouble() / (it.quota_mb ?: 1) }
        assertEquals(ratios.sorted(), ratios)
    }

    @Test fun periodFilter_buckets() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        val daily = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, listOf("daily"), 1, "cheapest").first()
        assertTrue(daily.isNotEmpty() && daily.all { (it.validity_days ?: 99) <= 6 })
        val weekly = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, listOf("weekly"), 1, "cheapest").first()
        assertTrue(weekly.isNotEmpty() && weekly.all { (it.validity_days ?: 0) in 7..21 })
        val both = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, listOf("daily", "weekly"), 2, "cheapest").first()
        assertEquals(daily.size + weekly.size, both.size)
    }

    @Test fun history_seededWithInitialPoint() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val pts = db.packageDao().observeHistory("biznet-home-150").first()
        assertEquals(1, pts.size)
        assertEquals(375000, pts.single().price)
    }

    @Test fun paging_limitOffsetAndCountAgree() = runTest {
        SeedImporter(context, db, CalculateTrueCost()).importIfEmpty()
        val dao = db.packageDao()
        val total = dao.observeCount("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, emptyList(), 0).first()
        assertEquals(29, total)
        val p1 = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, emptyList(), 0, "cheapest", 15, 0).first()
        val p2 = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, emptyList(), 0, "cheapest", 15, 15).first()
        assertEquals(15, p1.size)
        assertEquals(14, p2.size)
        assertTrue((p1 + p2).map { it.id }.toSet().size == 29)
        // Same order as the unpaged query.
        val all = dao.observePackages("JAVA_ALL", "cellular", null, null, null, emptyList(), 0, emptyList(), 0, "cheapest").first()
        assertEquals(all.map { it.id }, (p1 + p2).map { it.id })
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
