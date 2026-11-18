package com.example.argon.domain.routing

import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckPoiCategory
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckType
import com.example.argon.domain.model.VerificationStatus
import com.example.argon.ui.RestrictedPoiAlertSeverity
import com.example.argon.ui.RestrictedPoiWarning
import java.util.Locale
import kotlin.math.roundToInt

enum class Nom012ViolationType(
    val title: String,
    val nomClause: String
) {
    INSUFFICIENT_BRIDGE_HEIGHT(
        title = "Altura de Puente / Gálibo Insuficiente",
        nomClause = "NOM-012-SCT-2-2017 · Numeral 5.2 (Altura Máxima)"
    ),
    GROSS_WEIGHT_EXCEEDED(
        title = "Peso Bruto Vehicular Excedido",
        nomClause = "NOM-012-SCT-2-2017 · Numeral 5.1 (Peso Bruto Vehicular)"
    ),
    AXLE_LOAD_EXCEEDED(
        title = "Peso Máximo por Eje Excedido",
        nomClause = "NOM-012-SCT-2-2017 · Numeral 5.1.1 (Concentración de Carga por Eje)"
    ),
    WIDTH_EXCEEDED(
        title = "Ancho Máximo Vehicular Excedido",
        nomClause = "NOM-012-SCT-2-2017 · Numeral 5.2.1 (Ancho Máximo 2.60 m)"
    ),
    LENGTH_EXCEEDED(
        title = "Longitud Máxima Vehicular Excedida",
        nomClause = "NOM-012-SCT-2-2017 · Numeral 5.2.2 (Longitud por Configuración)"
    ),
    FULL_DOUBLE_PROHIBITED(
        title = "Tramo Prohibido para Doblemente Articulado (Full T3-S2-R4)",
        nomClause = "NOM-012-SCT-2-2017 · Numeral 6.1.2 (Restricción a Caminos ET y A)"
    ),
    HEAVY_OR_HAZMAT_PROHIBITED(
        title = "Prohibición de Circulación a Carga Pesada / Hazmat",
        nomClause = "NOM-012-SCT-2-2017 / Reglamento de Autotransporte Federal"
    )
}

data class Nom012ViolationDetail(
    val type: Nom012ViolationType,
    val vehicleMetric: String,
    val allowedLimitMetric: String,
    val excessMetric: String
)

/**
 * Capa de evaluación normativa NOM-012-SCT-2-2017 que compara la ubicación GPS actual del usuario
 * y el perfil del tractocamión contra los registros espaciales de Room (puentes bajos, límites
 * de peso, pendientes y zonas prohibidas).
 */
object Nom012ComplianceEvaluator {

    const val CRITICAL_RESTRICTION_ALERT_RADIUS_METERS = 3500
    const val WEIGH_STATION_ALERT_RADIUS_METERS = 5000

