package com.example.argon.domain.model

data class GeoPoint(
    val lat: Double,
    val lng: Double
)

data class RoadNode(
    val id: String,
    val name: String,
    val stateCode: String,
    val lat: Double,
    val lng: Double,
    val isFreightHub: Boolean = false,
    val allowsHeavyUturn: Boolean = false
)

data class RoadEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val roadName: String,
    val highwayShield: String,
    val stateCode: String,
    val roadClass: RoadClass,
    val distanceKm: Double,
    val speedLimitKmh: Int,
    val isBridge: Boolean = false,
    val isTunnel: Boolean = false,
    val hasToll: Boolean = false,
    val tollCostMxn: Double = 0.0,
    val gradePercent: Double = 0.0,
    val maxHeightMeters: Double? = null,
    val maxWeightTons: Double? = null,
    val maxAxleLoadTons: Double? = null,
    val maxWidthMeters: Double? = null,
    val maxLengthMeters: Double? = null,
    val hgvAllowed: Boolean = true,
    val hazmatAllowed: Boolean = true,
    val fullDoubleAllowed: Boolean = true,
    val minTurningRadiusM: Double = 18.0,
    val verificationStatus: VerificationStatus = VerificationStatus.VERIFIED_SOURCE,
    val sourceCitation: String = "OSM Overpass / SCT Red Nacional",
    val geometry: List<GeoPoint> = emptyList()
)

enum class ManeuverType {
    DEPART,
    CONTINUE,
    TURN_RIGHT,
    TURN_LEFT,
    SLIGHT_RIGHT,
    SLIGHT_LEFT,
    EXIT_HIGHWAY,
    SHARP_CURVE_AHEAD,
    BRIDGE_APPROACH,
    TOLL_BOOTH,
    WEIGH_STATION,
    ARRIVE
}

data class RouteManeuver(
    val id: String,
    val type: ManeuverType,
    val instruction: String,
    val roadName: String,
    val highwayShield: String,
    val townName: String,
    val distanceMeters: Double,
    val lat: Double,
    val lng: Double,
    val curveAdvisory: String? = null,
    val exitCode: String? = null
)

enum class DataCompleteness {
    FULLY_VERIFIED,
    PARTIAL_MISSING_DATA,
    INCOMPLETE_UNVERIFIED
}

data class RouteSafetyAudit(
    val isCompatible: Boolean,
    val safetyGuaranteed: Boolean,
    val dataCompleteness: DataCompleteness,
    val rejectionReasons: List<String>,
    val legalRestrictionsFound: List<RoadRestriction>,
    val drivingAdvisories: List<RoadRestriction>,
    val missingDataWarnings: List<String>,
    val avoidsResidential: Boolean,
    val avoidsIncompatibleUturns: Boolean
)

enum class RoutingSource(val label: String) {
    GOOGLE_ROUTES_AUDITED("Google Routes API + Auditoría Carga Pesada"),
    VALHALLA_TRUCK_VERIFIED("Valhalla Truck API + Auditoría Overpass"),
    LOCAL_FREIGHT_GRAPH("Motor Offline de Corredores Pesados ARGÓN"),
    HYBRID_VERIFIED("Híbrido (Valhalla Truck + Red Offline SCT)")
}

data class TruckRouteOption(
    val id: String,
    val label: String,
    val summary: String,
    val isPrimary: Boolean,
    val routingSource: RoutingSource,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val tollCostMxn: Double,
    val waypoints: List<GeoPoint>,
    val maneuvers: List<RouteManeuver>,
    val passedStates: List<String>,
    val roadClassesUsed: Set<RoadClass>,
    val safetyAudit: RouteSafetyAudit,
    val corridorPois: List<TruckPoi>
)

enum class NavigationStatus(val label: String) {
    IDLE("En espera"),
    CALCULATING("Calculando ruta de carga pesada"),
    READY("Ruta verificada lista"),
    NAVIGATING("Navegando en ruta"),
    REROUTING("Recalculando por desvío"),
    OFF_ROUTE("Fuera de ruta"),
    BLOCKED_INCOMPATIBLE("Ruta bloqueada por dimensiones/peso"),
    ARRIVED("Destino alcanzado")
}

data class TelemetryLocation(
    val lat: Double,
    val lng: Double,
    val speedKmh: Double,
    val bearingDeg: Float,
    val accuracyMeters: Float,
    val currentRoadName: String,
    val currentTown: String,
    val currentStateCode: String,
    val isSimulated: Boolean,
    val timestampMs: Long
)

data class GeocodePlace(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
    val stateCode: String,
    val source: String
)
