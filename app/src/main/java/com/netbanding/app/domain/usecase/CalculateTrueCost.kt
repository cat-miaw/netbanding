package com.netbanding.app.domain.usecase

import com.netbanding.app.domain.model.TrueCost
import kotlin.math.roundToLong

/**
 * Blueprint Section 5. Single implementation of True Cost; Room precomputes
 * monthlyTotal with this same math so SQL filters/sorts stay consistent.
 *
 * Device rental is treated as already final (see blueprint assumption).
 */
class CalculateTrueCost(private val ppnRate: Double = 0.11) {

    operator fun invoke(
        basePrice: Long,
        taxInclusive: Boolean,
        deviceRentalFee: Long = 0,
        installFee: Long? = 0,
        contractMonths: Int? = null,
        speedMbps: Int? = null,
    ): TrueCost {
        val taxedBase = if (taxInclusive) basePrice else (basePrice * (1 + ppnRate)).roundToLong()
        val monthlyTotal = taxedBase + deviceRentalFee
        val installUnknown = installFee == null
        val firstMonthTotal = monthlyTotal + (installFee ?: 0)
        val contractTotal = contractMonths?.let { monthlyTotal * it + (installFee ?: 0) }
        val pricePerMbps = speedMbps?.takeIf { it > 0 }?.let { monthlyTotal / it }
        return TrueCost(
            taxedBase = taxedBase,
            monthlyTotal = monthlyTotal,
            firstMonthTotal = firstMonthTotal,
            contractTotal = contractTotal,
            pricePerMbps = pricePerMbps,
            installUnknown = installUnknown,
        )
    }
}
