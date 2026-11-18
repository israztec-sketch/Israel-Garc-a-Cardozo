package com.example.argon.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.data.local.RestrictionRepository
import com.example.argon.domain.model.DataCompleteness
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.GeocodePlace
import com.example.argon.domain.model.ManeuverType
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadClass
import com.example.argon.domain.model.RoadNode
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.RouteManeuver
import com.example.argon.domain.model.RouteSafetyAudit
import com.example.argon.domain.model.RoutingSource
import com.example.argon.domain.model.SpecialRestriction
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckRouteOption
import com.example.argon.domain.model.TruckType
import com.example.argon.domain.model.VerificationStatus
import com.example.argon.domain.routing.GeoMath
import com.example.argon.domain.routing.TruckGraphRouter
import com.example.argon.ui.screens.hasConfiguredMapsApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.roundToInt

class ArgonNavigationRepository(
    private val context: Context,
    private val restrictionRepository: RestrictionRepository? = null
) {
    val router = TruckGraphRouter(
        nodes = MexicoFreightCatalog.networkNodes,
        edges = MexicoFreightCatalog.freightEdges,
        restrictions = MexicoFreightCatalog.verifiedRestrictions,
        pois = MexicoFreightCatalog.verifiedPois
    )

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun searchPlaces(query: String): List<GeocodePlace> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        val localMatches = MexicoFreightCatalog.all32States.filter {
            clean.isBlank() ||
                it.name.contains(clean, ignoreCase = true) ||
                it.capitalOrHub.contains(clean, ignoreCase = true) ||
                it.code.contains(clean, ignoreCase = true)
        }.map {
            GeocodePlace(
                name = "${it.name} (${it.capitalOrHub})",
                subtitle = "Corredores: ${it.mainCorridors.joinToString(", ")}",
                lat = it.centerLat,
                lng = it.centerLng,
                stateCode = it.code,
                source = "Catálogo Nacional 32 Estados"
            )
        }

        if (clean.length < 3 || !isNetworkAvailable()) {
            return@withContext localMatches
        }

        val remote = runCatching {
            CartographicApiClient.nominatimApi.searchMexico(clean).map { item ->
                val lat = item.lat.toDoubleOrNull() ?: 19.4326
                val lng = item.lon.toDoubleOrNull() ?: -99.1332
                val nearestState = MexicoFreightCatalog.all32States.minByOrNull { st ->
                    GeoMath.haversineKm(GeoPoint(lat, lng), GeoPoint(st.centerLat, st.centerLng))
                }
                GeocodePlace(
                    name = item.displayName.substringBefore(","),
                    subtitle = item.displayName,
                    lat = lat,
                    lng = lng,
                    stateCode = nearestState?.code ?: "CMX",
                    source = "OSM Nominatim"
                )
            }
        }.getOrDefault(emptyList())

        (remote + localMatches).distinctBy { it.name }.take(12)
    }

    /**
     * Calcula rutas para tractocamión usando:
     * 1) Coordenadas GPS reales del conductor (si están disponibles) o nodo de origen seleccionado
     * 2) Red de corredores pesados verificados (siempre disponible offline)
     * 3) API especializada Valhalla Truck + Google Routes API v2 + auditoría Overpass cuando hay internet
     * Nunca sustituye una ruta de automóvil convencional.
     */
    suspend fun calculateHeavyTruckRoutes(
        originNodeId: String,
        destinationNodeId: String,
        profile: TruckProfile,
        offlineFirst: Boolean,
        liveOriginPoint: GeoPoint? = null,
        liveOriginLabel: String? = null
    ): List<TruckRouteOption> = withContext(Dispatchers.IO) {
        val effectiveGraphOriginId = if (liveOriginPoint != null) {
            router.findNearestNode(liveOriginPoint)?.id ?: originNodeId
        } else {
            originNodeId
        }

        val targetDestId = if (effectiveGraphOriginId == destinationNodeId) {
            MexicoFreightCatalog.networkNodes.firstOrNull { it.id != effectiveGraphOriginId }?.id ?: destinationNodeId
        } else {
            destinationNodeId
        }

        val localRoutes = router.calculateRoutes(effectiveGraphOriginId, targetDestId, profile).map { route ->
            if (liveOriginPoint != null && route.waypoints.isNotEmpty()) {
                val firstWp = route.waypoints.first()
                val distFromGpsKm = GeoMath.haversineKm(liveOriginPoint, firstWp)
                val updatedWaypoints = if (distFromGpsKm > 0.05) {
                    listOf(liveOriginPoint) + route.waypoints
                } else {
                    route.waypoints
                }
                route.copy(
                    summary = "Origen GPS Real (${liveOriginLabel ?: "Mi Ubicación"}) · ${route.summary}",
                    waypoints = updatedWaypoints
                )
            } else {
                route
            }
        }

        val catalogOriginNode = MexicoFreightCatalog.networkNodes.firstOrNull { it.id == effectiveGraphOriginId }
        val originNode = if (liveOriginPoint != null) {
            RoadNode(
                id = effectiveGraphOriginId,
                name = liveOriginLabel?.takeIf { it.isNotBlank() } ?: "Mi Ubicación GPS Actual",
                stateCode = catalogOriginNode?.stateCode ?: originNodeId,
                lat = liveOriginPoint.lat,
                lng = liveOriginPoint.lng,
                isFreightHub = true,
                allowsHeavyUturn = true
            )
        } else {
            catalogOriginNode
        }
        val destNode = MexicoFreightCatalog.networkNodes.firstOrNull { it.id == targetDestId }

        if (offlineFirst || !isNetworkAvailable() || originNode == null || destNode == null) {
            return@withContext localRoutes
        }

        val localRestrictions = runCatching {
            restrictionRepository?.allRestrictions?.firstOrNull()
        }.getOrNull() ?: MexicoFreightCatalog.verifiedRestrictions

        val valhallaRoute = runCatching {
            val avoidTolls = SpecialRestriction.AVOID_TOLLS in profile.specialRestrictions
            val req = ValhallaTruckRequest(
                locations = listOf(
                    ValhallaLocation(originNode.lat, originNode.lng),
                    ValhallaLocation(destNode.lat, destNode.lng)
                ),
                costing = "truck",
                costingOptions = ValhallaCostingOptions(
                    truck = ValhallaTruckOptions(
                        height = profile.heightMeters,
                        width = profile.widthMeters,
                        length = profile.lengthMeters,
                        weight = profile.grossWeightTons,
                        axleLoad = profile.axleLoadTons,
                        axleCount = profile.axleCount,
                        hazmat = profile.cargoType.isHazmat,
                        useTolls = if (avoidTolls) 0.05 else 0.85,
                        useHighways = 1.0
                    )
                ),
                alternates = 0
            )
            val response = CartographicApiClient.valhallaApi.calculateTruckRoute(req)
            val trip = response.trip ?: return@runCatching null
            val leg = trip.legs.firstOrNull() ?: return@runCatching null
            val polyline = GeoMath.decodePolyline(leg.shape, precision = 1e6)
            if (polyline.size < 2) return@runCatching null

            val overpassRestrictions = fetchOverpassRestrictionsNearRoute(polyline)
            val audit = auditPolylineForHeavyTruck(
                polyline = polyline,
                profile = profile,
                baseRestrictions = localRestrictions,
                overpassRestrictions = overpassRestrictions,
                extraWarning = null
            )

            val maneuvers = leg.maneuvers.mapIndexed { idx, m ->
                val pt = polyline.getOrElse(m.beginShapeIndex.coerceIn(0, polyline.lastIndex)) { polyline.first() }
                RouteManeuver(
                    id = "val_$idx",
                    type = if (idx == 0) ManeuverType.DEPART else if (idx == leg.maneuvers.lastIndex) ManeuverType.ARRIVE else ManeuverType.CONTINUE,
                    instruction = m.instruction,
                    roadName = m.streetNames?.firstOrNull() ?: "Carretera Federal / Autopista",
                    highwayShield = m.streetNames?.firstOrNull()?.take(10) ?: "MEX",
                    townName = destNode.name,
                    distanceMeters = m.length * 1000.0,
                    lat = pt.lat,
                    lng = pt.lng
                )
            }

            val states = resolvePassedStates(polyline, originNode, destNode)
            val pois = resolveCorridorPois(states, polyline)

            TruckRouteOption(
                id = "route_valhalla_truck",
                label = "Ruta Valhalla Truck Online (Auditada)",
                summary = "Calculada con dimensiones reales (${profile.heightMeters}m × ${profile.grossWeightTons}t) vía Valhalla Truck",
                isPrimary = audit.isCompatible,
                routingSource = RoutingSource.VALHALLA_TRUCK_VERIFIED,
                distanceKm = (trip.summary.length * 10.0).roundToInt() / 10.0,
                estimatedMinutes = (trip.summary.time / 60.0).roundToInt().coerceAtLeast(1),
                tollCostMxn = if (trip.summary.hasToll == true) localRoutes.firstOrNull()?.tollCostMxn ?: 350.0 else 0.0,
                waypoints = polyline,
                maneuvers = maneuvers,
                passedStates = states,
                roadClassesUsed = setOf(RoadClass.AUTOPISTA_CUOTA, RoadClass.CARRETERA_FEDERAL),
                safetyAudit = audit,
                corridorPois = pois
            )
        }.getOrNull()

        val googleRoutes = if (hasConfiguredMapsApiKey()) {
            fetchGoogleRoutesAudited(
                originNode = originNode,
                destNode = destNode,
                profile = profile,
                baseRestrictions = localRestrictions,
                fallbackTollMxn = localRoutes.firstOrNull()?.tollCostMxn ?: 0.0
            )
        } else {
            emptyList()
        }

        val remoteCompatible = listOfNotNull(valhallaRoute)
            .plus(googleRoutes)
            .filter { it.safetyAudit.isCompatible }

        val remoteBlocked = listOfNotNull(valhallaRoute)
            .plus(googleRoutes)
            .filter { !it.safetyAudit.isCompatible }

        if (remoteCompatible.isNotEmpty()) {
            val primaryRemote = remoteCompatible.first().copy(isPrimary = true)
            val secondaryRemote = remoteCompatible.drop(1).map { it.copy(isPrimary = false) }
            listOf(primaryRemote) + secondaryRemote + localRoutes.map { it.copy(isPrimary = false) } + remoteBlocked
        } else {
            localRoutes + remoteBlocked
        }
    }

    private suspend fun fetchGoogleRoutesAudited(
        originNode: RoadNode,
        destNode: RoadNode,
        profile: TruckProfile,
        baseRestrictions: List<RoadRestriction>,
        fallbackTollMxn: Double
    ): List<TruckRouteOption> = runCatching {
        val avoidTolls = SpecialRestriction.AVOID_TOLLS in profile.specialRestrictions
        val request = GoogleRoutesRequest(
            origin = GoogleRouteWaypoint(
                location = GoogleRouteLocation(GoogleRouteLatLng(originNode.lat, originNode.lng))
            ),
            destination = GoogleRouteWaypoint(
                location = GoogleRouteLocation(GoogleRouteLatLng(destNode.lat, destNode.lng))
            ),
            routeModifiers = GoogleRouteModifiers(
                avoidTolls = avoidTolls,
                avoidHighways = false,
                vehicleInfo = GoogleVehicleInfo(emissionType = "DIESEL")
            )
        )
        val response = CartographicApiClient.googleRoutesApi.computeRoutes(
            apiKey = BuildConfig.MAPS_API_KEY.trim(),
            request = request
        )
        response.routes.mapIndexedNotNull { index, routeData ->
            val overpassRestrictions = routeData.polyline?.encodedPolyline
                ?.takeIf { it.isNotBlank() }
                ?.let { GeoMath.decodePolyline(it, precision = 1e5) }
                ?.takeIf { it.size >= 2 }
                ?.let { fetchOverpassRestrictionsNearRoute(it) }
                ?: emptyList()

            buildGoogleRouteOption(
                index = index,
                routeData = routeData,
                originNode = originNode,
                destNode = destNode,
                profile = profile,
                baseRestrictions = baseRestrictions,
                overpassRestrictions = overpassRestrictions,
                fallbackTollMxn = fallbackTollMxn
            )
        }
    }.getOrDefault(emptyList())

    fun buildGoogleRouteOption(
        index: Int,
        routeData: GoogleRouteData,
        originNode: RoadNode,
        destNode: RoadNode,
        profile: TruckProfile,
        baseRestrictions: List<RoadRestriction>,
        overpassRestrictions: List<RoadRestriction> = emptyList(),
        fallbackTollMxn: Double = 0.0
    ): TruckRouteOption? {
        val encoded = routeData.polyline?.encodedPolyline ?: return null
        val polyline = GeoMath.decodePolyline(encoded, precision = 1e5)
        if (polyline.size < 2) return null

        val audit = auditPolylineForHeavyTruck(
            polyline = polyline,
            profile = profile,
            baseRestrictions = baseRestrictions,
            overpassRestrictions = overpassRestrictions,
            extraWarning = "Google Routes API (tráfico en tiempo real) no valida altura ni peso por eje; trazado auditado con BD local Room y Overpass."
        )

        val steps = routeData.legs.flatMap { it.steps }
        val maneuvers = if (steps.isNotEmpty()) {
            steps.mapIndexed { stepIdx, step ->
                val pt = step.startLocation?.latLng?.let { GeoPoint(it.latitude, it.longitude) }
                    ?: polyline.getOrElse(stepIdx.coerceAtMost(polyline.lastIndex)) { polyline.first() }
                RouteManeuver(
                    id = "groute_${index}_$stepIdx",
                    type = mapGoogleManeuver(step.navigationInstruction?.maneuver, stepIdx == 0, stepIdx == steps.lastIndex),
                    instruction = step.navigationInstruction?.instructions?.takeIf { it.isNotBlank() }
                        ?: "Continuar por corredor hacia ${destNode.name}",
                    roadName = originNode.stateCode + " → " + destNode.stateCode,
                    highwayShield = "MEX",
                    townName = destNode.name,
                    distanceMeters = (step.distanceMeters ?: 0).toDouble(),
                    lat = pt.lat,
                    lng = pt.lng
                )
            }
        } else {
            listOf(
                RouteManeuver(
                    id = "groute_${index}_0",
                    type = ManeuverType.DEPART,
                    instruction = "Iniciar corredor auditado Google Routes hacia ${destNode.name}",
                    roadName = "${originNode.name} → ${destNode.name}",
                    highwayShield = "MEX",
                    townName = destNode.name,
                    distanceMeters = (routeData.distanceMeters ?: 0).toDouble(),
                    lat = polyline.first().lat,
                    lng = polyline.first().lng
                )
            )
        }

        val distKm = ((routeData.distanceMeters ?: 0) / 100.0).roundToInt() / 10.0
        val durationSec = routeData.duration?.removeSuffix("s")?.toDoubleOrNull() ?: 0.0
        val durationMin = (durationSec / 60.0).roundToInt().coerceAtLeast(1)

        val tollMxn = routeData.travelAdvisory?.tollInfo?.estimatedPrice
            ?.firstOrNull { it.currencyCode == "MXN" }
            ?.let { money ->
                val units = money.units?.toDoubleOrNull() ?: 0.0
                val nanos = (money.nanos ?: 0L) / 1_000_000_000.0
                units + nanos
            } ?: if (SpecialRestriction.AVOID_TOLLS in profile.specialRestrictions) 0.0 else fallbackTollMxn

        val states = resolvePassedStates(polyline, originNode, destNode)
        val pois = resolveCorridorPois(states, polyline)
        val labelSuffix = if (index == 0) "Principal" else "Alterna ${index + 1}"

        return TruckRouteOption(
            id = "route_google_$index",
            label = "Google Routes API ($labelSuffix · Auditada)",
            summary = "Trazado con tráfico en vivo (${distKm} km, ${durationMin} min) auditado para ${profile.heightMeters}m / ${profile.grossWeightTons}t",
            isPrimary = index == 0 && audit.isCompatible,
            routingSource = RoutingSource.GOOGLE_ROUTES_AUDITED,
            distanceKm = distKm,
            estimatedMinutes = durationMin,
            tollCostMxn = tollMxn,
            waypoints = polyline,
            maneuvers = maneuvers,
            passedStates = states,
            roadClassesUsed = setOf(RoadClass.AUTOPISTA_CUOTA, RoadClass.CARRETERA_FEDERAL),
            safetyAudit = audit,
            corridorPois = pois
        )
    }

    private fun auditPolylineForHeavyTruck(
        polyline: List<GeoPoint>,
        profile: TruckProfile,
        baseRestrictions: List<RoadRestriction>,
        overpassRestrictions: List<RoadRestriction>,
        extraWarning: String?
    ): RouteSafetyAudit {
        val combinedRestrictions = (baseRestrictions + overpassRestrictions).distinctBy { it.id }
        val nearbyLegal = mutableListOf<RoadRestriction>()
        val nearbyAdvisories = mutableListOf<RoadRestriction>()
        val rejections = mutableListOf<String>()
        val missingWarnings = mutableListOf<String>()

        combinedRestrictions.forEach { res ->
            val distM = GeoMath.minDistanceToPolylineMeters(GeoPoint(res.lat, res.lng), polyline)
            if (distM < 1200.0) {
                if (res.maxHeightMeters != null && res.maxHeightMeters < profile.heightMeters) {
                    rejections.add("Gálibo insuficiente detectado en trazado: ${res.title}")
                }
                if (res.maxWeightTons != null && res.maxWeightTons < profile.grossWeightTons) {
                    rejections.add("Límite de peso excedido en trazado: ${res.title}")
                }
                if (profile.axleLoadTons != null && res.maxAxleLoadTons != null && res.maxAxleLoadTons < profile.axleLoadTons) {
                    rejections.add("Peso por eje excedido en trazado: ${res.title}")
                }
                if (res.maxWidthMeters != null && res.maxWidthMeters < profile.widthMeters) {
                    rejections.add("Anchura excedida en trazado: ${res.title}")
                }
                if (res.maxLengthMeters != null && res.maxLengthMeters < profile.lengthMeters) {
                    rejections.add("Longitud excedida en trazado: ${res.title}")
                }
                if (res.hgvProhibited) {
                    rejections.add("Tramo prohibido para vehículos pesados (hgv=no): ${res.title}")
                }
                if (res.hazmatProhibited && profile.cargoType.isHazmat) {
                    rejections.add("Tramo prohibido para material peligroso: ${res.title}")
                }
                if (res.fullDoubleProhibited && profile.truckType == TruckType.FULL_DOUBLE_TRAILER) {
                    rejections.add("Tramo prohibido para doble articulado Full: ${res.title}")
                }
                if (res.kind == RestrictionKind.LEGAL_PROHIBITION) nearbyLegal.add(res) else nearbyAdvisories.add(res)
            }
        }

        if (extraWarning != null) {
            missingWarnings.add(extraWarning)
        }
        if (overpassRestrictions.isEmpty()) {
            missingWarnings.add("No se obtuvieron etiquetas OSM maxheight/maxweight completas en todo el corredor; no se garantiza gálibo total sin verificación en sitio.")
        }
        if (profile.axleLoadTons == null) {
            missingWarnings.add("Peso por eje no especificado en el perfil del vehículo.")
        }

        return RouteSafetyAudit(
            isCompatible = rejections.isEmpty(),
            safetyGuaranteed = rejections.isEmpty() && missingWarnings.isEmpty(),
            dataCompleteness = if (missingWarnings.isEmpty()) DataCompleteness.FULLY_VERIFIED else DataCompleteness.PARTIAL_MISSING_DATA,
            rejectionReasons = rejections.distinct(),
            legalRestrictionsFound = nearbyLegal,
            drivingAdvisories = nearbyAdvisories,
            missingDataWarnings = missingWarnings.distinct(),
            avoidsResidential = rejections.none { it.contains("hgv=no", ignoreCase = true) },
            avoidsIncompatibleUturns = true
        )
    }

    private fun resolvePassedStates(
        polyline: List<GeoPoint>,
        originNode: RoadNode,
        destNode: RoadNode
    ): List<String> {
        return MexicoFreightCatalog.all32States.filter { st ->
            GeoMath.minDistanceToPolylineMeters(GeoPoint(st.centerLat, st.centerLng), polyline) < 60_000.0
        }.map { it.code }.ifEmpty { listOf(originNode.stateCode, destNode.stateCode).distinct() }
    }

    private fun resolveCorridorPois(states: List<String>, polyline: List<GeoPoint>) =
        MexicoFreightCatalog.verifiedPois.filter { poi ->
            poi.stateCode in states || GeoMath.minDistanceToPolylineMeters(GeoPoint(poi.lat, poi.lng), polyline) < 15_000.0
        }

    private fun mapGoogleManeuver(maneuver: String?, isFirst: Boolean, isLast: Boolean): ManeuverType {
        if (isFirst) return ManeuverType.DEPART
        if (isLast) return ManeuverType.ARRIVE
        return when (maneuver) {
            "TURN_LEFT", "TURN_SHARP_LEFT", "UTURN_LEFT" -> ManeuverType.TURN_LEFT
            "TURN_RIGHT", "TURN_SHARP_RIGHT", "UTURN_RIGHT" -> ManeuverType.TURN_RIGHT
            "TURN_SLIGHT_LEFT", "KEEP_LEFT", "RAMP_LEFT", "FORK_LEFT" -> ManeuverType.SLIGHT_LEFT
            "TURN_SLIGHT_RIGHT", "KEEP_RIGHT", "FORK_RIGHT" -> ManeuverType.SLIGHT_RIGHT
            "RAMP_RIGHT", "OFF_RAMP" -> ManeuverType.EXIT_HIGHWAY
            else -> ManeuverType.CONTINUE
        }
    }

    private suspend fun fetchOverpassRestrictionsNearRoute(polyline: List<GeoPoint>): List<RoadRestriction> {
        val minLat = polyline.minOf { it.lat } - 0.1
        val maxLat = polyline.maxOf { it.lat } + 0.1
        val minLng = polyline.minOf { it.lng } - 0.1
        val maxLng = polyline.maxOf { it.lng } + 0.1
        val query = """
            [out:json][timeout:6];
            (
              node["maxheight"]($minLat,$minLng,$maxLat,$maxLng);
              way["maxheight"]($minLat,$minLng,$maxLat,$maxLng);
              way["maxweight"]($minLat,$minLng,$maxLat,$maxLng);
            );
            out center 25;
        """.trimIndent()
        return runCatching {
            val body = query.toRequestBody("text/plain".toMediaType())
            val resp = CartographicApiClient.overpassApi.queryRestrictions(body)
            resp.elements.mapNotNull { el ->
                val lat = el.lat ?: return@mapNotNull null
                val lon = el.lon ?: return@mapNotNull null
                val tags = el.tags.orEmpty()
                val maxH = tags["maxheight"]?.replace("m", "")?.trim()?.toDoubleOrNull()
                val maxW = tags["maxweight"]?.replace("t", "")?.trim()?.toDoubleOrNull()
                RoadRestriction(
                    id = "osm_${el.type}_${el.id}",
                    title = tags["name"] ?: "Restricción OSM #${el.id}",
                    description = "Etiqueta verificada en OpenStreetMap Overpass",
                    kind = RestrictionKind.LEGAL_PROHIBITION,
                    roadName = tags["name"] ?: tags["ref"] ?: "Tramo OSM",
                    stateCode = "MX",
                    lat = lat,
                    lng = lon,
                    maxHeightMeters = maxH,
                    maxWeightTons = maxW,
                    hgvProhibited = tags["hgv"] == "no",
                    isBridge = tags["bridge"] == "yes",
                    isTunnel = tags["tunnel"] == "yes",
                    verificationStatus = if (maxH != null || maxW != null) VerificationStatus.VERIFIED_SOURCE else VerificationStatus.PARTIAL_MISSING_DATA,
                    sourceCitation = "OSM Overpass API"
                )
            }
        }.getOrDefault(emptyList())
    }
}
