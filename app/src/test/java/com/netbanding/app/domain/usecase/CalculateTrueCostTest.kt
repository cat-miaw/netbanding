package com.netbanding.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateTrueCostTest {
    private val calc = CalculateTrueCost(ppnRate = 0.11)

    @Test fun taxInclusive_monthlyEqualsBase() {
        val r = calc(basePrice = 277_500, taxInclusive = true)
        assertEquals(277_500, r.taxedBase)
        assertEquals(277_500, r.monthlyTotal)
    }

    @Test fun taxExclusive_addsPpn11() {
        // 300_000 * 1.11 = 333_000
        val r = calc(basePrice = 300_000, taxInclusive = false)
        assertEquals(333_000, r.taxedBase)
        assertEquals(333_000, r.monthlyTotal)
    }

    @Test fun rentalAdded_nullInstall_flagged() {
        val r = calc(basePrice = 375_000, taxInclusive = false, deviceRentalFee = 50_000, installFee = null)
        // 375_000 * 1.11 = 416_250 + 50_000 = 466_250
        assertEquals(466_250, r.monthlyTotal)
        assertEquals(true, r.installUnknown)
        assertEquals(466_250, r.firstMonthTotal)
    }

    @Test fun contractTotal_includesInstallOnce() {
        val r = calc(basePrice = 229_000, taxInclusive = false, installFee = 0, contractMonths = 12)
        // 229_000 * 1.11 = 254_190
        assertEquals(254_190L, r.monthlyTotal)
        assertEquals(254_190L * 12, r.contractTotal)
        assertEquals(254_190L, r.firstMonthTotal)
    }

    @Test fun pricePerMbps_nullWhenSpeedUnknown() {
        val withSpeed = calc(basePrice = 277_500, taxInclusive = true, speedMbps = 150)
        assertEquals(1_850L, withSpeed.pricePerMbps)
        val without = calc(basePrice = 277_500, taxInclusive = true, speedMbps = null)
        assertEquals(null, without.pricePerMbps)
    }
}
