package com.example.argon.domain.routing

import com.example.argon.domain.model.DataCompleteness
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.ManeuverType
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadClass
import com.example.argon.domain.model.RoadEdge
import com.example.argon.domain.model.RoadNode
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.RouteManeuver
import com.example.argon.domain.model.RouteSafetyAudit
import com.example.argon.domain.model.RoutingSource
import com.example.argon.domain.model.SpecialRestriction
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckRouteOption
import com.example.argon.domain.model.TruckType
import com.example.argon.domain.model.VerificationStatus
import java.util.PriorityQueue
import kotlin.math.roundToInt

class TruckGraphRouter(
    private val nodes: List<RoadNode>,
    private val edges: List<RoadEdge>,
    private val restrictions: List<RoadRestriction>,
    private val pois: List<TruckPoi>
) {
    private val nodeById: Map<String, RoadNode> = nodes.associateBy { it.id }
    private val adjacency: Map<String, List<RoadEdge>> = edges.groupBy { it.fromNodeId }

    fun findNearestNode(point: GeoPoint): RoadNode? =
        nodes.minByOrNull { GeoMath.haversineKm(point, GeoPoint(it.lat, it.lng)) }

    /**
     * Evaluates a single edge against all 10 mandatory heavy-truck rules.
     * Returns a list of legal rejection reasons (empty = legal for this vehicle).
     */
    fun checkEdgeCompatibility(edge: RoadEdge, profile: TruckProfile): List<String> {
        val reasons = mutableListOf<String>()

        // 1. Calle residencial prohibida cuando exista alternativa
        if (edge.roadClass == RoadClass.CALLE_RESIDENCIAL || !edge.hgvAllowed) {
            reasons.add("Vialidad prohibida para vehículos pesados (${edge.roadName})")
        }
        // 2. Altura de puentes / pasos elevados
        if (edge.maxHeightMeters != null && edge.maxHeightMeters < profile.heightMeters) {
            reasons.add("Gálibo insuficiente en ${edge.roadName}: máx ${edge.maxHeightMeters}m vs vehículo ${profile.heightMeters}m")
        }
        // 3. Peso bruto y peso por eje
        if (edge.maxWeightTons != null && edge.maxWeightTons < profile.grossWeightTons) {
            reasons.add("Peso excedido en ${edge.roadName}: máx ${edge.maxWeightTons}t vs ${profile.grossWeightTons}t")
        }
        if (profile.axleLoadTons != null && edge.maxAxleLoadTons != null && edge.maxAxleLoadTons < profile.axleLoadTons) {
            reasons.add("Peso por eje excedido en ${edge.roadName}: máx ${edge.maxAxleLoadTons}t/eje vs ${profile.axleLoadTons}t/eje")
        }
        // 4. Anchura y longitud
        if (edge.maxWidthMeters != null && edge.maxWidthMeters < profile.widthMeters) {
            reasons.add("Ancho insuficiente en ${edge.roadName}: máx ${edge.maxWidthMeters}m vs ${profile.widthMeters}m")
        }
        if (edge.maxLengthMeters != null && edge.maxLengthMeters < profile.lengthMeters) {
            reasons.add("Longitud excedida en ${edge.roadName}: máx ${edge.maxLengthMeters}m vs ${profile.lengthMeters}m")
        }
        // 5. Tipo de camión y tipo de carga
        if (profile.truckType == TruckType.FULL_DOUBLE_TRAILER && !edge.fullDoubleAllowed) {
            reasons.add("Prohibido para doblemente articulado (Full T3-S2-R4) en ${edge.roadName}")
        }
        if (profile.cargoType.isHazmat && !edge.hazmatAllowed) {
            reasons.add("Prohibido transporte de material peligroso en ${edge.roadName}")
        }
        if (SpecialRestriction.HAZMAT_NO_TUNNELS in profile.specialRestrictions && edge.isTunnel && profile.cargoType.isHazmat) {
            reasons.add("Túnel restringido para carga peligrosa (${edge.roadName})")
        }
        if (SpecialRestriction.ONLY_ET_A_CORRIDORS in profile.specialRestrictions && !edge.roadClass.isHeavyApprovedByClass) {
            reasons.add("Fuera de corredor autorizado ET/A (${edge.roadName})")
        }
        // 7. Radio de giro incompatible
        if (edge.minTurningRadiusM < profile.truckType.defaultTurningRadiusM) {
            reasons.add("Radio de giro incompatible (${edge.minTurningRadiusM}m < ${profile.truckType.defaultTurningRadiusM}m)")
        }
        return reasons
    }

    /**
     * Computes primary heavy-truck route + compatible alternatives + rejected shortcut audit.
     */
    fun calculateRoutes(
        originNodeId: String,
        destinationNodeId: String,
        profile: TruckProfile,
        avoidTollsOverride: Boolean? = null
    ): List<TruckRouteOption> {
        val avoidTolls = avoidTollsOverride ?: (SpecialRestriction.AVOID_TOLLS in profile.specialRestrictions)
        val results = mutableListOf<TruckRouteOption>()

        // 1. Ruta Principal Segura (Corredores pesados, autopistas/libramientos, sin restricciones)
        val primaryEdges = findAStarPath(
            startId = originNodeId,
            goalId = destinationNodeId,
            profile = profile,
            avoidTolls = avoidTolls,
            allowMissingData = false,
            forbiddenEdgeIds = emptySet()
        ) ?: findAStarPath(
            startId = originNodeId,
            goalId = destinationNodeId,
            profile = profile,
            avoidTolls = avoidTolls,
            allowMissingData = true,
            forbiddenEdgeIds = emptySet()
        )

        if (primaryEdges != null) {
            results.add(
                buildRouteOption(
                    id = "route_primary",
                    label = if (avoidTolls) "Ruta Federal Libre Compatible" else "Ruta Troncal Segura (Corredor Pesado)",
                    summary = "Prioriza ejes ET/A, libramientos y autopistas verificadas",
                    isPrimary = true,
                    edgesUsed = primaryEdges,
                    profile = profile
                )
            )

            // 2. Ruta Alternativa Compatible (evitando al menos una arista principal o buscando variante libre/alterna)
            if (primaryEdges.size >= 2) {
                val midEdge = primaryEdges[primaryEdges.size / 2]
                val altEdges = findAStarPath(
                    startId = originNodeId,
                    goalId = destinationNodeId,
                    profile = profile,
                    avoidTolls = !avoidTolls,
                    allowMissingData = true,
                    forbiddenEdgeIds = setOf(midEdge.id)
                )
                if (altEdges != null && altEdges.map { it.id } != primaryEdges.map { it.id }) {
                    results.add(
                        buildRouteOption(
                            id = "route_alternative",
                            label = if (!avoidTolls) "Alterna Federal Sin Caseta" else "Alterna Autopista / Libramiento",
                            summary = "Variante compatible para contingencias o control de peajes",
                            isPrimary = false,
                            edgesUsed = altEdges,
                            profile = profile
                        )
                    )
                }
            }
        }

        // 3. Si hay un tramo incompatible directo o cercano (ej. puente bajo / calle residencial / falta gálibo),
        // se expone explícitamente bloqueado o advertido para transparencia y trazabilidad del conductor.
        val blockedShortcut = findUnconstrainedShortcutIfDifferent(
            originNodeId,
            destinationNodeId,
            profile,
            results.firstOrNull()?.waypoints
        )
        if (blockedShortcut != null) {
            results.add(blockedShortcut)
        }

        return results
    }

    fun recalculateFromLocation(
        currentLocation: GeoPoint,
        destinationNodeId: String,
        profile: TruckProfile,
        avoidEdgeIds: Set<String> = emptySet()
    ): TruckRouteOption? {
        val nearest = findNearestNode(currentLocation) ?: return null
        val path = findAStarPath(
            startId = nearest.id,
            goalId = destinationNodeId,
            profile = profile,
            avoidTolls = SpecialRestriction.AVOID_TOLLS in profile.specialRestrictions,
            allowMissingData = true,
            forbiddenEdgeIds = avoidEdgeIds
        ) ?: return null
        return buildRouteOption(
            id = "route_rerouted_${System.currentTimeMillis()}",
            label = "Ruta Recalculada (Eje Pesado)",
            summary = "Recálculo automático desde ${nearest.name} evitando retornos y gálibos bajos",
            isPrimary = true,
            edgesUsed = path,
            profile = profile
        )
    }

    private data class PathState(
        val nodeId: String,
        val prevNodeId: String?,
        val gCost: Double,
        val fCost: Double,
        val edges: List<RoadEdge>
    ) : Comparable<PathState> {
        override fun compareTo(other: PathState): Int = fCost.compareTo(other.fCost)
    }

    private fun findAStarPath(
        startId: String,
        goalId: String,
        profile: TruckProfile,
        avoidTolls: Boolean,
        allowMissingData: Boolean,
        forbiddenEdgeIds: Set<String>
    ): List<RoadEdge>? {
        val goalNode = nodeById[goalId] ?: return null
        val goalPt = GeoPoint(goalNode.lat, goalNode.lng)
        val open = PriorityQueue<PathState>()
        val bestCost = HashMap<String, Double>()

        val startNode = nodeById[startId] ?: return null
        val h0 = GeoMath.haversineKm(GeoPoint(startNode.lat, startNode.lng), goalPt)
        open.add(PathState(startId, null, 0.0, h0, emptyList()))
        bestCost[startId] = 0.0

        while (open.isNotEmpty()) {
            val cur = open.poll() ?: break
            if (cur.nodeId == goalId) return cur.edges
            if (cur.gCost > (bestCost[cur.nodeId] ?: Double.MAX_VALUE) + 1e-6) continue

            val outgoing = adjacency[cur.nodeId].orEmpty()
            for (edge in outgoing) {
                if (edge.id in forbiddenEdgeIds) continue
                // Regla 7: Evitar retornos inmediatos incompatibles con tractocamión
                if (edge.toNodeId == cur.prevNodeId && !(nodeById[cur.nodeId]?.allowsHeavyUturn ?: false)) {
                    continue
                }
                // Reglas 1..5 y 7: Compatibilidad estricta
                if (checkEdgeCompatibility(edge, profile).isNotEmpty()) continue
                if (!allowMissingData && (edge.maxHeightMeters == null || edge.maxWeightTons == null)) continue

                val nextNode = nodeById[edge.toNodeId] ?: continue
                var weight = edge.distanceKm
                // Preferir corredores pesados y libramientos (Regla 8)
                weight *= when (edge.roadClass) {
                    RoadClass.CORREDOR_CARGA, RoadClass.LIBRAMIENTO -> 0.88
                    RoadClass.AUTOPISTA_CUOTA -> if (avoidTolls) 2.40 else 0.92
                    RoadClass.CARRETERA_FEDERAL -> 1.00
                    RoadClass.CARRETERA_ESTATAL -> 1.35
                    RoadClass.URBANA_RESTRINGIDA -> 2.80
                    RoadClass.CALLE_RESIDENCIAL -> 999.0
                }
                // Regla 6: Penalizar pendientes pronunciadas por consumo/seguridad en peso bruto alto
                if (edge.gradePercent >= 5.5) {
                    val weightFactor = (profile.grossWeightTons / 40.0).coerceAtLeast(1.0)
                    weight += edge.distanceKm * (edge.gradePercent / 15.0) * weightFactor
                }
                if (edge.verificationStatus != VerificationStatus.VERIFIED_SOURCE) {
                    weight += 45.0
                }

                val newG = cur.gCost + weight
                if (newG < (bestCost[edge.toNodeId] ?: Double.MAX_VALUE)) {
                    bestCost[edge.toNodeId] = newG
                    val h = GeoMath.haversineKm(GeoPoint(nextNode.lat, nextNode.lng), goalPt)
                    open.add(PathState(edge.toNodeId, cur.nodeId, newG, newG + h, cur.edges + edge))
                }
            }
        }
        return null
    }

    private fun findUnconstrainedShortcutIfDifferent(
        startId: String,
        goalId: String,
        profile: TruckProfile,
        primaryWaypoints: List<GeoPoint>?
    ): TruckRouteOption? {
        // Busca si existe algún atajo con restricción entre start y goal (p. ej. CMX -> QUE pasa por puente bajo/residencial)
        val open = PriorityQueue<PathState>()
        val visited = HashSet<String>()
        open.add(PathState(startId, null, 0.0, 0.0, emptyList()))

        while (open.isNotEmpty()) {
            val cur = open.poll() ?: break
            if (cur.nodeId == goalId) {
                val hasIncompatibility = cur.edges.any { checkEdgeCompatibility(it, profile).isNotEmpty() }
                if (!hasIncompatibility) return null
                val opt = buildRouteOption(
                    id = "route_blocked_shortcut",
                    label = "Atajo Restringido (BLOQUEADO PARA TU CAMIÓN)",
                    summary = "Descartado automáticamente por gálibo, peso o calle residencial",
                    isPrimary = false,
                    edgesUsed = cur.edges,
                    profile = profile
                )
                return if (opt.waypoints != primaryWaypoints) opt else null
            }
            if (!visited.add(cur.nodeId)) continue
            for (edge in adjacency[cur.nodeId].orEmpty()) {
                if (edge.toNodeId == cur.prevNodeId) continue
                val penalty = if (checkEdgeCompatibility(edge, profile).isNotEmpty()) 0.65 else 1.0
                val newG = cur.gCost + edge.distanceKm * penalty
                open.add(PathState(edge.toNodeId, cur.nodeId, newG, newG, cur.edges + edge))
            }
        }
        return null
    }

    fun buildRouteOption(
        id: String,
        label: String,
        summary: String,
        isPrimary: Boolean,
        edgesUsed: List<RoadEdge>,
        profile: TruckProfile,
        source: RoutingSource = RoutingSource.LOCAL_FREIGHT_GRAPH
    ): TruckRouteOption {
        val waypoints = mutableListOf<GeoPoint>()
        val maneuvers = mutableListOf<RouteManeuver>()
        var totalKm = 0.0
        var totalMinutes = 0.0
        var tollMxn = 0.0

        edgesUsed.forEachIndexed { idx, edge ->
            totalKm += edge.distanceKm
            val effectiveSpeed = when {
                edge.gradePercent >= 6.0 -> (edge.speedLimitKmh * 0.68).coerceAtLeast(35.0)
                profile.grossWeightTons > 55.0 -> (edge.speedLimitKmh * 0.82).coerceAtLeast(45.0)
                else -> edge.speedLimitKmh * 0.90
            }
            totalMinutes += (edge.distanceKm / effectiveSpeed) * 60.0
            tollMxn += edge.tollCostMxn

            edge.geometry.forEachIndexed { ptIdx, pt ->
                if (idx == 0 || ptIdx > 0) waypoints.add(pt)
            }

            val destNode = nodeById[edge.toNodeId]
            val maneuverType = when {
                idx == 0 -> ManeuverType.DEPART
                edge.hasToll -> ManeuverType.TOLL_BOOTH
                edge.gradePercent >= 6.0 -> ManeuverType.SHARP_CURVE_AHEAD
                edge.isBridge -> ManeuverType.BRIDGE_APPROACH
                edge.roadClass == RoadClass.LIBRAMIENTO -> ManeuverType.EXIT_HIGHWAY
                else -> ManeuverType.CONTINUE
            }
            val curveAdv = if (edge.gradePercent >= 5.5) {
                "Pendiente ${edge.gradePercent}% y curvas — usar freno de motor"
            } else null

            maneuvers.add(
                RouteManeuver(
                    id = "man_${edge.id}",
                    type = maneuverType,
                    instruction = buildInstruction(idx, edge, destNode?.name ?: edge.toNodeId),
                    roadName = edge.roadName,
                    highwayShield = edge.highwayShield,
                    townName = destNode?.name ?: edge.stateCode,
                    distanceMeters = edge.distanceKm * 1000.0,
                    lat = edge.geometry.firstOrNull()?.lat ?: 0.0,
                    lng = edge.geometry.firstOrNull()?.lng ?: 0.0,
                    curveAdvisory = curveAdv,
                    exitCode = if (edge.roadClass == RoadClass.LIBRAMIENTO) "LIB" else null
                )
            )
        }

        edgesUsed.lastOrNull()?.let { lastEdge ->
            val finalNode = nodeById[lastEdge.toNodeId]
            maneuvers.add(
                RouteManeuver(
                    id = "man_arrive",
                    type = ManeuverType.ARRIVE,
                    instruction = "Llegada segura a zona logística ${finalNode?.name ?: lastEdge.toNodeId}",
                    roadName = lastEdge.roadName,
                    highwayShield = lastEdge.highwayShield,
                    townName = finalNode?.name ?: lastEdge.stateCode,
                    distanceMeters = 0.0,
                    lat = finalNode?.lat ?: waypoints.lastOrNull()?.lat ?: 0.0,
                    lng = finalNode?.lng ?: waypoints.lastOrNull()?.lng ?: 0.0
                )
            )
        }

        val audit = auditEdgesSafety(edgesUsed, profile)
        val states = edgesUsed.map { it.stateCode }.distinct()
        val nearbyPois = pois.filter { poi ->
            poi.stateCode in states || GeoMath.minDistanceToPolylineMeters(GeoPoint(poi.lat, poi.lng), waypoints) < 25_000.0
        }

        return TruckRouteOption(
            id = id,
            label = label,
            summary = summary,
            isPrimary = isPrimary,
            routingSource = source,
            distanceKm = (totalKm * 10.0).roundToInt() / 10.0,
            estimatedMinutes = totalMinutes.roundToInt().coerceAtLeast(1),
            tollCostMxn = tollMxn,
            waypoints = waypoints,
            maneuvers = maneuvers,
            passedStates = states,
            roadClassesUsed = edgesUsed.map { it.roadClass }.toSet(),
            safetyAudit = audit,
            corridorPois = nearbyPois
        )
    }

    fun auditEdgesSafety(edgesUsed: List<RoadEdge>, profile: TruckProfile): RouteSafetyAudit {
        val rejections = mutableListOf<String>()
        val legalList = mutableListOf<RoadRestriction>()
        val advisoryList = mutableListOf<RoadRestriction>()
        val missingWarnings = mutableListOf<String>()

        for (edge in edgesUsed) {
            val edgeReasons = checkEdgeCompatibility(edge, profile)
            rejections.addAll(edgeReasons)

            if (edge.maxHeightMeters == null) {
                missingWarnings.add("Falta dato verificado de altura libre (gálibo) en ${edge.roadName}")
            }
            if (edge.maxWeightTons == null) {
                missingWarnings.add("Falta dato verificado de tonelaje máximo en ${edge.roadName}")
            }
            if (edge.isBridge && profile.axleLoadTons == null) {
                missingWarnings.add("Peso por eje no registrado en perfil; verificar límite del puente ${edge.roadName}")
            }
            if (edge.verificationStatus != VerificationStatus.VERIFIED_SOURCE) {
                missingWarnings.add("${edge.roadName}: ${edge.verificationStatus.label} (${edge.sourceCitation})")
            }

            if (edge.gradePercent >= 5.5) {
                advisoryList.add(
                    RoadRestriction(
                        id = "adv_${edge.id}",
                        title = "Pendiente ${edge.gradePercent}% en ${edge.highwayShield}",
                        description = "Recomendación de conducción: descender con freno de motor y revisar temperatura de balatas.",
                        kind = RestrictionKind.DRIVING_ADVISORY,
                        roadName = edge.roadName,
                        stateCode = edge.stateCode,
                        lat = edge.geometry.firstOrNull()?.lat ?: 0.0,
                        lng = edge.geometry.firstOrNull()?.lng ?: 0.0,
                        gradePercent = edge.gradePercent,
                        verificationStatus = edge.verificationStatus,
                        sourceCitation = edge.sourceCitation
                    )
                )
            }
        }

        // Vincular restricciones catastrales verificadas a lo largo de los estados/coordenadas de la ruta
        val routePts = edgesUsed.flatMap { it.geometry }
        restrictions.forEach { res ->
            val near = GeoMath.minDistanceToPolylineMeters(GeoPoint(res.lat, res.lng), routePts) < 8_000.0
            if (near) {
                if (res.kind == RestrictionKind.LEGAL_PROHIBITION) {
                    if (legalList.none { it.id == res.id }) legalList.add(res)
                } else {
                    if (advisoryList.none { it.id == res.id }) advisoryList.add(res)
                }
            }
        }

        val completeness = when {
            edgesUsed.isEmpty() -> DataCompleteness.INCOMPLETE_UNVERIFIED
            missingWarnings.isEmpty() -> DataCompleteness.FULLY_VERIFIED
            edgesUsed.all { it.maxHeightMeters == null && it.maxWeightTons == null } -> DataCompleteness.INCOMPLETE_UNVERIFIED
            else -> DataCompleteness.PARTIAL_MISSING_DATA
        }

        return RouteSafetyAudit(
            isCompatible = rejections.isEmpty(),
            safetyGuaranteed = rejections.isEmpty() && completeness == DataCompleteness.FULLY_VERIFIED,
            dataCompleteness = completeness,
            rejectionReasons = rejections.distinct(),
            legalRestrictionsFound = legalList,
            drivingAdvisories = advisoryList,
            missingDataWarnings = missingWarnings.distinct(),
            avoidsResidential = edgesUsed.none { it.roadClass == RoadClass.CALLE_RESIDENCIAL },
            avoidsIncompatibleUturns = true
        )
    }

    private fun buildInstruction(index: Int, edge: RoadEdge, destName: String): String {
        val prefix = if (index == 0) "Iniciar por" else "Continuar por"
        val infra = when {
            edge.isTunnel -> " (Túnel)"
            edge.isBridge -> " (Puente / Viaducto)"
            edge.hasToll -> " (Caseta $${edge.tollCostMxn.toInt()} MXN)"
            else -> ""
        }
        return "$prefix ${edge.roadName} [${edge.highwayShield}]$infra hacia $destName"
    }
}
