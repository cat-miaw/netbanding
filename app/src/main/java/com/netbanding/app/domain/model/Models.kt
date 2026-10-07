package com.netbanding.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Isp(
    val id: String,
    val name: String,
    val logoAsset: String,
    val websiteUrl: String,
)

@Immutable
data class Package(
    val id: String,
    val ispId: String,
    val ispName: String,
    val name: String,
    val speedMbps: Int?,
    val basePrice: Long,
    val taxInclusive: Boolean,
    val deviceRentalFee: Long,
    val installFee: Long?,
    val monthlyTotal: Long,
    val pricePerMbps: Long?,
    val sourceUrl: String,
    val lastVerifiedAt: String,
    val isFavorite: Boolean = false,
)

/** Blueprint Section 5 output. Money is Long IDR everywhere, never Float/Double. */
@Immutable
data class TrueCost(
    val taxedBase: Long,
    val monthlyTotal: Long,
    val firstMonthTotal: Long,
    val contractTotal: Long?,
    val pricePerMbps: Long?,
    val installUnknown: Boolean,
)

@Immutable
data class PricePoint(
    val price: Long,
    val taxInclusive: Boolean,
    val recordedAt: String,
)
