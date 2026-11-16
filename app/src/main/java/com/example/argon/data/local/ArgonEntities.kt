package com.example.argon.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.argon.domain.model.CargoType
import com.example.argon.domain.model.MexicoStateRegion
import com.example.argon.domain.model.RestrictionCrossing
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.SpecialRestriction
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckPoiCategory
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckType
import com.example.argon.domain.model.VerificationStatus

@Entity(tableName = "truck_profiles")
data class TruckProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val heightMeters: Double,
    val widthMeters: Double,
    val lengthMeters: Double,
    val grossWeightTons: Double,
    val axleCount: Int,
    val axleLoadTons: Double?,
    val truckTypeName: String,
    val cargoTypeName: String,
    val specialRestrictionsCsv: String,
    val isActive: Boolean
) {
    fun toDomain(): TruckProfile {
        val restrictions = specialRestrictionsCsv
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { name -> SpecialRestriction.entries.firstOrNull { it.name == name } }
            .toSet()
        return TruckProfile(
            id = id,
            name = name,
            heightMeters = heightMeters,
            widthMeters = widthMeters,
            lengthMeters = lengthMeters,
            grossWeightTons = grossWeightTons,
            axleCount = axleCount,
            axleLoadTons = axleLoadTons,
            truckType = TruckType.entries.firstOrNull { it.name == truckTypeName } ?: TruckType.TRACTOR_SEMITRAILER,
            cargoType = CargoType.entries.firstOrNull { it.name == cargoTypeName } ?: CargoType.GENERAL_DRY,
            specialRestrictions = restrictions,
            isActive = isActive
        )
    }

    companion object {
        fun fromDomain(profile: TruckProfile): TruckProfileEntity = TruckProfileEntity(
            id = profile.id,
            name = profile.name,
            heightMeters = profile.heightMeters,
            widthMeters = profile.widthMeters,
            lengthMeters = profile.lengthMeters,
            grossWeightTons = profile.grossWeightTons,
            axleCount = profile.axleCount,
            axleLoadTons = profile.axleLoadTons,
            truckTypeName = profile.truckType.name,
            cargoTypeName = profile.cargoType.name,
            specialRestrictionsCsv = profile.specialRestrictions.joinToString(",") { it.name },
            isActive = profile.isActive
        )
    }
}

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val code: String,
    val name: String,
    val capitalOrHub: String,
    val centerLat: Double,
    val centerLng: Double,
    val mainCorridorsCsv: String,
    val estimatedSizeMb: Double,
    val isDownloaded: Boolean,
    val downloadedAtEpochMs: Long?,
    val segmentCount: Int,
    val restrictionCount: Int,
    val poiCount: Int
) {
    fun toDomain(): MexicoStateRegion = MexicoStateRegion(
        code = code,
        name = name,
        capitalOrHub = capitalOrHub,
        centerLat = centerLat,
        centerLng = centerLng,
        mainCorridors = mainCorridorsCsv.split("|").filter { it.isNotBlank() },
        estimatedSizeMb = estimatedSizeMb,
        isDownloaded = isDownloaded,
        downloadedAtEpochMs = downloadedAtEpochMs,
        segmentCount = segmentCount,
        restrictionCount = restrictionCount,
        poiCount = poiCount
    )

    companion object {
        fun fromDomain(region: MexicoStateRegion): OfflineRegionEntity = OfflineRegionEntity(
            code = region.code,
            name = region.name,
            capitalOrHub = region.capitalOrHub,
            centerLat = region.centerLat,
            centerLng = region.centerLng,
            mainCorridorsCsv = region.mainCorridors.joinToString("|"),
            estimatedSizeMb = region.estimatedSizeMb,
            isDownloaded = region.isDownloaded,
            downloadedAtEpochMs = region.downloadedAtEpochMs,
            segmentCount = region.segmentCount,
            restrictionCount = region.restrictionCount,
            poiCount = region.poiCount
        )
    }
}

@Entity(tableName = "truck_pois")
data class TruckPoiEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryName: String,
    val stateCode: String,
    val highwayRef: String,
    val lat: Double,
    val lng: Double,
    val details: String,
    val verifiedSource: String,
    val tollEstimateMxn: Double?,
    val hasTrailerParking: Boolean
) {
    fun toDomain(): TruckPoi = TruckPoi(
        id = id,
        name = name,
        category = TruckPoiCategory.entries.firstOrNull { it.name == categoryName } ?: TruckPoiCategory.PARADOR_CAMIONES,
        stateCode = stateCode,
        highwayRef = highwayRef,
        lat = lat,
        lng = lng,
        details = details,
        verifiedSource = verifiedSource,
        tollEstimateMxn = tollEstimateMxn,
        hasTrailerParking = hasTrailerParking
    )

    companion object {
        fun fromDomain(poi: TruckPoi): TruckPoiEntity = TruckPoiEntity(
            id = poi.id,
            name = poi.name,
            categoryName = poi.category.name,
            stateCode = poi.stateCode,
            highwayRef = poi.highwayRef,
            lat = poi.lat,
            lng = poi.lng,
            details = poi.details,
            verifiedSource = poi.verifiedSource,
            tollEstimateMxn = poi.tollEstimateMxn,
            hasTrailerParking = poi.hasTrailerParking
        )
    }
}

