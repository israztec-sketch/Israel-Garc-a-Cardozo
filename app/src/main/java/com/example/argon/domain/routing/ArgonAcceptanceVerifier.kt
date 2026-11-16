package com.example.argon.domain.routing

import android.content.Context
import android.net.Uri
import com.example.argon.api.ArgonNavigationContentProvider
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.domain.model.CargoType
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.RoadClass
import com.example.argon.domain.model.SpecialRestriction
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckType

data class AcceptanceCheckResult(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String
)

object ArgonAcceptanceVerifier {

    fun runAllChecks(context: Context? = null): List<AcceptanceCheckResult> {
        val router = TruckGraphRouter(
            nodes = MexicoFreightCatalog.networkNodes,
            edges = MexicoFreightCatalog.freightEdges,
            restrictions = MexicoFreightCatalog.verifiedRestrictions,
            pois = MexicoFreightCatalog.verifiedPois
        )
        val results = mutableListOf<AcceptanceCheckResult>()

        // 1. Regla 1 & 2: Evitar calle residencial y puente bajo (3.75m) para tractocamión de 4.20m
        val t3s2 = MexicoFreightCatalog.defaultProfiles.first()
        val cmxQro = router.calculateRoutes("CMX", "QUE", t3s2)
        val primary = cmxQro.firstOrNull { it.isPrimary }
        val avoidsResAndBridge = primary != null &&
            primary.safetyAudit.isCompatible &&
            primary.safetyAudit.avoidsResidential &&
            RoadClass.CALLE_RESIDENCIAL !in primary.roadClassesUsed &&
            cmxQro.any { it.id == "route_blocked_shortcut" && !it.safetyAudit.isCompatible }

        results.add(
            AcceptanceCheckResult(
                id = "CHK_RES_BRIDGE",
                title = "1. Evita calles residenciales y puentes bajos (4.20m vs 3.75m)",
                passed = avoidsResAndBridge,
                detail = if (avoidsResAndBridge) {
                    "Ruta principal '${primary?.label}' (${primary?.distanceKm} km) evita Azcapotzalco residencial y gálibo 3.75m Tultitlán."
                } else {
                    "Fallo en validación de gálibo o calle residencial."
                }
            )
        )

        // 2. Regla 3: Límite de peso bruto y peso por eje (Puente Viejo Tula 24t vs 46.5t)
        val heavyArcoToHid = router.calculateRoutes("ARCO_TULA", "HID", t3s2)
        val heavyPrimary = heavyArcoToHid.firstOrNull { it.isPrimary }
        val avoidedWeightLimit = heavyPrimary != null &&
            heavyPrimary.safetyAudit.isCompatible &&
            heavyPrimary.waypoints.none { it.lat == 20.0480 && it.lng == -99.3460 }

        results.add(
            AcceptanceCheckResult(
                id = "CHK_WEIGHT",
                title = "2. Evita puentes con límite de peso inferior (24t vs 46.5t)",
                passed = avoidedWeightLimit,
                detail = if (avoidedWeightLimit) {
                    "Ruta evita Puente Viejo Río Tula (24t / 7t eje) usando corredor MEX-85D."
                } else {
                    "No se evitó puente con restricción de tonelaje."
                }
            )
        )

        // 3. Regla 5: Restricción por tipo de carga Hazmat (Túnel El Sinaloense MEX-40D)
        val tankerHazmat = t3s2.copy(
            id = 3L,
            truckType = TruckType.TANKER,
            cargoType = CargoType.HAZMAT_FLAMMABLE,
            specialRestrictions = setOf(SpecialRestriction.HAZMAT_NO_TUNNELS)
        )
        val durToSin = router.calculateRoutes("DUR", "SIN", tankerHazmat)
        val hazmatSafe = durToSin.firstOrNull { it.isPrimary }
        val avoidsHazmatTunnel = hazmatSafe != null &&
            hazmatSafe.safetyAudit.isCompatible &&
            hazmatSafe.waypoints.none { it.lat == 23.5335 && it.lng == -105.8390 }

        results.add(
            AcceptanceCheckResult(
                id = "CHK_HAZMAT",
                title = "3. Respeta restricción de túneles para Material Peligroso",
                passed = avoidsHazmatTunnel,
                detail = if (avoidsHazmatTunnel) {
                    "Pipa Hazmat desviada de Túnel El Sinaloense hacia corredor seguro (${hazmatSafe?.distanceKm} km)."
                } else {
                    "Fallo en desvío de túnel Hazmat."
                }
            )
        )

        // 4. Regla de Veracidad: Tramo sin datos de gálibo/peso genera advertencia y NO garantiza seguridad
        val missingEdge = MexicoFreightCatalog.freightEdges.first { it.id == "E_PUENTE_QRO_MISS" }
        val auditMissing = router.auditEdgesSafety(listOf(missingEdge), t3s2)
        val veracityOk = !auditMissing.safetyGuaranteed && auditMissing.missingDataWarnings.isNotEmpty()
        results.add(
            AcceptanceCheckResult(
                id = "CHK_VERACITY",
                title = "4. Regla de veracidad ante datos faltantes de gálibo/peso",
                passed = veracityOk,
                detail = if (veracityOk) {
                    "Advertencias activas: ${auditMissing.missingDataWarnings.first()}"
                } else {
                    "Se garantizó seguridad indebidamente con datos faltantes."
                }
            )
        )

        // 5. Regla 10: Recálculo automático al desviarse de la ruta
        val rerouted = router.recalculateFromLocation(
            currentLocation = GeoPoint(20.0520, -99.3410), // Desvío en Arco Norte Tula
            destinationNodeId = "QUE",
            profile = t3s2
        )
        val rerouteOk = rerouted != null && rerouted.safetyAudit.isCompatible && rerouted.distanceKm > 0
        results.add(
            AcceptanceCheckResult(
                id = "CHK_REROUTE",
                title = "5. Recálculo automático compatible al desviarse",
                passed = rerouteOk,
                detail = if (rerouteOk) {
                    "Recalculada ruta segura desde Arco Norte Tula (${rerouted?.distanceKm} km, ${rerouted?.estimatedMinutes} min)."
                } else {
                    "Fallo en recálculo automático."
                }
            )
        )

        // 6. Fase 3: Cobertura verificable de los 32 estados de México y 9 categorías de POIs
        val statesCount = MexicoFreightCatalog.all32States.size
        val poiCategories = MexicoFreightCatalog.verifiedPois.map { it.category }.distinct().size
        val nationalOk = statesCount == 32 && poiCategories == 9
        results.add(
            AcceptanceCheckResult(
                id = "CHK_32_STATES",
                title = "6. Cobertura nacional 32 estados y 9 tipos de POI pesados",
                passed = nationalOk,
                detail = "Estados configurados: $statesCount/32 | Categorías POI cubiertas: $poiCategories/9."
            )
        )

        // 7. Fase 3: Consulta independiente a interfaz ContentProvider ARGÓN (si hay Context)
        if (context != null) {
            val providerOk = runCatching {
                val uri = Uri.withAppendedPath(ArgonNavigationContentProvider.BASE_URI, "snapshot")
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).contains("ARGON-TRUCK-1.0")
                } ?: false
            }.getOrDefault(false)
            results.add(
                AcceptanceCheckResult(
                    id = "CHK_ARGON_API",
                    title = "7. Interfaz independiente ARGÓN (ContentProvider)",
                    passed = providerOk,
                    detail = if (providerOk) {
                        "ContentProvider respondió protocolo ARGON-TRUCK-1.0 con vehículo, ubicación, ruta y estado."
                    } else {
                        "No se pudo consultar ArgonNavigationContentProvider."
                    }
                )
            )
        }

        return results
    }
}
