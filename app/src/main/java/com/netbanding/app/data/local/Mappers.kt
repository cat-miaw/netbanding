package com.netbanding.app.data.local

import com.netbanding.app.domain.model.Package
import com.netbanding.app.domain.usecase.CalculateTrueCost

fun PackageWithIsp.toDomain(): Package {
    val monthly = monthly_total
    return Package(
        id = id,
        ispId = isp_id,
        ispName = isp_name,
        name = name,
        speedMbps = speed_mbps,
        basePrice = base_price,
        taxInclusive = tax_inclusive,
        deviceRentalFee = device_rental_fee,
        installFee = install_fee,
        monthlyTotal = monthly,
        pricePerMbps = speed_mbps?.takeIf { it > 0 }?.let { monthly / it },
        sourceUrl = source_url,
        lastVerifiedAt = last_verified_at,
        isFavorite = is_favorite,
    )
}

/** Shared import-time math: entities store monthlyTotal computed by the use case. */
fun monthlyTotalFor(
    calc: CalculateTrueCost,
    basePrice: Long,
    taxInclusive: Boolean,
    deviceRentalFee: Long,
): Long = calc(
    basePrice = basePrice,
    taxInclusive = taxInclusive,
    deviceRentalFee = deviceRentalFee,
).monthlyTotal
