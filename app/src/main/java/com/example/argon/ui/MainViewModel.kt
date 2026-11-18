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
import com.example.argon.domain.routing.Nom012ComplianceEvaluator
import com.example.argon.domain.routing.Nom012ViolationDetail
import com.example.argon.location.LocationAccessLevel
import com.example.argon.location.LocationProviderStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RestrictedPoiAlertSeverity(val label: String) {
    CRITICAL_LEGAL_BLOCK("VIOLACIÓN NOM-012 / RESTRICCIÓN CRÍTICA"),
    WEIGH_STATION_CHECK("CONTROL DE PESO / BÁSCULA SCT"),
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
    val isBridgeOrClearanceHazard: Boolean,
    val isNom012Violation: Boolean = false,
    val nom012Clause: String = "NOM-012-SCT-2-2017",
    val nom012Violations: List<Nom012ViolationDetail> = emptyList()
)

data class ArgonUiState(
    val originCode: String = "CMX",
    val destinationCode: String = "QUE",
    val useCurrentGpsAsOrigin: Boolean = true,
    val hasRealGpsFix: Boolean = false,
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
    val locationProviderStatus: LocationProviderStatus = LocationProviderStatus.PERMISSION_REQUIRED,
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

    private val _nom012AlertEvents = MutableSharedFlow<RestrictedPoiWarning>(
        replay = 1,
        extraBufferCapacity = 8
    )
    val nom012AlertEvents: SharedFlow<RestrictedPoiWarning> = _nom012AlertEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(
        ArgonUiState(
            hasLocationPermission = locationController.hasLocationPermission(),
            locationAccessLevel = locationController.getLocationAccessLevel(),
            locationProviderStatus = locationController.providerStatus.value,
            isOnline = repo.isNetworkAvailable(),
            offlineStorageKb = offlineStorage.getDownloadedStorageBytes() / 1024L,
            argonJsonPreview = ArgonTelemetryBus.snapshotJson()
        )
    )
    val uiState: StateFlow<ArgonUiState> = _uiState.asStateFlow()

    private val dismissedWarningIds = MutableStateFlow<Set<String>>(emptySet())
    private var initialRealGpsRouteSynced = false

    init {
        viewModelScope.launch {
            db.ensureSeeded()
            if (locationController.hasLocationPermission()) {
                locationController.startRealGpsUpdates()
            }
            refreshCorridorRoute()
        }
        viewModelScope.launch {
            locationController.providerStatus.collectLatest { status ->
                _uiState.update { it.copy(locationProviderStatus = status) }
            }
        }
        // Capa de lógica NOM-012 en ViewModel: compara reactivamente la ubicación actual del usuario
        // contra la base de datos de restricciones de Room y dispara eventos de alerta ante posibles
        // violaciones de la NOM-012-SCT-2-2017 (como altura de puente insuficiente o exceso de peso).
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
                val warning = Nom012ComplianceEvaluator.evaluateProximityAndNom012(
                    currentPoint = GeoPoint(loc.lat, loc.lng),
                    restrictions = nearbyRestrictions,
                    weighStations = scales,
                    profile = profile,
                    dismissedIds = dismissed
                )
                Triple(loc, nearbyRestrictions, warning)
            }
                .distinctUntilChangedBy { (_, _, warning) -> warning }
                .collectLatest { (loc, nearbyRestrictions, warning) ->
                    _uiState.update {
                        it.copy(
                            activeProximityWarning = warning,
                            activeAlertBanner = if (warning?.isNom012Violation == true) {
                                "${warning.nom012Clause}: ${warning.title} a ${warning.distanceMeters}m"
                            } else {
                                it.activeAlertBanner
                            }
                        )
                    }
                    if (warning != null) {
                        if (warning.isNom012Violation || warning.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK) {
                            _nom012AlertEvents.tryEmit(warning)
                        }
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
                        val voiceMessage = if (warning.isNom012Violation) {
                            "Alerta NOM 012. ${warning.categoryLabel} a ${warning.distanceMeters} metros en ${warning.roadName}: ${warning.title}"
                        } else {
                            "${warning.categoryLabel} a ${warning.distanceMeters} metros: ${warning.title}"
                        }
                        voice.announce(
                            message = voiceMessage,
                            dedupeKey = "prox_${warning.id}",
                            enabled = preferences.value.voiceEnabled,
                            interrupt = warning.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK
                        )
                    }
                }
        }
        viewModelScope.launch {
            locationController.locationFlow.collectLatest { loc ->
                if (!loc.isSimulated) {
                    val currentPt = GeoPoint(loc.lat, loc.lng)
                    val nearestMxState = MexicoFreightCatalog.all32States.minByOrNull {
                        GeoMath.haversineKm(currentPt, GeoPoint(it.centerLat, it.centerLng))
                    }
                    val isInsideMexico = loc.lat in 14.45..32.73 && loc.lng in -117.15..-86.65
                    val detectedOriginCode = if (isInsideMexico && nearestMxState != null) {
                        nearestMxState.code
                    } else {
                        _uiState.value.originCode
                    }

                    if (!_uiState.value.hasRealGpsFix) {
                        _uiState.update {
                            it.copy(
                                hasRealGpsFix = true,
                                originCode = if (it.useCurrentGpsAsOrigin) detectedOriginCode else it.originCode
                            )
                        }
                    }

                    if (!initialRealGpsRouteSynced && _uiState.value.useCurrentGpsAsOrigin) {
                        initialRealGpsRouteSynced = true
                        refreshCorridorRoute()
                    }

                    val st = _uiState.value
                    val route = st.selectedRoute
                    if (st.navigationStatus == NavigationStatus.NAVIGATING && route != null) {
                        val deviationMeters = GeoMath.minDistanceToPolylineMeters(currentPt, route.waypoints)
                        if (deviationMeters > 150.0) {
                            triggerAutomaticReroute(currentPt)
                        } else {
                            updateRealtimeProgressFromGps(currentPt, loc.speedKmh, route)
                        }
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

    /**
     * Evalúa bajo demanda la ubicación indicada (o la ubicación GPS actual) contra las restricciones
     * almacenadas en Room para detectar violaciones de la NOM-012-SCT-2-2017 y emitir el evento de alerta.
     */
    suspend fun evaluateCurrentLocationNom012Compliance(
        lat: Double = locationController.locationFlow.value.lat,
        lng: Double = locationController.locationFlow.value.lng
    ): RestrictedPoiWarning? {
        val nearby = restrictionRepo.findRestrictionsCrossingLocation(lat, lng, radiusKm = 5.0)
        val scales = pois.value.filter { it.category == TruckPoiCategory.BASCULA }
        val warning = Nom012ComplianceEvaluator.evaluateProximityAndNom012(
            currentPoint = GeoPoint(lat, lng),
            restrictions = nearby,
            weighStations = scales,
            profile = activeProfile.value,
            dismissedIds = dismissedWarningIds.value
        )
        if (warning != null) {
            _uiState.update { it.copy(activeProximityWarning = warning) }
            if (warning.isNom012Violation || warning.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK) {
                _nom012AlertEvents.tryEmit(warning)
            }
        }
        return warning
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

    fun refreshRealGpsFix() {
        val anyGranted = locationController.hasLocationPermission()
        val level = locationController.getLocationAccessLevel()
        _uiState.update {
            it.copy(
                hasLocationPermission = anyGranted,
                locationAccessLevel = level
            )
        }
        if (anyGranted) {
            locationController.startRealGpsUpdates()
        }
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
                        else "Permiso de ubicación requerido para detectar la posición real de tu tractocamión."
                    LocationAccessLevel.PRECISE_GRANTED -> null
                }
            )
        }
        if (anyGranted) {
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

    fun setUseCurrentGpsAsOrigin(useGps: Boolean) {
        _uiState.update { it.copy(useCurrentGpsAsOrigin = useGps) }
        if (useGps) {
            refreshRealGpsFix()
        }
        refreshCorridorRoute()
    }

    fun setCorridor(originCode: String, destCode: String, useGpsOrigin: Boolean = false) {
        _uiState.update {
            it.copy(
                originCode = originCode,
                destinationCode = destCode,
                useCurrentGpsAsOrigin = useGpsOrigin
            )
        }
        viewModelScope.launch {
            prefs.setLastCorridor(originCode, destCode)
            refreshCorridorRoute()
        }
    }

    fun setDestinationFromCurrentGps(destCode: String) {
        _uiState.update {
            it.copy(
                destinationCode = destCode,
                useCurrentGpsAsOrigin = true
            )
        }
        viewModelScope.launch {
            prefs.setLastCorridor(_uiState.value.originCode, destCode)
            refreshCorridorRoute()
        }
    }

    fun refreshCorridorRoute() {
        val origin = _uiState.value.originCode
        val dest = _uiState.value.destinationCode
        val useGpsOrigin = _uiState.value.useCurrentGpsAsOrigin
        if (!useGpsOrigin && origin == dest) return
        val profile = activeProfile.value
        val offlineFirst = preferences.value.offlineFirstMode
        val loc = locationController.locationFlow.value
        val liveGpsPoint = if (useGpsOrigin && !loc.isSimulated) {
            GeoPoint(loc.lat, loc.lng)
        } else {
            null
        }
        val liveGpsLabel = if (useGpsOrigin && !loc.isSimulated) {
            loc.currentTown
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCalculating = true,
                    navigationStatus = NavigationStatus.CALCULATING,
                    isOnline = repo.isNetworkAvailable()
                )
            }
            val options = repo.calculateHeavyTruckRoutes(
                originNodeId = origin,
                destinationNodeId = dest,
                profile = profile,
                offlineFirst = offlineFirst,
                liveOriginPoint = liveGpsPoint,
                liveOriginLabel = liveGpsLabel
            )
            val chosen = options.firstOrNull { it.safetyAudit.isCompatible && it.isPrimary }
                ?: options.firstOrNull { it.safetyAudit.isCompatible }

            val newStatus = if (chosen == null) NavigationStatus.BLOCKED_INCOMPATIBLE else NavigationStatus.READY
            val alert = when {
                chosen == null -> "No existe ruta legal compatible para ${profile.heightMeters}m / ${profile.grossWeightTons}t hacia $dest."
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
            append("Iniciando navegación GPS en tiempo real para tractocamión. ")
            if (!route.safetyAudit.safetyGuaranteed) {
                append("Precaución: algunos tramos no tienen dato verificado de gálibo o peso. ")
            }
            if (first != null) append(first.instruction)
        }
        voice.announce(msg, dedupeKey = "start_${route.id}", enabled = preferences.value.voiceEnabled, interrupt = true)
        publishArgonSnapshot()

        if (locationController.hasLocationPermission()) {
            locationController.startRealGpsUpdates()
        }
    }

    fun stopNavigation() {
        _uiState.update { it.copy(navigationStatus = NavigationStatus.IDLE, screenOffMode = false) }
        publishArgonSnapshot()
    }

    /**
     * Recalcula la ruta pesada inmediatamente desde la posición GPS real del tractocamión.
     */
    fun recalculateFromCurrentGps() {
        refreshRealGpsFix()
        val loc = locationController.locationFlow.value
        val currentPt = GeoPoint(loc.lat, loc.lng)
        _uiState.update {
            it.copy(
                useCurrentGpsAsOrigin = true,
                navigationStatus = NavigationStatus.REROUTING,
                activeAlertBanner = "Recalculando corredor pesado desde tu posición GPS actual..."
            )
        }
        voice.announce(
            "Recalculando ruta compatible para tractocamión desde tu ubicación GPS actual.",
            dedupeKey = "reroute_${System.currentTimeMillis()}",
            enabled = preferences.value.voiceEnabled,
            interrupt = true
        )
        triggerAutomaticReroute(currentPt)
    }

    private fun triggerAutomaticReroute(
        deviatedPoint: GeoPoint,
        destCode: String = _uiState.value.destinationCode,
        profile: TruckProfile = activeProfile.value
    ) {
        viewModelScope.launch {
            val options = repo.calculateHeavyTruckRoutes(
                originNodeId = _uiState.value.originCode,
                destinationNodeId = destCode,
                profile = profile,
                offlineFirst = preferences.value.offlineFirstMode,
                liveOriginPoint = deviatedPoint,
                liveOriginLabel = locationController.locationFlow.value.currentTown
            )
            val rerouted = options.firstOrNull { it.safetyAudit.isCompatible }
                ?: repo.router.recalculateFromLocation(deviatedPoint, destCode, profile)

            if (rerouted != null && rerouted.safetyAudit.isCompatible) {
                _uiState.update {
                    it.copy(
                        selectedRoute = rerouted,
                        routeOptions = listOf(rerouted) + options.filter { r -> r.id != rerouted.id },
                        navigationStatus = NavigationStatus.NAVIGATING,
                        currentManeuverIndex = 0,
                        distanceToNextManeuverMeters = rerouted.maneuvers.firstOrNull()?.distanceMeters ?: 0.0,
                        remainingDistanceKm = rerouted.distanceKm,
                        etaRemainingMinutes = rerouted.estimatedMinutes,
                        activeAlertBanner = "Ruta recalculada desde GPS: ${rerouted.summary}"
                    )
                }
                voice.announce(
                    "Nueva ruta segura calculada desde tu posición GPS: ${rerouted.distanceKm} kilómetros.",
                    dedupeKey = "reroute_done_${rerouted.id}",
                    enabled = preferences.value.voiceEnabled
                )
            } else {
                _uiState.update {
                    it.copy(
                        navigationStatus = NavigationStatus.BLOCKED_INCOMPATIBLE,
                        activeAlertBanner = "Alerta: No se encontró retorno o corredor seguro hacia $destCode."
                    )
                }
            }
            publishArgonSnapshot()
        }
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
        locationController.stopRealGpsUpdates()
    }
}
