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
}