    /**
     * Evalúa una restricción individual de Room frente al perfil activo del tractocamión
     * para detectar violaciones a la NOM-012-SCT-2-2017.
     */
    fun detectNom012Violations(
        restriction: RoadRestriction,
        profile: TruckProfile
    ): List<Nom012ViolationDetail> {
        val violations = mutableListOf<Nom012ViolationDetail>()

        // 1. Altura de puente / gálibo insuficiente (Numeral 5.2 NOM-012)
        if (restriction.maxHeightMeters != null && profile.heightMeters > restriction.maxHeightMeters) {
            val excess = profile.heightMeters - restriction.maxHeightMeters
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.INSUFFICIENT_BRIDGE_HEIGHT,
                    vehicleMetric = String.format(Locale.US, "%.2f m", profile.heightMeters),
                    allowedLimitMetric = String.format(Locale.US, "%.2f m", restriction.maxHeightMeters),
                    excessMetric = String.format(Locale.US, "+%.2f m sobre gálibo", excess)
                )
            )
        }

        // 2. Peso bruto vehicular excedido (Numeral 5.1 NOM-012)
        if (restriction.maxWeightTons != null && profile.grossWeightTons > restriction.maxWeightTons) {
            val excess = profile.grossWeightTons - restriction.maxWeightTons
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.GROSS_WEIGHT_EXCEEDED,
                    vehicleMetric = String.format(Locale.US, "%.1f t", profile.grossWeightTons),
                    allowedLimitMetric = String.format(Locale.US, "%.1f t", restriction.maxWeightTons),
                    excessMetric = String.format(Locale.US, "+%.1f t sobre límite", excess)
                )
            )
        }

        // 3. Peso por eje excedido (Numeral 5.1.1 NOM-012)
        if (profile.axleLoadTons != null &&
            restriction.maxAxleLoadTons != null &&
            profile.axleLoadTons > restriction.maxAxleLoadTons
        ) {
            val excess = profile.axleLoadTons - restriction.maxAxleLoadTons
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.AXLE_LOAD_EXCEEDED,
                    vehicleMetric = String.format(Locale.US, "%.1f t/eje", profile.axleLoadTons),
                    allowedLimitMetric = String.format(Locale.US, "%.1f t/eje", restriction.maxAxleLoadTons),
                    excessMetric = String.format(Locale.US, "+%.1f t/eje", excess)
                )
            )
        }

        // 4. Ancho máximo excedido (Numeral 5.2.1 NOM-012)
        if (restriction.maxWidthMeters != null && profile.widthMeters > restriction.maxWidthMeters) {
            val excess = profile.widthMeters - restriction.maxWidthMeters
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.WIDTH_EXCEEDED,
                    vehicleMetric = String.format(Locale.US, "%.2f m", profile.widthMeters),
                    allowedLimitMetric = String.format(Locale.US, "%.2f m", restriction.maxWidthMeters),
                    excessMetric = String.format(Locale.US, "+%.2f m", excess)
                )
            )
        }

        // 5. Longitud máxima excedida (Numeral 5.2.2 NOM-012)
        if (restriction.maxLengthMeters != null && profile.lengthMeters > restriction.maxLengthMeters) {
            val excess = profile.lengthMeters - restriction.maxLengthMeters
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.LENGTH_EXCEEDED,
                    vehicleMetric = String.format(Locale.US, "%.2f m", profile.lengthMeters),
                    allowedLimitMetric = String.format(Locale.US, "%.2f m", restriction.maxLengthMeters),
                    excessMetric = String.format(Locale.US, "+%.2f m", excess)
                )
            )
        }

        // 6. Restricción específica para Doblemente Articulado Full T3-S2-R4 (Numeral 6.1.2 NOM-012)
        if (restriction.fullDoubleProhibited && profile.truckType == TruckType.FULL_DOUBLE_TRAILER) {
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.FULL_DOUBLE_PROHIBITED,
                    vehicleMetric = profile.truckType.nomCode,
                    allowedLimitMetric = "Solo configuraciones C2, C3 o T3-S2",
                    excessMetric = "Doble remolque prohibido fuera de corredor ET/A"
                )
            )
        }

        // 7. Prohibición general a carga pesada o materiales peligrosos
        if (restriction.hgvProhibited || (restriction.hazmatProhibited && profile.cargoType.isHazmat)) {
            violations.add(
                Nom012ViolationDetail(
                    type = Nom012ViolationType.HEAVY_OR_HAZMAT_PROHIBITED,
                    vehicleMetric = "${profile.truckType.nomCode} (${profile.cargoType.displayName})",
                    allowedLimitMetric = if (restriction.hazmatProhibited) "Prohibido Hazmat" else "Prohibido Carga Pesada (hgv=no)",
                    excessMetric = "Acceso restringido por normativa vial"
                )
            )
        }

        return violations
    }

    /**
     * Compara la ubicación GPS actual contra las restricciones obtenidas de Room y las básculas SCT,
     * priorizando cualquier violación a la NOM-012-SCT-2-2017 (como altura de puente insuficiente).
     */
    fun evaluateProximityAndNom012(
        currentPoint: GeoPoint,
        restrictions: List<RoadRestriction>,
        weighStations: List<TruckPoi>,
        profile: TruckProfile,
        dismissedIds: Set<String> = emptySet()
    ): RestrictedPoiWarning? {
        val nearbyRestrictionWarnings = restrictions.mapNotNull { res ->
            val distMeters = (GeoMath.haversineKm(currentPoint, GeoPoint(res.lat, res.lng)) * 1000.0).roundToInt()
            if (distMeters > CRITICAL_RESTRICTION_ALERT_RADIUS_METERS || res.id in dismissedIds) {
                return@mapNotNull null
            }

            val nomViolations = detectNom012Violations(res, profile)
            val isNom012Violation = nomViolations.isNotEmpty()
            val primaryViolation = nomViolations.firstOrNull()
            val missingData = res.verificationStatus != VerificationStatus.VERIFIED_SOURCE

            val severity = when {
                isNom012Violation -> RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK
                missingData || res.kind == RestrictionKind.DRIVING_ADVISORY -> RestrictedPoiAlertSeverity.DRIVING_CAUTION
                else -> RestrictedPoiAlertSeverity.DRIVING_CAUTION
            }

            val detail = buildString {
                append(res.description)
                nomViolations.forEach { v ->
                    append(" · ${v.type.title}: tu unidad ${v.vehicleMetric} vs límite ${v.allowedLimitMetric} (${v.excessMetric}).")
                }
                if (res.gradePercent != null && res.gradePercent >= 5.5) {
                    append(" · Pendiente pronunciada ${res.gradePercent}% (usar freno de motor).")
                }
            }

            val categoryLabel = when {
                primaryViolation?.type == Nom012ViolationType.INSUFFICIENT_BRIDGE_HEIGHT ->
                    "Violación NOM-012: Altura de Puente Insuficiente"
                isNom012Violation ->
                    "Violación NOM-012: ${primaryViolation?.type?.title ?: "Restricción Legal"}"
                res.isBridge || res.maxHeightMeters != null || res.maxWeightTons != null ->
                    "Puente / Paso con Restricción"
                res.gradePercent != null && res.gradePercent >= 5.5 ->
                    "Pendiente Pronunciada (${res.gradePercent}%)"
                res.isProhibitedZone ->
                    "Zona Prohibida para Carga Pesada"
                else -> res.kind.label
            }

            RestrictedPoiWarning(
                id = res.id,
                title = res.title,
                categoryLabel = categoryLabel,
                roadName = res.roadName,
                distanceMeters = distMeters,
                detail = detail,
                sourceCitation = res.sourceCitation,
                severity = severity,
                isBridgeOrClearanceHazard = res.isBridge || res.maxHeightMeters != null,
                isNom012Violation = isNom012Violation,
                nom012Clause = primaryViolation?.type?.nomClause ?: "NOM-012-SCT-2-2017 · Seguridad Vial",
                nom012Violations = nomViolations
            )
        }.minByOrNull { warning ->
            val priorityOffset = when {
                warning.isNom012Violation && warning.isBridgeOrClearanceHazard -> 0
                warning.isNom012Violation -> 2_000
                warning.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK -> 4_000
                else -> 15_000
            }
            priorityOffset + warning.distanceMeters
        }

        if (nearbyRestrictionWarnings != null &&
            nearbyRestrictionWarnings.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK
        ) {
            return nearbyRestrictionWarnings
        }

        val nearbyScale = weighStations
            .filter { it.category == TruckPoiCategory.BASCULA && it.id !in dismissedIds }
            .mapNotNull { poi ->
                val distMeters = (GeoMath.haversineKm(currentPoint, GeoPoint(poi.lat, poi.lng)) * 1000.0).roundToInt()
                if (distMeters > WEIGH_STATION_ALERT_RADIUS_METERS) return@mapNotNull null
                RestrictedPoiWarning(
                    id = poi.id,
                    title = poi.name,
                    categoryLabel = "Báscula / Control de Peso NOM-012 SCT",
                    roadName = poi.highwayRef,
                    distanceMeters = distMeters,
                    detail = "${poi.details}. Tu unidad (${profile.truckType.nomCode}) registra ${profile.grossWeightTons}t en ${profile.axleCount} ejes y ${profile.heightMeters}m de altura.",
                    sourceCitation = poi.verifiedSource,
                    severity = RestrictedPoiAlertSeverity.WEIGH_STATION_CHECK,
                    isBridgeOrClearanceHazard = false,
                    isNom012Violation = false,
                    nom012Clause = "NOM-012-SCT-2-2017 · Verificación de Peso y Dimensiones",
                    nom012Violations = emptyList()
                )
            }
            .minByOrNull { it.distanceMeters }

        return nearbyScale ?: nearbyRestrictionWarnings
    }
}
