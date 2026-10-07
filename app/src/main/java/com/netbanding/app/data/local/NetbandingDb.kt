package com.netbanding.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        IspEntity::class,
        PackageEntity::class,
        PackageRegionEntity::class,
        PriceHistoryEntity::class,
        FavoriteEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NetbandingDb : RoomDatabase() {
    abstract fun packageDao(): PackageDao
}
