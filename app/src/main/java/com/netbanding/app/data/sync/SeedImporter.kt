package com.netbanding.app.data.sync

import android.content.Context
import com.netbanding.app.data.local.IspEntity
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.local.PackageEntity
import com.netbanding.app.data.local.PackageRegionEntity
import com.netbanding.app.data.local.PriceHistoryEntity
import com.netbanding.app.data.local.SeedCatalog
import com.netbanding.app.data.local.SeedHistoryPoint
import com.netbanding.app.data.local.monthlyTotalFor
import com.netbanding.app.domain.usecase.CalculateTrueCost
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** First launch: loads bundled seed catalog into Room. Blocking <500ms for 18 rows. */
class SeedImporter(
    private val context: Context,
    private val db: NetbandingDb,
    private val calc: CalculateTrueCost = CalculateTrueCost(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun importIfEmpty(): Boolean = withContext(Dispatchers.IO) {
        val dao = db.packageDao()
        if (dao.activeCount() > 0) return@withContext false
        val catalog = json.decodeFromString<SeedCatalog>(
            context.assets.open("seed/catalog.json").bufferedReader().readText(),
        )
        val history: Map<String, List<SeedHistoryPoint>> = runCatching {
            json.decodeFromString<Map<String, List<SeedHistoryPoint>>>(
                context.assets.open("seed/history.json").bufferedReader().readText(),
            )
        }.getOrDefault(emptyMap())

        val isps = catalog.isps.map {
            IspEntity(it.id, it.name, it.category, it.logo_asset, it.website_url)
        }
        val packages = catalog.packages.map {
            PackageEntity(
                id = it.id, ispId = it.isp_id, type = it.type, name = it.name,
                speedMbps = it.speed_mbps, quotaMb = it.quota_mb,
                validityDays = it.validity_days, contractMonths = it.contract_months,
                basePrice = it.base_price, taxInclusive = it.tax_inclusive,
                deviceRentalFee = it.device_rental_fee, installFee = it.install_fee,
                fupNote = it.fup_note, promoNote = it.promo_note,
                sourceUrl = it.source_url, isActive = it.is_active,
                lastVerifiedAt = it.last_verified_at, updatedAt = it.updated_at,
                monthlyTotal = monthlyTotalFor(calc, it.base_price, it.tax_inclusive, it.device_rental_fee),
            )
        }
        val regions = catalog.packages.flatMap { p ->
            p.regions.map { PackageRegionEntity(p.id, it) }
        }
        val historyRows = history.flatMap { (pkgId, points) ->
            points.map { PriceHistoryEntity(packageId = pkgId, price = it.price, taxInclusive = it.tax_inclusive, recordedAt = it.recorded_at) }
        }
        db.withTransaction {
            dao.replaceAll(isps, packages, regions, historyRows)
        }
        true
    }
}
