package com.netbanding.app.data.local

import kotlinx.serialization.Serializable

@Serializable
data class SeedIsp(
    val id: String,
    val name: String,
    val category: String = "broadband",
    val logo_asset: String,
    val website_url: String,
)

@Serializable
data class SeedPackage(
    val id: String,
    val isp_id: String,
    val name: String,
    val type: String = "broadband",
    val speed_mbps: Int? = null,
    val quota_mb: Int? = null,
    val validity_days: Int? = null,
    val contract_months: Int? = null,
    val base_price: Long,
    val tax_inclusive: Boolean,
    val device_rental_fee: Long = 0,
    val install_fee: Long? = null,
    val fup_note: String? = null,
    val promo_note: String? = null,
    val regions: List<String> = listOf("JAVA_ALL"),
    val source_url: String,
    val is_active: Boolean = true,
    val last_verified_at: String,
    val updated_at: String,
)

@Serializable
data class SeedCatalog(val isps: List<SeedIsp>, val packages: List<SeedPackage>)

@Serializable
data class SeedHistoryPoint(
    val price: Long,
    val tax_inclusive: Boolean,
    val recorded_at: String,
)
