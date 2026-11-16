package com.example.argon.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TruckProfileDao {
    @Query("SELECT * FROM truck_profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<TruckProfileEntity>>

    @Query("SELECT * FROM truck_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<TruckProfileEntity?>

    @Query("SELECT COUNT(*) FROM truck_profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: TruckProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<TruckProfileEntity>)

    @Query("UPDATE truck_profiles SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(activeId: Long)

    @Transaction
    suspend fun upsertAndActivate(profile: TruckProfileEntity): Long {
        val insertedId = upsert(profile.copy(isActive = true))
        val targetId = if (profile.id > 0) profile.id else insertedId
        setActiveProfile(targetId)
        return targetId
    }
}

@Dao
interface OfflineRegionDao {
    @Query("SELECT * FROM offline_regions ORDER BY name ASC")
    fun observeAllRegions(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT COUNT(*) FROM offline_regions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(regions: List<OfflineRegionEntity>)

    @Query(
        "UPDATE offline_regions SET isDownloaded = :downloaded, downloadedAtEpochMs = :timestamp, " +
            "segmentCount = :segments, restrictionCount = :restrictions, poiCount = :pois WHERE code = :code"
    )
    suspend fun updateDownloadState(
        code: String,
        downloaded: Boolean,
        timestamp: Long?,
        segments: Int,
        restrictions: Int,
        pois: Int
    )
}

@Dao
interface TruckPoiDao {
    @Query("SELECT * FROM truck_pois ORDER BY name ASC")
    fun observeAllPois(): Flow<List<TruckPoiEntity>>

    @Query("SELECT COUNT(*) FROM truck_pois")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<TruckPoiEntity>)
}

@Dao
interface RoadRestrictionDao {
    @Query("SELECT * FROM road_restrictions ORDER BY stateCode ASC, title ASC")
    fun observeAllRestrictions(): Flow<List<RoadRestrictionEntity>>

    @Query(
        "SELECT * FROM road_restrictions " +
            "WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng " +
            "ORDER BY stateCode ASC, title ASC"
    )
    fun observeRestrictionsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<RoadRestrictionEntity>>

    @Query(
        "SELECT * FROM road_restrictions " +
            "WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng"
    )
    suspend fun findRestrictionsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<RoadRestrictionEntity>

    @Query("SELECT COUNT(*) FROM road_restrictions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRestriction(restriction: RoadRestrictionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(restrictions: List<RoadRestrictionEntity>)

    @Query("DELETE FROM road_restrictions WHERE id = :id")
    suspend fun deleteRestrictionById(id: String)
}

@Dao
interface RestrictionCrossingDao {
    @Query("SELECT * FROM restriction_crossings ORDER BY crossedAtEpochMs DESC LIMIT :limit")
    fun observeRecentCrossings(limit: Int = 50): Flow<List<RestrictionCrossingEntity>>

    @Query("SELECT * FROM restriction_crossings WHERE restrictionId = :restrictionId ORDER BY crossedAtEpochMs DESC LIMIT 1")
    suspend fun getLatestCrossingForRestriction(restrictionId: String): RestrictionCrossingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossing(crossing: RestrictionCrossingEntity): Long

    @Query("DELETE FROM restriction_crossings")
    suspend fun clearAll()
}
