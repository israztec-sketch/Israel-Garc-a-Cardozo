package com.example.argon.data.local

import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.RestrictionCrossing
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckPoiCategory
import com.example.argon.domain.routing.GeoMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.cos
import kotlin.math.max

/**
 * Repositorio Room para puntos geográficos de restricciones (puentes bajos, pendientes, zonas prohibidas)
 * y registro de cruces con la ubicación en tiempo real del usuario.
 */
class RestrictionRepository(
    private val restrictionDao: RoadRestrictionDao,
    private val crossingDao: RestrictionCrossingDao,
    private val poiDao: TruckPoiDao
) {
    val allRestrictions: Flow<List<RoadRestriction>> = restrictionDao.observeAllRestrictions()
        .map { entities ->
            if (entities.isEmpty()) MexicoFreightCatalog.verifiedRestrictions
            else entities.map { it.toDomain() }
        }

    val recentCrossings: Flow<List<RestrictionCrossing>> = crossingDao.observeRecentCrossings(limit = 50)
        .map { entities -> entities.map { it.toDomain() } }

    val allPois: Flow<List<TruckPoi>> = poiDao.observeAllPois()
        .map { entities ->
            if (entities.isEmpty()) MexicoFreightCatalog.verifiedPois
            else entities.map { it.toDomain() }
        }

    val weighStations: Flow<List<TruckPoi>> = allPois.map { list ->
        list.filter { it.category == TruckPoiCategory.BASCULA }
    }

    /**
     * Consulta espacial reactiva en Room usando caja delimitadora (BETWEEN lat/lng)
     * y refinamiento exacto por distancia geodésica Haversine.
     */
    fun observeRestrictionsCrossingLocation(
        lat: Double,
        lng: Double,
        radiusKm: Double = 5.0
    ): Flow<List<RoadRestriction>> {
        val bounds = computeBoundingBox(lat, lng, radiusKm)
        val userPoint = GeoPoint(lat, lng)
        return restrictionDao.observeRestrictionsInBounds(
            minLat = bounds.minLat,
            maxLat = bounds.maxLat,
            minLng = bounds.minLng,
            maxLng = bounds.maxLng
        ).map { entities ->
            entities.map { it.toDomain() }
                .filter { GeoMath.haversineKm(userPoint, GeoPoint(it.lat, it.lng)) <= radiusKm }
                .sortedBy { GeoMath.haversineKm(userPoint, GeoPoint(it.lat, it.lng)) }
        }
    }

    suspend fun findRestrictionsCrossingLocation(
        lat: Double,
        lng: Double,
        radiusKm: Double = 3.5
    ): List<RoadRestriction> {
        val bounds = computeBoundingBox(lat, lng, radiusKm)
        val userPoint = GeoPoint(lat, lng)
        return restrictionDao.findRestrictionsInBounds(
            minLat = bounds.minLat,
            maxLat = bounds.maxLat,
            minLng = bounds.minLng,
            maxLng = bounds.maxLng
        )
            .map { it.toDomain() }
            .filter { GeoMath.haversineKm(userPoint, GeoPoint(it.lat, it.lng)) <= radiusKm }
            .sortedBy { GeoMath.haversineKm(userPoint, GeoPoint(it.lat, it.lng)) }
    }

    suspend fun saveRestrictionPoint(restriction: RoadRestriction) {
        restrictionDao.upsertRestriction(RoadRestrictionEntity.fromDomain(restriction))
    }

    suspend fun deleteRestrictionPoint(id: String) {
        restrictionDao.deleteRestrictionById(id)
    }

    suspend fun recordCrossingIfNew(
        crossing: RestrictionCrossing,
        cooldownMs: Long = 120_000L
    ) {
        val latest = crossingDao.getLatestCrossingForRestriction(crossing.restrictionId)
        val elapsed = if (latest != null) crossing.crossedAtEpochMs - latest.crossedAtEpochMs else Long.MAX_VALUE
        if (latest == null || elapsed >= cooldownMs) {
            crossingDao.insertCrossing(RestrictionCrossingEntity.fromDomain(crossing))
        }
    }

    suspend fun clearCrossingHistory() {
        crossingDao.clearAll()
    }

    private data class GeoBounds(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double
    )

    private fun computeBoundingBox(lat: Double, lng: Double, radiusKm: Double): GeoBounds {
        val latDelta = radiusKm / 111.0
        val cosLat = max(0.2, cos(Math.toRadians(lat)))
        val lngDelta = radiusKm / (111.32 * cosLat)
        return GeoBounds(
            minLat = lat - latDelta,
            maxLat = lat + latDelta,
            minLng = lng - lngDelta,
            maxLng = lng + lngDelta
        )
    }
}
