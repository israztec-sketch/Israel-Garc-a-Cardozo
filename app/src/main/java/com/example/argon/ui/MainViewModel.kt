package com.example.argon.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ArgonApplication
import com.example.argon.api.ArgonTelemetryBus
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.data.local.CockpitPreferences
import com.example.argon.data.local.TruckProfileEntity
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.GeocodePlace
import com.example.argon.domain.model.ManeuverType
import com.example.argon.domain.model.MexicoStateRegion
import com.example.argon.domain.model.NavigationStatus
import com.example.argon.domain.model.RestrictionCrossing
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RestrictionPointFilter
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckPoiCategory
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckRouteOption
import com.example.argon.domain.model.VerificationStatus
import com.example.argon.domain.routing.AcceptanceCheckResult
import com.example.argon.domain.routing.ArgonAcceptanceVerifier
import com.example.argon.domain.routing.GeoMath
import com.example.argon.location.LocationAccessLevel
import com.example.argon.location.LocationProviderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class RestrictedPoiAlertSeverity(val label: String) {
    CRITICAL_LEGAL_BLOCK("RESTRICCIÓN CRÍTICA"),
    WEIGH_STATION_CHECK("CONTROL DE PESO / BÁSCULA"),
    DRIVING_CAUTION("ADVERTENCIA VIAL")
}

data class RestrictedPoiWarning(
    val id: String,
    val title: String,
    val categoryLabel: String,
    val roadName: String,
    val distanceMeters: Int,
    val detail: String,
    val sourceCitation: String,
    val severity: RestrictedPoiAlertSeverity,
    val isBridgeOrClearanceHazard: Boolean
)