@Entity(
    tableName = "road_restrictions",
    indices = [
        Index(value = ["lat", "lng"]),
        Index(value = ["stateCode"])
    ]
)
data class RoadRestrictionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val kindName: String,
    val roadName: String,
    val stateCode: String,
    val lat: Double,
    val lng: Double,
    val maxHeightMeters: Double?,
    val maxWeightTons: Double?,
    val maxAxleLoadTons: Double?,
    val maxWidthMeters: Double?,
    val maxLengthMeters: Double?,
    val hgvProhibited: Boolean,
    val hazmatProhibited: Boolean,
    val fullDoubleProhibited: Boolean,
    val isBridge: Boolean,
    val isTunnel: Boolean,
    val gradePercent: Double?,
    val verificationStatusName: String,
    val sourceCitation: String
) {
    fun toDomain(): RoadRestriction = RoadRestriction(
        id = id,
        title = title,
        description = description,
        kind = RestrictionKind.entries.firstOrNull { it.name == kindName } ?: RestrictionKind.LEGAL_PROHIBITION,
        roadName = roadName,
        stateCode = stateCode,
        lat = lat,
        lng = lng,
        maxHeightMeters = maxHeightMeters,
        maxWeightTons = maxWeightTons,
        maxAxleLoadTons = maxAxleLoadTons,
        maxWidthMeters = maxWidthMeters,
        maxLengthMeters = maxLengthMeters,
        hgvProhibited = hgvProhibited,
        hazmatProhibited = hazmatProhibited,
        fullDoubleProhibited = fullDoubleProhibited,
        isBridge = isBridge,
        isTunnel = isTunnel,
        gradePercent = gradePercent,
        verificationStatus = VerificationStatus.entries.firstOrNull { it.name == verificationStatusName } ?: VerificationStatus.VERIFIED_SOURCE,
        sourceCitation = sourceCitation
    )

    companion object {
        fun fromDomain(r: RoadRestriction): RoadRestrictionEntity = RoadRestrictionEntity(
            id = r.id,
            title = r.title,
            description = r.description,
            kindName = r.kind.name,
            roadName = r.roadName,
            stateCode = r.stateCode,
            lat = r.lat,
            lng = r.lng,
            maxHeightMeters = r.maxHeightMeters,
            maxWeightTons = r.maxWeightTons,
            maxAxleLoadTons = r.maxAxleLoadTons,
            maxWidthMeters = r.maxWidthMeters,
            maxLengthMeters = r.maxLengthMeters,
            hgvProhibited = r.hgvProhibited,
            hazmatProhibited = r.hazmatProhibited,
            fullDoubleProhibited = r.fullDoubleProhibited,
            isBridge = r.isBridge,
            isTunnel = r.isTunnel,
            gradePercent = r.gradePercent,
            verificationStatusName = r.verificationStatus.name,
            sourceCitation = r.sourceCitation
        )
    }
}

@Entity(
    tableName = "restriction_crossings",
    indices = [
        Index(value = ["crossedAtEpochMs"]),
        Index(value = ["restrictionId"])
    ]
)
data class RestrictionCrossingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val restrictionId: String,
    val restrictionTitle: String,
    val restrictionCategory: String,
    val roadName: String,
    val stateCode: String,
    val userLat: Double,
    val userLng: Double,
    val restrictionLat: Double,
    val restrictionLng: Double,
    val distanceMeters: Int,
    val severityName: String,
    val crossedAtEpochMs: Long
) {
    fun toDomain(): RestrictionCrossing = RestrictionCrossing(
        id = id,
        restrictionId = restrictionId,
        restrictionTitle = restrictionTitle,
        restrictionCategory = restrictionCategory,
        roadName = roadName,
        stateCode = stateCode,
        userLat = userLat,
        userLng = userLng,
        restrictionLat = restrictionLat,
        restrictionLng = restrictionLng,
        distanceMeters = distanceMeters,
        severityName = severityName,
        crossedAtEpochMs = crossedAtEpochMs
    )

    companion object {
        fun fromDomain(c: RestrictionCrossing): RestrictionCrossingEntity = RestrictionCrossingEntity(
            id = c.id,
            restrictionId = c.restrictionId,
            restrictionTitle = c.restrictionTitle,
            restrictionCategory = c.restrictionCategory,
            roadName = c.roadName,
            stateCode = c.stateCode,
            userLat = c.userLat,
            userLng = c.userLng,
            restrictionLat = c.restrictionLat,
            restrictionLng = c.restrictionLng,
            distanceMeters = c.distanceMeters,
            severityName = c.severityName,
            crossedAtEpochMs = c.crossedAtEpochMs
        )
    }
}
