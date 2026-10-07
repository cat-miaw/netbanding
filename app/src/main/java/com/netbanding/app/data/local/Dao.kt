package com.netbanding.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class PackageWithIsp(
    val id: String,
    val isp_id: String,
    val isp_name: String,
    val name: String,
    val speed_mbps: Int?,
    val base_price: Long,
    val tax_inclusive: Boolean,
    val device_rental_fee: Long,
    val install_fee: Long?,
    val monthly_total: Long,
    val source_url: String,
    val last_verified_at: String,
    val is_favorite: Boolean,
)

@Dao
interface PackageDao {
    /** Filtering/sorting in SQL per blueprint rule 6. Region join handles JAVA_ALL. */
    @Query(
        """
        SELECT p.id, p.isp_id, i.name AS isp_name, p.name, p.speed_mbps, p.base_price,
               p.tax_inclusive, p.device_rental_fee, p.install_fee, p.monthly_total,
               p.source_url, p.last_verified_at,
               (f.package_id IS NOT NULL) AS is_favorite
        FROM packages p
        JOIN isps i ON i.id = p.isp_id
        LEFT JOIN favorites f ON f.package_id = p.id
        LEFT JOIN package_regions r ON r.package_id = p.id
        WHERE p.is_active = 1
          AND (:region = 'JAVA_ALL' OR r.region_code = :region OR r.region_code = 'JAVA_ALL')
          AND (:maxMonthly IS NULL OR p.monthly_total <= :maxMonthly)
          AND (:minSpeed IS NULL OR p.speed_mbps >= :minSpeed)
          AND (:query IS NULL OR p.name LIKE '%' || :query || '%' OR i.name LIKE '%' || :query || '%')
          AND (:ispCount = 0 OR p.isp_id IN (:ispIds))
        GROUP BY p.id
        ORDER BY
          CASE WHEN :sort = 'cheapest' THEN p.monthly_total END ASC,
          CASE WHEN :sort = 'fastest' THEN p.speed_mbps END DESC,
          CASE WHEN :sort = 'value' THEN
            CASE WHEN p.speed_mbps > 0 THEN CAST(p.monthly_total AS REAL) / p.speed_mbps END END ASC,
          p.monthly_total ASC
        """,
    )
    fun observePackages(
        region: String,
        maxMonthly: Long?,
        minSpeed: Int?,
        query: String?,
        ispIds: List<String>,
        ispCount: Int,
        sort: String,
    ): Flow<List<PackageWithIsp>>

    @Query("SELECT * FROM isps ORDER BY name")
    fun observeIsps(): Flow<List<IspEntity>>

    @Query("SELECT COUNT(*) FROM packages WHERE is_active = 1")
    suspend fun activeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIsps(isps: List<IspEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackages(packages: List<PackageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegions(regions: List<PackageRegionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(history: List<PriceHistoryEntity>)

    @Query("DELETE FROM packages WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissingPackages(keepIds: Set<String>)

    @Query("DELETE FROM isps WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissingIsps(keepIds: Set<String>)

    @Query("SELECT * FROM price_history WHERE package_id = :id ORDER BY recorded_at")
    fun observeHistory(id: String): Flow<List<PriceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(fav: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE package_id = :id")
    suspend fun removeFavorite(id: String)

    @Transaction
    suspend fun replaceAll(
        isps: List<IspEntity>,
        packages: List<PackageEntity>,
        regions: List<PackageRegionEntity>,
        history: List<PriceHistoryEntity>,
    ) {
        upsertIsps(isps)
        upsertPackages(packages)
        upsertRegions(regions)
        insertHistory(history)
        deleteMissingPackages(packages.map { it.id }.toSet())
        deleteMissingIsps(isps.map { it.id }.toSet())
    }
}