data class ArgonUiState(
    val originCode: String = "CMX",
    val destinationCode: String = "QUE",
    val searchQuery: String = "",
    val searchResults: List<GeocodePlace> = emptyList(),
    val isCalculating: Boolean = false,
    val routeOptions: List<TruckRouteOption> = emptyList(),
    val selectedRoute: TruckRouteOption? = null,
    val navigationStatus: NavigationStatus = NavigationStatus.IDLE,
    val currentManeuverIndex: Int = 0,
    val distanceToNextManeuverMeters: Double = 0.0,
    val remainingDistanceKm: Double = 0.0,
    val etaRemainingMinutes: Int = 0,
    val activeAlertBanner: String? = null,
    val screenOffMode: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val locationAccessLevel: LocationAccessLevel = LocationAccessLevel.DENIED,
    val showLocationRationale: Boolean = false,
    val isPermissionPermanentlyDenied: Boolean = false,
    val locationProviderStatus: LocationProviderStatus = LocationProviderStatus.SIMULATED,
    val activeProximityWarning: RestrictedPoiWarning? = null,
    val isOnline: Boolean = false,
    val downloadingStateCode: String? = null,
    val offlineStorageKb: Long = 0L,
    val selectedPoiFilter: TruckPoiCategory? = null,
    val selectedRestrictionFilter: RestrictionPointFilter = RestrictionPointFilter.ALL,
    val acceptanceResults: List<AcceptanceCheckResult> = emptyList(),
    val argonJsonPreview: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ArgonApplication
    private val db = app.database
    private val restrictionRepo = app.restrictionRepository
    private val prefs = app.preferencesManager
    private val repo = app.navigationRepository
    private val offlineStorage = app.offlineMapStorage
    private val locationController = app.locationController
    private val voice = app.voiceNavigator

    val profiles: StateFlow<List<TruckProfile>> = db.truckProfileDao().observeAll()
        .map { entities ->
            if (entities.isEmpty()) MexicoFreightCatalog.defaultProfiles else entities.map { it.toDomain() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MexicoFreightCatalog.defaultProfiles)

    val activeProfile: StateFlow<TruckProfile> = profiles
        .map { list -> list.firstOrNull { it.isActive } ?: list.first() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MexicoFreightCatalog.defaultProfiles.first())

    val regions: StateFlow<List<MexicoStateRegion>> = db.offlineRegionDao().observeAllRegions()
        .map { entities ->
            if (entities.isEmpty()) MexicoFreightCatalog.all32States else entities.map { it.toDomain() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MexicoFreightCatalog.all32States)

    val pois: StateFlow<List<TruckPoi>> = restrictionRepo.allPois
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MexicoFreightCatalog.verifiedPois)

    val restrictions: StateFlow<List<RoadRestriction>> = restrictionRepo.allRestrictions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MexicoFreightCatalog.verifiedRestrictions)

    val recentCrossings: StateFlow<List<RestrictionCrossing>> = restrictionRepo.recentCrossings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<CockpitPreferences> = prefs.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CockpitPreferences())

    val currentLocation = locationController.locationFlow
    val lastVoiceAnnouncement = voice.lastAnnouncement

    private val _uiState = MutableStateFlow(
        ArgonUiState(
            hasLocationPermission = locationController.hasLocationPermission(),
            locationAccessLevel = locationController.getLocationAccessLevel(),
            isOnline = repo.isNetworkAvailable(),
            offlineStorageKb = offlineStorage.getDownloadedStorageBytes() / 1024L,
            argonJsonPreview = ArgonTelemetryBus.snapshotJson()
        )
    )
    val uiState: StateFlow<ArgonUiState> = _uiState.asStateFlow()

    private var simulationJob: Job? = null
    private val dismissedWarningIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            db.ensureSeeded()
            refreshCorridorRoute()
            if (locationController.hasLocationPermission() && !preferences.value.simulationMode) {
                locationController.startRealGpsUpdates()
            }
        }
        viewModelScope.launch {
            locationController.providerStatus.collectLatest { status ->
                _uiState.update { it.copy(locationProviderStatus = status) }
            }
        }
        // Observador espacial reactivo en Room: consulta restricciones dentro de caja delimitadora
        // (puentes bajos, pendientes, zonas prohibidas) y registra cruces con la ubicación del usuario.
        val nearbyRestrictionsFlow = locationController.locationFlow
            .flatMapLatest { loc ->
                restrictionRepo.observeRestrictionsCrossingLocation(loc.lat, loc.lng, radiusKm = 5.0)
                    .map { list -> loc to list }
            }

        viewModelScope.launch {
            combine(
                nearbyRestrictionsFlow,
                restrictionRepo.weighStations,
                activeProfile,
                dismissedWarningIds
            ) { (loc, nearbyRestrictions), scales, profile, dismissed ->
                val warning = evaluateProximityWarning(
                    currentPoint = GeoPoint(loc.lat, loc.lng),
                    restrictions = nearbyRestrictions,
                    pois = scales,
                    profile = profile,
                    dismissedIds = dismissed
                )
                Triple(loc, nearbyRestrictions, warning)
            }
                .distinctUntilChangedBy { (_, _, warning) -> warning }
                .collectLatest { (loc, nearbyRestrictions, warning) ->
                    _uiState.update { it.copy(activeProximityWarning = warning) }
                    if (warning != null) {
                        val matchedRestriction = nearbyRestrictions.firstOrNull { it.id == warning.id }
                        if (matchedRestriction != null) {
                            restrictionRepo.recordCrossingIfNew(
                                RestrictionCrossing(
                                    restrictionId = matchedRestriction.id,
                                    restrictionTitle = matchedRestriction.title,
                                    restrictionCategory = warning.categoryLabel,
                                    roadName = matchedRestriction.roadName,
                                    stateCode = matchedRestriction.stateCode,
                                    userLat = loc.lat,
                                    userLng = loc.lng,
                                    restrictionLat = matchedRestriction.lat,
                                    restrictionLng = matchedRestriction.lng,
                                    distanceMeters = warning.distanceMeters,
                                    severityName = warning.severity.name
                                )
                            )
                        }
                        voice.announce(
                            message = "${warning.categoryLabel} a ${warning.distanceMeters} metros: ${warning.title}",
                            dedupeKey = "prox_${warning.id}",
                            enabled = preferences.value.voiceEnabled,
                            interrupt = warning.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK
                        )
                    }
                }
        }
        viewModelScope.launch {
            locationController.locationFlow.collectLatest { loc ->
                val st = _uiState.value
                val route = st.selectedRoute
                if (st.navigationStatus == NavigationStatus.NAVIGATING && route != null && !loc.isSimulated) {
                    val currentPt = GeoPoint(loc.lat, loc.lng)
                    val deviationMeters = GeoMath.minDistanceToPolylineMeters(currentPt, route.waypoints)
                    if (deviationMeters > 150.0) {
                        triggerAutomaticReroute(currentPt)
                    } else {
                        updateRealtimeProgressFromGps(currentPt, loc.speedKmh, route)
                    }
                }
                publishArgonSnapshot()
            }
        }
        viewModelScope.launch {
            activeProfile.collectLatest {
                publishArgonSnapshot()
            }
        }
    }

    private fun evaluateProximityWarning(
        currentPoint: GeoPoint,
        restrictions: List<RoadRestriction>,
        pois: List<TruckPoi>,
        profile: TruckProfile,
        dismissedIds: Set<String>
    ): RestrictedPoiWarning? {
        // 1. Verificar restricciones críticas (puentes bajos, peso excedido, hgv=no, hazmat) dentro de 3.5 km
        val nearbyCritical = restrictions.mapNotNull { res ->
            val distMeters = (GeoMath.haversineKm(currentPoint, GeoPoint(res.lat, res.lng)) * 1000.0).roundToInt()
            if (distMeters > 3500 || res.id in dismissedIds) return@mapNotNull null

            val heightConflict = res.maxHeightMeters != null && res.maxHeightMeters < profile.heightMeters
            val weightConflict = res.maxWeightTons != null && res.maxWeightTons < profile.grossWeightTons
            val axleConflict = profile.axleLoadTons != null && res.maxAxleLoadTons != null && res.maxAxleLoadTons < profile.axleLoadTons
            val hazmatConflict = res.hazmatProhibited && profile.cargoType.isHazmat
            val missingData = res.verificationStatus != com.example.argon.domain.model.VerificationStatus.VERIFIED_SOURCE

            val isCritical = heightConflict || weightConflict || axleConflict || hazmatConflict || res.hgvProhibited
            val severity = when {
                isCritical -> RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK
                missingData || res.kind == RestrictionKind.DRIVING_ADVISORY -> RestrictedPoiAlertSeverity.DRIVING_CAUTION
                else -> RestrictedPoiAlertSeverity.DRIVING_CAUTION
            }

            val detail = buildString {
                append(res.description)
                if (heightConflict) append(" · Altura vehículo ${profile.heightMeters}m > gálibo ${res.maxHeightMeters}m.")
                if (weightConflict) append(" · Peso vehículo ${profile.grossWeightTons}t > límite ${res.maxWeightTons}t.")
                if (res.gradePercent != null && res.gradePercent >= 5.5) append(" · Pendiente ${res.gradePercent}%.")
            }

            val categoryLabel = when {
                res.isBridge || res.maxHeightMeters != null || res.maxWeightTons != null -> "Puente / Paso con Restricción"
                res.gradePercent != null && res.gradePercent >= 5.5 -> "Pendiente Pronunciada (${res.gradePercent}%)"
                res.isProhibitedZone -> "Zona Prohibida para Carga Pesada"
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
                isBridgeOrClearanceHazard = res.isBridge || res.maxHeightMeters != null
            )
        }.minByOrNull {
            // Priorizar restricciones críticas sobre advertencias informativas
            (if (it.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK) 0 else 10_000) + it.distanceMeters
        }

        if (nearbyCritical != null && nearbyCritical.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK) {
            return nearbyCritical
        }

        // 2. Verificar Básculas SCT en la base de datos dentro de 5.0 km
        val nearbyScale = pois
            .filter { it.category == TruckPoiCategory.BASCULA && it.id !in dismissedIds }
            .mapNotNull { poi ->
                val distMeters = (GeoMath.haversineKm(currentPoint, GeoPoint(poi.lat, poi.lng)) * 1000.0).roundToInt()
                if (distMeters > 5000) return@mapNotNull null
                RestrictedPoiWarning(
                    id = poi.id,
                    title = poi.name,
                    categoryLabel = "Báscula / Control de Peso SCT",
                    roadName = poi.highwayRef,
                    distanceMeters = distMeters,
                    detail = "${poi.details}. Tu unidad registra ${profile.grossWeightTons}t en ${profile.axleCount} ejes.",
                    sourceCitation = poi.verifiedSource,
                    severity = RestrictedPoiAlertSeverity.WEIGH_STATION_CHECK,
                    isBridgeOrClearanceHazard = false
                )
            }
            .minByOrNull { it.distanceMeters }

        return nearbyScale ?: nearbyCritical
    }

    fun dismissProximityWarning() {
        val current = _uiState.value.activeProximityWarning ?: return
        dismissedWarningIds.update { it + current.id }
        _uiState.update { it.copy(activeProximityWarning = null) }
    }

    fun rerouteAroundRestrictedPoi() {
        val current = _uiState.value.activeProximityWarning
        if (current != null) {
            dismissedWarningIds.update { it + current.id }
        }
        _uiState.update { it.copy(activeProximityWarning = null) }
        val loc = locationController.locationFlow.value
        triggerAutomaticReroute(GeoPoint(loc.lat, loc.lng))
    }

    fun updateTelemetryForVerification(lat: Double, lng: Double, roadName: String = "Tramo Verificación") {
        locationController.publishSimulatedTelemetry(
            lat = lat,
            lng = lng,
            speedKmh = 60.0,
            bearingDeg = 0f,
            roadName = roadName,
            town = "Verificación BD",
            stateCode = "MEX"
        )
    }

    private fun updateRealtimeProgressFromGps(
        currentPt: GeoPoint,
        speedKmh: Double,
        route: TruckRouteOption
    ) {
        val destPt = route.waypoints.lastOrNull() ?: return
        val remainingKm = ((GeoMath.haversineKm(currentPt, destPt) * 1.18) * 10.0).roundToInt() / 10.0
        val effectiveSpeed = speedKmh.coerceAtLeast(55.0)
        val etaMin = ((remainingKm / effectiveSpeed) * 60.0).roundToInt().coerceAtLeast(1)
        val nearestManeuverIdx = route.maneuvers.indices.minByOrNull { idx ->
            val m = route.maneuvers[idx]
            GeoMath.haversineKm(currentPt, GeoPoint(m.lat, m.lng))
        } ?: _uiState.value.currentManeuverIndex
        val nextMan = route.maneuvers.getOrNull(nearestManeuverIdx)
        val distToNextM = nextMan?.let {
            GeoMath.haversineKm(currentPt, GeoPoint(it.lat, it.lng)) * 1000.0
        } ?: 0.0

        _uiState.update {
            it.copy(
                currentManeuverIndex = nearestManeuverIdx,
                distanceToNextManeuverMeters = distToNextM,
                remainingDistanceKm = remainingKm,
                etaRemainingMinutes = etaMin
            )
        }
    }

    fun requestLocationRationale(show: Boolean) {
        _uiState.update { it.copy(showLocationRationale = show) }
    }

    fun onLocationPermissionResult(
        fineGranted: Boolean,
        coarseGranted: Boolean = fineGranted,
        shouldShowRationaleAfterDeny: Boolean = false
    ) {
        val level = when {
            fineGranted -> LocationAccessLevel.PRECISE_GRANTED
            coarseGranted -> LocationAccessLevel.APPROXIMATE_ONLY
            else -> LocationAccessLevel.DENIED
        }
        val anyGranted = fineGranted || coarseGranted
        val permanentlyDenied = !anyGranted && !shouldShowRationaleAfterDeny

        _uiState.update {
            it.copy(
                hasLocationPermission = anyGranted,
                locationAccessLevel = level,
                showLocationRationale = false,
                isPermissionPermanentlyDenied = permanentlyDenied,
                activeAlertBanner = when (level) {
                    LocationAccessLevel.APPROXIMATE_ONLY ->
                        "Ubicación aproximada activa. Otorga Ubicación Precisa (Fine) para guiado exacto de tractocamión."
                    LocationAccessLevel.DENIED ->
                        if (permanentlyDenied) "Permiso GPS denegado. Habilítalo en Ajustes del sistema para navegación en tiempo real."
                        else "Permiso de ubicación denegado. Puedes usar Modo Simulación GPS o conceder el permiso."
                    LocationAccessLevel.PRECISE_GRANTED -> null
                }
            )
        }
        if (anyGranted) {
            simulationJob?.cancel()
            viewModelScope.launch { prefs.setSimulationMode(false) }
            locationController.startRealGpsUpdates()
        }
    }

    fun toggleNightMode() {
        viewModelScope.launch { prefs.setNightMode(!preferences.value.nightMode) }
    }

    fun toggleDarkCockpit() {
        viewModelScope.launch { prefs.setDarkCockpit(!preferences.value.darkCockpit) }
    }

    fun toggleVoice() {
        val next = !preferences.value.voiceEnabled
        viewModelScope.launch { prefs.setVoiceEnabled(next) }
        if (next) voice.announce("Guía por voz activada", dedupeKey = "voice_on", enabled = true, interrupt = true)
    }

    fun toggleSimulationMode() {
        val next = !preferences.value.simulationMode
        viewModelScope.launch { prefs.setSimulationMode(next) }
        if (next) {
            locationController.stopRealGpsUpdates()
        } else if (locationController.hasLocationPermission()) {
            simulationJob?.cancel()
            locationController.startRealGpsUpdates()
        }
    }

    fun toggleOfflineFirstMode() {
        viewModelScope.launch {
            prefs.setOfflineFirstMode(!preferences.value.offlineFirstMode)
            refreshCorridorRoute()
        }
    }

    fun setScreenOffMode(enabled: Boolean) {
        _uiState.update { it.copy(screenOffMode = enabled) }
        if (enabled) {
            voice.announce("Modo de conducción con pantalla apagada activado. Alertas de voz activas.", dedupeKey = "screen_off", enabled = preferences.value.voiceEnabled)
        }
    }

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            db.truckProfileDao().setActiveProfile(profileId)
            delay(80)
            refreshCorridorRoute()
        }
    }

    fun saveTruckProfile(profile: TruckProfile) {
        viewModelScope.launch {
            db.truckProfileDao().upsertAndActivate(TruckProfileEntity.fromDomain(profile))
            delay(80)
            refreshCorridorRoute()
            voice.announce(
                "Perfil actualizado: ${profile.heightMeters} metros de altura y ${profile.grossWeightTons} toneladas.",
                dedupeKey = "profile_${profile.heightMeters}_${profile.grossWeightTons}",
                enabled = preferences.value.voiceEnabled
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val hits = repo.searchPlaces(query)
            _uiState.update { it.copy(searchResults = hits) }
        }
    }

    fun setCorridor(originCode: String, destCode: String) {
        _uiState.update { it.copy(originCode = originCode, destinationCode = destCode) }
        viewModelScope.launch {
            prefs.setLastCorridor(originCode, destCode)
            refreshCorridorRoute()
        }
    }

    fun refreshCorridorRoute() {
        val origin = _uiState.value.originCode
        val dest = _uiState.value.destinationCode
        if (origin == dest) return
        val profile = activeProfile.value
        val offlineFirst = preferences.value.offlineFirstMode

        viewModelScope.launch {
            simulationJob?.cancel()
            val locationTimestampAtStart = locationController.locationFlow.value.timestampMs
            _uiState.update {
                it.copy(
                    isCalculating = true,
                    navigationStatus = NavigationStatus.CALCULATING,
                    isOnline = repo.isNetworkAvailable()
                )
            }
            val options = repo.calculateHeavyTruckRoutes(origin, dest, profile, offlineFirst)
            val chosen = options.firstOrNull { it.safetyAudit.isCompatible && it.isPrimary }
                ?: options.firstOrNull { it.safetyAudit.isCompatible }

            val newStatus = if (chosen == null) NavigationStatus.BLOCKED_INCOMPATIBLE else NavigationStatus.READY
            val alert = when {
                chosen == null -> "No existe ruta legal compatible para ${profile.heightMeters}m / ${profile.grossWeightTons}t entre $origin y $dest."
                !chosen.safetyAudit.safetyGuaranteed -> "Advertencia: ${chosen.safetyAudit.missingDataWarnings.firstOrNull() ?: "Datos parciales en ruta"}"
                else -> null
            }

            val firstMan = chosen?.maneuvers?.firstOrNull()
            _uiState.update {
                it.copy(
                    isCalculating = false,
                    routeOptions = options,
                    selectedRoute = chosen,
                    navigationStatus = newStatus,
                    currentManeuverIndex = 0,
                    distanceToNextManeuverMeters = firstMan?.distanceMeters ?: 0.0,
                    remainingDistanceKm = chosen?.distanceKm ?: 0.0,
                    etaRemainingMinutes = chosen?.estimatedMinutes ?: 0,
                    activeAlertBanner = alert
                )
            }

            if (chosen != null) {
                val firstPt = chosen.waypoints.firstOrNull()
                val telemetryUnchanged = locationController.locationFlow.value.timestampMs == locationTimestampAtStart
                if (firstPt != null && preferences.value.simulationMode && telemetryUnchanged) {
                    val secondPt = chosen.waypoints.getOrElse(1) { firstPt }
                    locationController.publishSimulatedTelemetry(
                        lat = firstPt.lat,
                        lng = firstPt.lng,
                        speedKmh = 0.0,
                        bearingDeg = GeoMath.bearingDegrees(firstPt, secondPt),
                        roadName = firstMan?.roadName ?: chosen.label,
                        town = firstMan?.townName ?: origin,
                        stateCode = origin
                    )
                }
            }
            publishArgonSnapshot()
        }
    }

    fun selectRouteOption(option: TruckRouteOption) {
        if (!option.safetyAudit.isCompatible) {
            _uiState.update {
                it.copy(activeAlertBanner = "Ruta bloqueada por seguridad: ${option.safetyAudit.rejectionReasons.firstOrNull()}")
            }
            voice.announce(
                "Atención. Ruta incompatible con las dimensiones de tu tractocamión.",
                dedupeKey = "blocked_${option.id}",
                enabled = preferences.value.voiceEnabled,
                interrupt = true
            )
            return
        }
        val firstMan = option.maneuvers.firstOrNull()
        _uiState.update {
            it.copy(
                selectedRoute = option,
                navigationStatus = NavigationStatus.READY,
                currentManeuverIndex = 0,
                distanceToNextManeuverMeters = firstMan?.distanceMeters ?: 0.0,
                remainingDistanceKm = option.distanceKm,
                etaRemainingMinutes = option.estimatedMinutes,
                activeAlertBanner = if (!option.safetyAudit.safetyGuaranteed) option.safetyAudit.missingDataWarnings.firstOrNull() else null
            )
        }
        publishArgonSnapshot()
    }

    fun startNavigation() {
        val route = _uiState.value.selectedRoute ?: return
        if (!route.safetyAudit.isCompatible) return

        _uiState.update { it.copy(navigationStatus = NavigationStatus.NAVIGATING, currentManeuverIndex = 0) }
        val first = route.maneuvers.firstOrNull()
        val msg = buildString {
            append("Iniciando navegación para tractocamión. ")
            if (!route.safetyAudit.safetyGuaranteed) {
                append("Precaución: algunos tramos no tienen dato verificado de gálibo o peso. ")
            }
            if (first != null) append(first.instruction)
        }
        voice.announce(msg, dedupeKey = "start_${route.id}", enabled = preferences.value.voiceEnabled, interrupt = true)
        publishArgonSnapshot()

        if (preferences.value.simulationMode) {
            startSimulatedDrive(route)
        } else if (locationController.hasLocationPermission()) {
            locationController.startRealGpsUpdates()
        }
    }

    fun stopNavigation() {
        simulationJob?.cancel()
        _uiState.update { it.copy(navigationStatus = NavigationStatus.IDLE, screenOffMode = false) }
        publishArgonSnapshot()
    }

    /**
     * Simula avance realista paso a paso por los waypoints y maniobras del tractocamión.
     */
    private fun startSimulatedDrive(route: TruckRouteOption) {
        simulationJob?.cancel()
        val pts = route.waypoints
        if (pts.size < 2) return
        simulationJob = viewModelScope.launch {
            val totalSteps = (pts.size - 1).coerceAtLeast(1)
            for (i in 0 until totalSteps) {
                if (!isActive) break
                val from = pts[i]
                val to = pts[i + 1]
                val subSteps = 4
                for (s in 0 until subSteps) {
                    if (_uiState.value.navigationStatus != NavigationStatus.NAVIGATING) return@launch
                    val t = s.toDouble() / subSteps
                    val lat = from.lat + (to.lat - from.lat) * t
                    val lng = from.lng + (to.lng - from.lng) * t
                    val progress = (i + t) / totalSteps.toDouble()
                    val manIdx = (progress * (route.maneuvers.size - 1)).toInt().coerceIn(0, route.maneuvers.lastIndex)
                    val maneuver = route.maneuvers[manIdx]
                    val remKm = ((1.0 - progress) * route.distanceKm * 10.0).roundToInt() / 10.0
                    val etaMin = ((1.0 - progress) * route.estimatedMinutes).roundToInt().coerceAtLeast(1)
                    val bearing = GeoMath.bearingDegrees(from, to)

                    locationController.publishSimulatedTelemetry(
                        lat = lat,
                        lng = lng,
                        speedKmh = 78.0,
                        bearingDeg = bearing,
                        roadName = maneuver.roadName,
                        town = maneuver.townName,
                        stateCode = route.passedStates.firstOrNull() ?: "MX"
                    )

                    val alertText = maneuver.curveAdvisory
                        ?: route.safetyAudit.legalRestrictionsFound.firstOrNull {
                            GeoMath.haversineKm(GeoPoint(lat, lng), GeoPoint(it.lat, it.lng)) < 35.0
                        }?.title

                    _uiState.update {
                        it.copy(
                            currentManeuverIndex = manIdx,
                            distanceToNextManeuverMeters = (maneuver.distanceMeters * (1.0 - (progress * 3 % 1.0))).coerceAtLeast(150.0),
                            remainingDistanceKm = remKm,
                            etaRemainingMinutes = etaMin,
                            activeAlertBanner = alertText
                        )
                    }

                    // Anuncios anticipados de curvas, salidas o restricciones
                    val voiceCue = when {
                        maneuver.curveAdvisory != null -> "Atención: ${maneuver.curveAdvisory} en ${maneuver.roadName}"
                        maneuver.type == ManeuverType.TOLL_BOOTH -> "Caseta de cobro próxima en ${maneuver.roadName}"
                        maneuver.type == ManeuverType.EXIT_HIGHWAY -> "Salida próxima hacia ${maneuver.roadName}"
                        else -> maneuver.instruction
                    }
                    voice.announce(voiceCue, dedupeKey = "man_${route.id}_$manIdx", enabled = preferences.value.voiceEnabled)
                    publishArgonSnapshot()
                    delay(1400L)
                }
            }
            _uiState.update {
                it.copy(
                    navigationStatus = NavigationStatus.ARRIVED,
                    remainingDistanceKm = 0.0,
                    etaRemainingMinutes = 0,
                    activeAlertBanner = "Llegada al destino logístico completada"
                )
            }
            voice.announce("Has llegado a tu destino de carga pesada.", dedupeKey = "arrived_${route.id}", enabled = preferences.value.voiceEnabled, interrupt = true)
            publishArgonSnapshot()
        }
    }

    /**
     * Prueba / ejecuta recálculo automático ante desvío del tractocamión (Regla 10).
     */
    fun simulateDeviationAndReroute() {
        val currentDest = _uiState.value.destinationCode
        val profile = activeProfile.value
        simulationJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    navigationStatus = NavigationStatus.REROUTING,
                    activeAlertBanner = "Desvío detectado (>150m). Recalculando corredor pesado..."
                )
            }
            voice.announce(
                "Desvío detectado. Recalculando ruta compatible para tractocamión sin calles residenciales.",
                dedupeKey = "reroute_${System.currentTimeMillis()}",
                enabled = preferences.value.voiceEnabled,
                interrupt = true
            )
            publishArgonSnapshot()
            delay(700L)
            // Simulamos posición desviada hacia Enlace Arco Norte Tula
            val deviatedPt = GeoPoint(20.0520, -99.3410)
            locationController.publishSimulatedTelemetry(
                lat = deviatedPt.lat,
                lng = deviatedPt.lng,
                speedKmh = 64.0,
                bearingDeg = 300f,
                roadName = "Enlace Arco Norte Tula (Desvío)",
                town = "Tula de Allende, Hgo.",
                stateCode = "HID"
            )
            triggerAutomaticReroute(deviatedPt, currentDest, profile)
        }
    }

    private fun triggerAutomaticReroute(
        deviatedPoint: GeoPoint,
        destCode: String = _uiState.value.destinationCode,
        profile: TruckProfile = activeProfile.value
    ) {
        val rerouted = repo.router.recalculateFromLocation(deviatedPoint, destCode, profile)
        if (rerouted != null && rerouted.safetyAudit.isCompatible) {
            _uiState.update {
                it.copy(
                    selectedRoute = rerouted,
                    routeOptions = listOf(rerouted) + it.routeOptions.filter { r -> r.id != rerouted.id },
                    navigationStatus = NavigationStatus.NAVIGATING,
                    currentManeuverIndex = 0,
                    distanceToNextManeuverMeters = rerouted.maneuvers.firstOrNull()?.distanceMeters ?: 0.0,
                    remainingDistanceKm = rerouted.distanceKm,
                    etaRemainingMinutes = rerouted.estimatedMinutes,
                    activeAlertBanner = "Ruta recalculada: ${rerouted.summary}"
                )
            }
            voice.announce(
                "Nueva ruta segura calculada: ${rerouted.distanceKm} kilómetros.",
                dedupeKey = "reroute_done_${rerouted.id}",
                enabled = preferences.value.voiceEnabled
            )
        } else {
            _uiState.update {
                it.copy(
                    navigationStatus = NavigationStatus.BLOCKED_INCOMPATIBLE,
                    activeAlertBanner = "Alerta: No se encontró retorno o corredor seguro tras el desvío."
                )
            }
        }
        publishArgonSnapshot()
    }

    fun downloadOfflineState(stateCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingStateCode = stateCode) }
            offlineStorage.downloadStatePack(stateCode)
            _uiState.update {
                it.copy(
                    downloadingStateCode = null,
                    offlineStorageKb = offlineStorage.getDownloadedStorageBytes() / 1024L
                )
            }
        }
    }

    fun removeOfflineState(stateCode: String) {
        viewModelScope.launch {
            offlineStorage.deleteStatePack(stateCode)
            _uiState.update {
                it.copy(offlineStorageKb = offlineStorage.getDownloadedStorageBytes() / 1024L)
            }
        }
    }

    fun setPoiFilter(category: TruckPoiCategory?) {
        _uiState.update { it.copy(selectedPoiFilter = category) }
    }

    fun setRestrictionFilter(filter: RestrictionPointFilter) {
        _uiState.update { it.copy(selectedRestrictionFilter = filter) }
    }

    fun registerLocalRestrictionPoint(
        title: String,
        category: RestrictionPointFilter,
        roadName: String,
        maxHeightMeters: Double?,
        gradePercent: Double?,
        lat: Double,
        lng: Double,
        stateCode: String
    ) {
        val cleanTitle = title.trim().ifEmpty {
            when (category) {
                RestrictionPointFilter.LOW_BRIDGES -> "Puente Bajo Registrado (${maxHeightMeters ?: 3.8}m)"
                RestrictionPointFilter.STEEP_GRADES -> "Pendiente Pronunciada (${gradePercent ?: 7.0}%)"
                RestrictionPointFilter.PROHIBITED_ZONES -> "Zona Prohibida a Tractocamiones"
                RestrictionPointFilter.ALL -> "Restricción Vial Local"
            }
        }
        val cleanRoad = roadName.trim().ifEmpty { "Tramo Local GPS" }
        val point = RoadRestriction(
            id = "LOCAL_${System.currentTimeMillis()}",
            title = cleanTitle,
            description = when (category) {
                RestrictionPointFilter.LOW_BRIDGES ->
                    "Punto registrado en BD local Room con altura máxima de ${maxHeightMeters ?: 3.80} m."
                RestrictionPointFilter.STEEP_GRADES ->
                    "Pendiente pronunciada registrada en BD local Room (${gradePercent ?: 7.0}%)."
                RestrictionPointFilter.PROHIBITED_ZONES, RestrictionPointFilter.ALL ->
                    "Zona restringida para circulación de vehículos pesados registrada en BD local Room."
            },
            kind = if (category == RestrictionPointFilter.STEEP_GRADES) {
                RestrictionKind.DRIVING_ADVISORY
            } else {
                RestrictionKind.LEGAL_PROHIBITION
            },
            roadName = cleanRoad,
            stateCode = stateCode.ifBlank { "MEX" },
            lat = lat,
            lng = lng,
            maxHeightMeters = if (category == RestrictionPointFilter.LOW_BRIDGES) (maxHeightMeters ?: 3.80) else null,
            hgvProhibited = category == RestrictionPointFilter.PROHIBITED_ZONES,
            isBridge = category == RestrictionPointFilter.LOW_BRIDGES,
            gradePercent = if (category == RestrictionPointFilter.STEEP_GRADES) (gradePercent ?: 7.0) else null,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "Registro Local Conductor (Room DB)"
        )
        viewModelScope.launch {
            restrictionRepo.saveRestrictionPoint(point)
        }
    }

    fun deleteLocalRestrictionPoint(id: String) {
        viewModelScope.launch {
            restrictionRepo.deleteRestrictionPoint(id)
        }
    }

    fun clearCrossingHistory() {
        viewModelScope.launch {
            restrictionRepo.clearCrossingHistory()
        }
    }

    suspend fun loadMapTile(z: Int, x: Int, y: Int): Bitmap? {
        return offlineStorage.getTileBitmap(z, x, y, allowNetwork = !preferences.value.offlineFirstMode && repo.isNetworkAvailable())
    }

    fun runAcceptanceSuite() {
        viewModelScope.launch {
            publishArgonSnapshot()
            val checks = ArgonAcceptanceVerifier.runAllChecks(app)
            _uiState.update {
                it.copy(
                    acceptanceResults = checks,
                    argonJsonPreview = ArgonTelemetryBus.snapshotJson()
                )
            }
        }
    }

    fun refreshArgonSnapshotPreview() {
        publishArgonSnapshot()
        _uiState.update { it.copy(argonJsonPreview = ArgonTelemetryBus.snapshotJson()) }
    }

    private fun publishArgonSnapshot() {
        val st = _uiState.value
        ArgonTelemetryBus.publish(
            location = currentLocation.value,
            profile = activeProfile.value,
            route = st.selectedRoute,
            status = st.navigationStatus,
            remainingKm = st.remainingDistanceKm,
            etaMinutes = st.etaRemainingMinutes
        )
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        locationController.stopRealGpsUpdates()
    }
}
