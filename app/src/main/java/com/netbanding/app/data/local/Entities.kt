package com.netbanding.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "isps")
data class IspEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "broadband",
    @ColumnInfo(name = "logo_asset") val logoAsset: String,
    @ColumnInfo(name = "website_url") val websiteUrl: String,
)

@Entity(
    tableName = "packages",
    foreignKeys = [ForeignKey(
        entity = IspEntity::class,
        parentColumns = ["id"],
        childColumns = ["isp_id"],
    )],
    indices = [Index("isp_id"), Index("type"), Index("monthly_total")],
)
data class PackageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "isp_id") val ispId: String,
    val type: String = "broadband",
    val name: String,
    @ColumnInfo(name = "speed_mbps") val speedMbps: Int?,
    @ColumnInfo(name = "quota_mb") val quotaMb: Int?,
    @ColumnInfo(name = "validity_days") val validityDays: Int?,
    @ColumnInfo(name = "contract_months") val contractMonths: Int?,
    @ColumnInfo(name = "base_price") val basePrice: Long,
    @ColumnInfo(name = "tax_inclusive") val taxInclusive: Boolean,
    @ColumnInfo(name = "device_rental_fee") val deviceRentalFee: Long,
    @ColumnInfo(name = "install_fee") val installFee: Long?,
    @ColumnInfo(name = "fup_note") val fupNote: String?,
    @ColumnInfo(name = "promo_note") val promoNote: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "last_verified_at") val lastVerifiedAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    /** Precomputed via CalculateTrueCost so budget filters/sorts run in SQL. */
    @ColumnInfo(name = "monthly_total") val monthlyTotal: Long,
)

@Entity(
    tableName = "package_regions",
    primaryKeys = ["package_id", "region_code"],
    foreignKeys = [ForeignKey(
        entity = PackageEntity::class,
        parentColumns = ["id"],
        childColumns = ["package_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("region_code")],
)
data class PackageRegionEntity(
    @ColumnInfo(name = "package_id") val packageId: String,
    @ColumnInfo(name = "region_code") val regionCode: String,
)

@Entity(
    tableName = "price_history",
    foreignKeys = [ForeignKey(
        entity = PackageEntity::class,
        parentColumns = ["id"],
        childColumns = ["package_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("package_id")],
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_id") val packageId: String,
    val price: Long,
    @ColumnInfo(name = "tax_inclusive") val taxInclusive: Boolean,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "package_id") val packageId: String,
    @ColumnInfo(name = "added_at") val addedAt: String,
)
