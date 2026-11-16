package com.example.argon.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.argon.data.catalog.MexicoFreightCatalog

@Database(
    entities = [
        TruckProfileEntity::class,
        OfflineRegionEntity::class,
        TruckPoiEntity::class,
        RoadRestrictionEntity::class,
        RestrictionCrossingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ArgonDatabase : RoomDatabase() {
    abstract fun truckProfileDao(): TruckProfileDao
    abstract fun offlineRegionDao(): OfflineRegionDao
    abstract fun truckPoiDao(): TruckPoiDao
    abstract fun roadRestrictionDao(): RoadRestrictionDao
    abstract fun restrictionCrossingDao(): RestrictionCrossingDao

    suspend fun ensureSeeded() {
        if (truckProfileDao().count() == 0) {
            truckProfileDao().insertAll(MexicoFreightCatalog.defaultProfiles.map { TruckProfileEntity.fromDomain(it) })
        }
        if (offlineRegionDao().count() == 0) {
            offlineRegionDao().insertAll(MexicoFreightCatalog.all32States.map { OfflineRegionEntity.fromDomain(it) })
        }
        if (truckPoiDao().count() == 0) {
            truckPoiDao().insertAll(MexicoFreightCatalog.verifiedPois.map { TruckPoiEntity.fromDomain(it) })
        }
        if (roadRestrictionDao().count() == 0) {
            roadRestrictionDao().insertAll(MexicoFreightCatalog.verifiedRestrictions.map { RoadRestrictionEntity.fromDomain(it) })
        }
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_road_restrictions_lat_lng` ON `road_restrictions` (`lat`, `lng`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_road_restrictions_stateCode` ON `road_restrictions` (`stateCode`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `restriction_crossings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `restrictionId` TEXT NOT NULL,
                        `restrictionTitle` TEXT NOT NULL,
                        `restrictionCategory` TEXT NOT NULL,
                        `roadName` TEXT NOT NULL,
                        `stateCode` TEXT NOT NULL,
                        `userLat` REAL NOT NULL,
                        `userLng` REAL NOT NULL,
                        `restrictionLat` REAL NOT NULL,
                        `restrictionLng` REAL NOT NULL,
                        `distanceMeters` INTEGER NOT NULL,
                        `severityName` TEXT NOT NULL,
                        `crossedAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_restriction_crossings_crossedAtEpochMs` ON `restriction_crossings` (`crossedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_restriction_crossings_restrictionId` ON `restriction_crossings` (`restrictionId`)")
            }
        }

        @Volatile
        private var INSTANCE: ArgonDatabase? = null

        fun getInstance(context: Context): ArgonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArgonDatabase::class.java,
                    "argon_truck_gps.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
