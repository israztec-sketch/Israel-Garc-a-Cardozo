package com.example.argon.api

import com.example.argon.data.remote.CartographicApiClient
import com.example.argon.domain.model.NavigationStatus
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckRouteOption
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JsonClass(generateAdapter = true)
data class ArgonRestrictionSummary(
    val id: String,
    val title: String,
    val kind: String,
    val roadName: String,
    val maxHeightMeters: Double?,
    val maxWeightTons: Double?,
    val sourceCitation: String
)

@JsonClass(generateAdapter = true)
data class ArgonTelemetrySnapshot(
    val protocolVersion: String = "ARGON-TRUCK-1.0",
    val updatedAtMs: Long,
    val navigationStatus: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val bearingDeg: Float,
    val currentRoadName: String,
    val currentStateCode: String,
    val isSimulatedGps: Boolean,
    val vehicleName: String,
    val vehicleTypeNom: String,
    val heightMeters: Double,
    val widthMeters: Double,
    val lengthMeters: Double,
    val grossWeightTons: Double,
    val axleCount: Int,
    val axleLoadTons: Double?,
    val cargoType: String,
    val selectedRouteId: String?,
    val selectedRouteLabel: String?,
    val totalDistanceKm: Double,
    val remainingDistanceKm: Double,
    val estimatedMinutesTotal: Int,
    val etaRemainingMinutes: Int,
    val routeSafetyGuaranteed: Boolean,
    val dataCompleteness: String,
    val restrictionsFound: List<ArgonRestrictionSummary>,
    val missingDataWarnings: List<String>
)

object ArgonTelemetryBus {
    private val _snapshot = MutableStateFlow(
        ArgonTelemetrySnapshot(
            updatedAtMs = System.currentTimeMillis(),
            navigationStatus = NavigationStatus.IDLE.name,
            lat = 19.4326,
            lng = -99.1332,
            speedKmh = 0.0,
            bearingDeg = 0f,
            currentRoadName = "En espera de ruta",
            currentStateCode = "CMX",
            isSimulatedGps = true,
            vehicleName = "Sin perfil activo",
            vehicleTypeNom = "T3-S2",
            heightMeters = 4.20,
            widthMeters = 2.60,
            lengthMeters = 21.50,
            grossWeightTons = 46.5,
            axleCount = 5,
            axleLoadTons = 10.0,
            cargoType = "GENERAL_DRY",
            selectedRouteId = null,
            selectedRouteLabel = null,
            totalDistanceKm = 0.0,
            remainingDistanceKm = 0.0,
            estimatedMinutesTotal = 0,
            etaRemainingMinutes = 0,
            routeSafetyGuaranteed = false,
            dataCompleteness = "INCOMPLETE",
            restrictionsFound = emptyList(),
            missingDataWarnings = emptyList()
        )
    )
    val snapshot: StateFlow<ArgonTelemetrySnapshot> = _snapshot.asStateFlow()

    private val adapter = CartographicApiClient.moshi.adapter(ArgonTelemetrySnapshot::class.java)

    fun publish(
        location: TelemetryLocation,
        profile: TruckProfile,
        route: TruckRouteOption?,
        status: NavigationStatus,
        remainingKm: Double,
        etaMinutes: Int
    ) {
        val allRestrictions: List<RoadRestriction> = if (route != null) {
            route.safetyAudit.legalRestrictionsFound + route.safetyAudit.drivingAdvisories
        } else emptyList()

        _snapshot.value = ArgonTelemetrySnapshot(
            updatedAtMs = System.currentTimeMillis(),
            navigationStatus = status.name,
            lat = location.lat,
            lng = location.lng,
            speedKmh = location.speedKmh,
            bearingDeg = location.bearingDeg,
            currentRoadName = location.currentRoadName,
            currentStateCode = location.currentStateCode,
            isSimulatedGps = location.isSimulated,
            vehicleName = profile.name,
            vehicleTypeNom = profile.truckType.nomCode,
            heightMeters = profile.heightMeters,
            widthMeters = profile.widthMeters,
            lengthMeters = profile.lengthMeters,
            grossWeightTons = profile.grossWeightTons,
            axleCount = profile.axleCount,
            axleLoadTons = profile.axleLoadTons,
            cargoType = profile.cargoType.name,
            selectedRouteId = route?.id,
            selectedRouteLabel = route?.label,
            totalDistanceKm = route?.distanceKm ?: 0.0,
            remainingDistanceKm = remainingKm,
            estimatedMinutesTotal = route?.estimatedMinutes ?: 0,
            etaRemainingMinutes = etaMinutes,
            routeSafetyGuaranteed = route?.safetyAudit?.safetyGuaranteed ?: false,
            dataCompleteness = route?.safetyAudit?.dataCompleteness?.name ?: "INCOMPLETE",
            restrictionsFound = allRestrictions.map {
                ArgonRestrictionSummary(
                    id = it.id,
                    title = it.title,
                    kind = it.kind.name,
                    roadName = it.roadName,
                    maxHeightMeters = it.maxHeightMeters,
                    maxWeightTons = it.maxWeightTons,
                    sourceCitation = it.sourceCitation
                )
            },
            missingDataWarnings = route?.safetyAudit?.missingDataWarnings ?: emptyList()
        )
    }

    fun snapshotJson(): String = adapter.indent("  ").toJson(_snapshot.value)
}
