package com.example.argon.domain.model

enum class TruckType(
    val displayName: String,
    val nomCode: String,
    val defaultTurningRadiusM: Double,
    val isArticulated: Boolean
) {
    TRACTOR_SEMITRAILER("Tractocamión + Semirremolque", "T3-S2", 13.5, true),
    FULL_DOUBLE_TRAILER("Tractocamión Doblemente Articulado (Full)", "T3-S2-R4", 16.5, true),
    TORTON_RIGID("Camión Rígido Torton", "C3", 10.5, false),
    RABON("Camión Rabón", "C2", 8.8, false),
    LOWBOY("Tractocamión Lowboy / Maquinaria", "T3-S3", 14.5, true),
    TANKER("Autotanque / Pipa", "T3-S2-P", 13.8, true),
    FLATBED("Plataforma Abierta", "T3-S2-PL", 13.5, true)
}

enum class CargoType(
    val displayName: String,
    val isHazmat: Boolean,
    val requiresSpecialPermit: Boolean
) {
    GENERAL_DRY("Carga General / Caja Seca", false, false),
    REFRIGERATED("Caja Refrigerada / Perecederos", false, false),
    BULK_GRAIN("Granel / Tolva Agrícola", false, false),
    HEAVY_MACHINERY("Maquinaria Pesada / Sobredimensión", false, true),
    HAZMAT_FLAMMABLE("Material Peligroso (Inflamable / Combustible)", true, true),
    HAZMAT_CORROSIVE("Material Peligroso (Corrosivo / Químicos)", true, true),
    OVERSIZE("Carga Especial / Exceso de Dimensiones", false, true)
}

enum class SpecialRestriction(val displayName: String, val code: String) {
    AVOID_TOLLS("Evitar casetas de cuota (Preferir Federal Libre)", "NO_TOLLS"),
    HAZMAT_NO_TUNNELS("Prohibido túneles por carga peligrosa", "NO_TUNNELS_HAZMAT"),
    ONLY_ET_A_CORRIDORS("Solo ejes de transporte ET y Tipo A (NOM-012)", "ONLY_ET_A"),
    URBAN_SCHEDULE_BAN("Respetar restricción horaria urbana (ZMVM/MTY/GDL)", "URBAN_WINDOW"),
    REQUIRES_PILOT_CAR("Requiere unidad piloto / abanderamiento", "PILOT_CAR")
}

data class TruckProfile(
    val id: Long = 1L,
    val name: String,
    val heightMeters: Double,
    val widthMeters: Double,
    val lengthMeters: Double,
    val grossWeightTons: Double,
    val axleCount: Int,
    val axleLoadTons: Double?, // Null when weight per axle is unavailable
    val truckType: TruckType,
    val cargoType: CargoType,
    val specialRestrictions: Set<SpecialRestriction>,
    val isActive: Boolean = true
)

enum class RoadClass(val displayName: String, val truckPriority: Int, val isHeavyApprovedByClass: Boolean) {
    AUTOPISTA_CUOTA("Autopista de Cuota (ET/A4)", 1, true),
    LIBRAMIENTO("Libramiento de Carga Pesada", 1, true),
    CORREDOR_CARGA("Corredor Logístico / Carga Designado", 1, true),
    CARRETERA_FEDERAL("Carretera Federal (SCT)", 2, true),
    CARRETERA_ESTATAL("Carretera Estatal", 3, true),
    URBANA_RESTRINGIDA("Vialidad Urbana Restringida", 6, false),
    CALLE_RESIDENCIAL("Calle Residencial / Local", 99, false)
}

enum class VerificationStatus(val label: String) {
    VERIFIED_SOURCE("Verificado (OSM/SCT)"),
    PARTIAL_MISSING_DATA("Datos Parciales (Falta gálibo/peso)"),
    UNVERIFIED("Sin verificación oficial")
}

enum class RestrictionKind(val label: String) {
    LEGAL_PROHIBITION("Restricción Legal"),
    DRIVING_ADVISORY("Recomendación de Conducción")
}

data class RoadRestriction(
    val id: String,
    val title: String,
    val description: String,
    val kind: RestrictionKind,
    val roadName: String,
    val stateCode: String,
    val lat: Double,
    val lng: Double,
    val maxHeightMeters: Double? = null,
    val maxWeightTons: Double? = null,
    val maxAxleLoadTons: Double? = null,
    val maxWidthMeters: Double? = null,
    val maxLengthMeters: Double? = null,
    val hgvProhibited: Boolean = false,
    val hazmatProhibited: Boolean = false,
    val fullDoubleProhibited: Boolean = false,
    val isBridge: Boolean = false,
    val isTunnel: Boolean = false,
    val gradePercent: Double? = null,
    val verificationStatus: VerificationStatus,
    val sourceCitation: String
) {
    val isLowBridgeOrWeightLimit: Boolean
        get() = isBridge || maxHeightMeters != null || maxWeightTons != null || maxAxleLoadTons != null

    val isSteepGrade: Boolean
        get() = gradePercent != null && gradePercent > 0.0

    val isProhibitedZone: Boolean
        get() = hgvProhibited || hazmatProhibited || fullDoubleProhibited
}

enum class RestrictionPointFilter(val label: String) {
    ALL("Todos"),
    LOW_BRIDGES("Puentes / Peso"),
    STEEP_GRADES("Pendientes"),
    PROHIBITED_ZONES("Zonas Prohibidas")
}

data class RestrictionCrossing(
    val id: Long = 0L,
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
    val crossedAtEpochMs: Long = System.currentTimeMillis()
)

enum class TruckPoiCategory(val displayName: String, val shortCode: String) {
    GASOLINERA_PESADA("Gasolinera Vehículos Pesados", "GAS"),
    ESTACION_DIESEL("Estación de Diésel Alto Flujo", "DIÉSEL"),
    PARADOR_CAMIONES("Parador de Camiones", "PARADOR"),
    AREA_DESCANSO("Área de Descanso", "DESCANSO"),
    ESTACIONAMIENTO_TRAILER("Estacionamiento para Tráileres", "PENSIÓN"),
    TALLER_PESADO("Taller de Servicio Pesado", "TALLER"),
    BASCULA("Báscula / Control de Peso SCT", "BÁSCULA"),
    CASETA_COBRO("Caseta de Cobro", "CASETA"),
    CENTRO_CARGA_DESCARGA("Centro de Carga y Descarga", "CEDIS")
}

data class TruckPoi(
    val id: String,
    val name: String,
    val category: TruckPoiCategory,
    val stateCode: String,
    val highwayRef: String,
    val lat: Double,
    val lng: Double,
    val details: String,
    val verifiedSource: String,
    val tollEstimateMxn: Double? = null,
    val hasTrailerParking: Boolean = false
)

data class MexicoStateRegion(
    val code: String,
    val name: String,
    val capitalOrHub: String,
    val centerLat: Double,
    val centerLng: Double,
    val mainCorridors: List<String>,
    val estimatedSizeMb: Double,
    val isDownloaded: Boolean = false,
    val downloadedAtEpochMs: Long? = null,
    val segmentCount: Int = 0,
    val restrictionCount: Int = 0,
    val poiCount: Int = 0
)
