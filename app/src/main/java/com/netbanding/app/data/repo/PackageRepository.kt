package com.netbanding.app.data.repo

import com.netbanding.app.data.local.FavoriteEntity
import com.netbanding.app.data.local.IspEntity
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.local.toDomain
import com.netbanding.app.data.sync.SeedImporter
import com.netbanding.app.domain.model.Package
import com.netbanding.app.domain.model.PricePoint
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * UI reads ONLY from Room via Flow (blueprint rule 5). No networking yet;
 * Phase 3 adds SyncRepository beside this class.
 */
class PackageRepository(
    private val db: NetbandingDb,
    private val seedImporter: SeedImporter,
) {
    fun observePackages(
        region: String = "JAVA_ALL",
        type: String? = null,
        maxMonthly: Long? = null,
        minSpeed: Int? = null,
        query: String? = null,
        ispIds: Set<String> = emptySet(),
        periods: Set<String> = emptySet(),
        sort: String = "cheapest",
    ): Flow<List<Package>> =
        db.packageDao().observePackages(
            region, type, maxMonthly, minSpeed, query?.takeIf { it.isNotBlank() },
            ispIds.toList(), ispIds.size, periods.toList(), periods.size, sort,
        )
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { withContext(Dispatchers.IO) { seedImporter.importIfEmpty() } }

    fun observeIsps(): Flow<List<IspEntity>> = db.packageDao().observeIsps()
        .onStart { withContext(Dispatchers.IO) { seedImporter.importIfEmpty() } }

    fun observeHistory(id: String): Flow<List<PricePoint>> =
        db.packageDao().observeHistory(id).map { rows ->
            rows.map { PricePoint(it.price, it.taxInclusive, it.recordedAt) }
        }

    fun observeFavorites(): Flow<List<Package>> =
        db.packageDao().observeFavorites().map { rows -> rows.map { it.toDomain() } }

    fun observeByIds(ids: Set<String>): Flow<List<Package>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.packageDao().observeByIds(ids.toList()).map { rows -> rows.map { it.toDomain() } }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (isFavorite) db.packageDao().removeFavorite(id)
        else db.packageDao().addFavorite(FavoriteEntity(id, Instant.now().toString()))
    }
}
