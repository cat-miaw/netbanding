package com.netbanding.app.ui.compare

import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.components.pageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pkg(id: String, total: Long, quotaMb: Int? = null, validity: Int? = null) = Package(
    id = id, ispId = "x", ispName = "X", ispWebsite = "", name = id,
    speedMbps = null, quotaMb = quotaMb, validityDays = validity,
    basePrice = total, taxInclusive = true, deviceRentalFee = 0,
    installFee = null, monthlyTotal = total, pricePerMbps = null,
    sourceUrl = "", lastVerifiedAt = "", isFavorite = false,
)

class CompareLogicTest {
    @Test fun totalComparable_sameCycle() {
        assertTrue(totalComparable(listOf(pkg("a", 5_000, validity = 1), pkg("b", 9_000, validity = 1))))
        assertTrue(totalComparable(listOf(pkg("a", 5_000), pkg("b", 9_000))))
    }

    @Test fun totalComparable_mixedCycles() {
        assertFalse(totalComparable(listOf(pkg("a", 5_600, validity = 1), pkg("b", 15_000, validity = 7))))
        assertFalse(totalComparable(listOf(pkg("a", 5_600, validity = 1), pkg("b", 300_000))))
    }

    @Test fun longestDays_broadbandBeatsDaily() {
        assertEquals(30, longestDays(listOf(pkg("a", 5_600, validity = 1), pkg("b", 300_000))))
        assertEquals(7, longestDays(listOf(pkg("a", 5_600, validity = 1), pkg("b", 15_000, validity = 7))))
    }

    @Test fun bestPerGb_cheapestPerMbWins() {
        // 2 GB @5600 = 2800/GB beats 2.5 GB @15000 = 6000/GB.
        val items = listOf(pkg("a", 5_600, quotaMb = 2048), pkg("b", 15_000, quotaMb = 2560))
        assertEquals(5_600 / 2048.0, bestPerGb(items)!!, 0.001)
    }

    @Test fun bestPerGb_noneWithoutQuota() {
        assertEquals(null, bestPerGb(listOf(pkg("a", 5_000), pkg("b", 9_000))))
    }

    @Test fun pageWindow_firstPages() {
        assertEquals(listOf(0, 1, null, 9), pageWindow(0, 10))
    }

    @Test fun pageWindow_middle() {
        assertEquals(listOf(0, null, 4, 5, 6, null, 9), pageWindow(5, 10))
    }

    @Test fun pageWindow_small() {
        assertEquals(listOf(0, 1, 2), pageWindow(1, 3))
        assertEquals(listOf(0), pageWindow(0, 1))
    }
}
