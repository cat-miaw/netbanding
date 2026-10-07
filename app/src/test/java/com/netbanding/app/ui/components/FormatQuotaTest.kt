package com.netbanding.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatQuotaTest {
    @Test fun wholeGb_keepsTrailingZero() {
        assertEquals("10 GB", formatQuota(10240))
        assertEquals("2 GB", formatQuota(2048))
    }

    @Test fun fractionalGb_usesComma() {
        assertEquals("5,5 GB", formatQuota(5632))
    }

    @Test fun `periode buckets`() {
        assertEquals("bln", periodeFor(null))
        assertEquals("bln", periodeFor(30))
        assertEquals("bln", periodeFor(28))
        assertEquals("mgg", periodeFor(21))
        assertEquals("mgg", periodeFor(7))
        assertEquals("hr", periodeFor(6))
        assertEquals("hr", periodeFor(1))
        assertEquals("Rp50.000/mgg", formatPricePeriode(50_000, 7))
    }
}
