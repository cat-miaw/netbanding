package com.netbanding.app.data.sync

import com.netbanding.app.data.local.IspEntity
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.local.PackageEntity
import com.netbanding.app.data.local.PackageRegionEntity
import com.netbanding.app.data.local.PriceHistoryEntity
import com.netbanding.app.data.local.SeedCatalog
import com.netbanding.app.data.local.SeedHistoryPoint
import com.netbanding.app.data.local.monthlyTotalFor
import com.netbanding.app.data.prefs.FakeSyncStore
import com.netbanding.app.data.prefs.SyncState
import com.netbanding.app.data.remote.NetbandingApi
import com.netbanding.app.data.remote.RemoteManifest
import com.netbanding.app.domain.usecase.CalculateTrueCost
import androidx.room.withTransaction
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed interface SyncResult {
    data object NoChange : SyncResult
    data class Updated(val version: Int) : SyncResult
    data object NeedsAppUpdate : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/** Highest schema_version this app build understands (blueprint 4.1). */
const val MAX_SUPPORTED_SCHEMA = 1

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** True when manifest data is older than 14 days (blueprint 7.4 stale warning). */
fun isStale(generatedAt: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (generatedAt == null) return false
    return runCatching {
        val t = java.time.Instant.parse(generatedAt).toEpochMilli()
        nowMs - t > 14L * 24 * 60 * 60 * 1000
    }.getOrDefault(false)
}

/**
 * Blueprint 7.4 sync engine: manifest check -> sha256 verify -> ONE Room
 * transaction. Any error rolls back; old data always stays.
 */
class SyncRepository(
    private val api: NetbandingApi,
    private val db: NetbandingDb,
    private val getState: suspend () -> SyncState,
    private val putState: suspend (version: Int, generatedAt: String, catalogSha: String, historySha: String) -> Unit,
    private val markChecked: suspend () -> Unit,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor(
        api: NetbandingApi,
        db: NetbandingDb,
        store: FakeSyncStore,
    ) : this(api, db, store::get, store::put, {})

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val manifestBytes: ByteArray
        val manifest: RemoteManifest
        try {
            manifestBytes = api.manifest().bytes()
            manifest = json.decodeFromString<RemoteManifest>(manifestBytes.decodeToString())
        } catch (e: Exception) {
            return@withContext SyncResult.Failed("manifest: ${e.message}")
        }
        if (manifest.schema_version > MAX_SUPPORTED_SCHEMA) {
            runCatching { markChecked() }
            return@withContext SyncResult.NeedsAppUpdate
        }
        val prev = runCatching { getState() }.getOrDefault(SyncState())
        if (manifest.data_version <= prev.dataVersion && prev.dataVersion > 0) {
            runCatching { markChecked() }
            return@withContext SyncResult.NoChange
        }
        try {
            val catalogEntry = manifest.files["catalog"]
            val historyEntry = manifest.files["history"]
            val catalogBytes = if (catalogEntry != null && catalogEntry.sha256 == prev.catalogSha && prev.dataVersion > 0) {
                null // unchanged file: skip download
            } else {
                api.catalog().bytes()
            }
            val historyBytes = if (historyEntry != null && historyEntry.sha256 == prev.historySha && prev.dataVersion > 0) {
                null
            } else {
                api.history().bytes()
            }
            // Verify hashes before touching the DB.
            catalogBytes?.let {
                val want = catalogEntry?.sha256
                if (want != null && sha256Hex(it) != want) throw IllegalStateException("catalog sha256 mismatch")
            }
            historyBytes?.let {
                val want = historyEntry?.sha256
                if (want != null && sha256Hex(it) != want) throw IllegalStateException("history sha256 mismatch")
            }
            // Parse everything before the transaction so corrupt payloads never half-apply.
            val calc = CalculateTrueCost(ppnRate = manifest.ppn_rate)
            val catalog = json.decodeFromString<SeedCatalog>(
                (catalogBytes ?: api.catalog().bytes()).decodeToString(),
            )
            val history: Map<String, List<SeedHistoryPoint>> = if (historyBytes != null) {
                json.decodeFromString(historyBytes.decodeToString())
            } else {
                runCatching {
                    json.decodeFromString<Map<String, List<SeedHistoryPoint>>>(api.history().bytes().decodeToString())
                }.getOrDefault(emptyMap())
            }
            val isps = catalog.isps.map { IspEntity(it.id, it.name, it.category, it.logo_asset, it.website_url) }
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
            if (packages.isEmpty()) throw IllegalStateException("empty catalog")
            val regions = catalog.packages.flatMap { p -> p.regions.map { PackageRegionEntity(p.id, it) } }
            val historyRows = history.flatMap { (pkgId, points) ->
                points.map { PriceHistoryEntity(packageId = pkgId, price = it.price, taxInclusive = it.tax_inclusive, recordedAt = it.recorded_at) }
            }
            val catalogSha = catalogBytes?.let { sha256Hex(it) } ?: prev.catalogSha ?: ""
            val historySha = historyBytes?.let { sha256Hex(it) } ?: prev.historySha ?: ""
            db.withTransaction {
                db.packageDao().replaceAll(isps, packages, regions, historyRows)
            }
            putState(manifest.data_version, manifest.generated_at, catalogSha, historySha)
            SyncResult.Updated(manifest.data_version)
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "sync failed")
        }
    }
}
