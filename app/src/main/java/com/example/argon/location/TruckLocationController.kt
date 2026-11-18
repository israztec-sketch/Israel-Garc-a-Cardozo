package com.example.argon.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.data.remote.CartographicApiClient
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.routing.GeoMath
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class LocationAccessLevel(val label: String) {
    DENIED("Sin permiso de ubicación"),
    APPROXIMATE_ONLY("Solo ubicación aproximada"),
    PRECISE_GRANTED("Ubicación precisa (Fine Location)")
}

enum class LocationProviderStatus(val label: String) {
    PERMISSION_REQUIRED("Permiso GPS requerido"),
    GPS_DISABLED("GPS del dispositivo desactivado"),
    APPROXIMATE_ACTIVE("GPS Aproximado Activo"),
    SEARCHING_GPS("Obteniendo señal GPS real (PRIORITY_HIGH_ACCURACY)..."),
    FUSED_ACTIVE("GPS Preciso Activo (Fused High Accuracy)"),
    LEGACY_GPS_ACTIVE("GPS Satelital Nativo Activo"),
    MOCK_OR_EMULATOR_IGNORED("Ubicación simulada/emulador ignorada (esperando GPS real)"),
    SIMULATED("Verificación Interna")
}

private data class ResolvedGeoAddress(
    val roadName: String,
    val townName: String,
    val stateCode: String
)

class TruckLocationController(private val context: Context) {

    companion object {
        const val ACTIVE_LOCATION_INTERVAL_MS = 1000L
        const val MIN_ACTIVE_UPDATE_INTERVAL_MS = 500L
        const val MAX_ACTIVE_UPDATE_DELAY_MS = 1000L

        private val MOCK_PROVIDER_KEYWORDS = listOf("mock", "simulator", "emulator", "fake", "test")

        /**
         * Verifica si una coordenada o proveedor proviene de un proveedor simulado (Mock Location)
         * o de coordenadas sintéticas por defecto de emulador (Googleplex 37.4219983, -122.084 / Null Island 0,0).
         */
        fun isMockOrEmulatorData(
            lat: Double,
            lng: Double,
            isMockFlag: Boolean,
            hasMockExtra: Boolean,
            providerName: String?,
            isEmulatorBuild: Boolean = isRunningOnEmulatorBuild()
        ): Boolean {
            if (lat.isNaN() || lng.isNaN()) return true
            if (isMockFlag || hasMockExtra) return true

            val cleanProvider = providerName?.trim()?.lowercase(Locale.US).orEmpty()
            if (cleanProvider.isNotEmpty() && MOCK_PROVIDER_KEYWORDS.any { cleanProvider.contains(it) }) {
                return true
            }

            // Bloquear Null Island (0.0, 0.0) inyectado por proveedores sin fix satelital real
            if (abs(lat) < 0.0001 && abs(lng) < 0.0001) {
                return true
            }

            // Bloquear coordenada sintética fija por defecto del emulador Android (Mountain View Googleplex)
            val isDefaultEmulatorGoogleplex =
                lat in 37.4215..37.4225 && lng in -122.0846..-122.0834
            if (isDefaultEmulatorGoogleplex && isEmulatorBuild) {
                return true
            }

            return false
        }

        fun isRunningOnEmulatorBuild(): Boolean {
            val fingerprint = Build.FINGERPRINT.orEmpty().lowercase(Locale.US)
            val model = Build.MODEL.orEmpty().lowercase(Locale.US)
            val product = Build.PRODUCT.orEmpty().lowercase(Locale.US)
            val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.US)
            return fingerprint.contains("generic") ||
                fingerprint.contains("emulator") ||
                model.contains("sdk_gphone") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                product.contains("sdk_gphone") ||
                product.contains("emulator") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu")
        }
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val addressCache = ConcurrentHashMap<String, ResolvedGeoAddress>()

    private val _locationFlow = MutableStateFlow(
        TelemetryLocation(
            lat = 19.4326,
            lng = -99.1332,
            speedKmh = 0.0,
            bearingDeg = 0f,
            accuracyMeters = 15f,
            currentRoadName = "Esperando señal GPS real...",
            currentTown = "Detectando ubicación GPS...",
            currentStateCode = "GPS",
            isSimulated = true,
            timestampMs = 0L
        )
    )
    val locationFlow: StateFlow<TelemetryLocation> = _locationFlow.asStateFlow()

    private val _providerStatus = MutableStateFlow(
        when {
            !hasLocationPermission() -> LocationProviderStatus.PERMISSION_REQUIRED
            !isLocationServicesEnabled() -> LocationProviderStatus.GPS_DISABLED
            else -> LocationProviderStatus.SEARCHING_GPS
        }
    )
    val providerStatus: StateFlow<LocationProviderStatus> = _providerStatus.asStateFlow()

    private var fusedCallback: LocationCallback? = null
    private var gpsHardwareListener: LocationListener? = null
    private var networkHardwareListener: LocationListener? = null
    private var activeCancellationToken: CancellationTokenSource? = null

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(): Boolean =
        hasFineLocationPermission() || hasCoarseLocationPermission()

    fun isLocationServicesEnabled(): Boolean {
        val lm = locationManager ?: return true
        val gpsOn = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val netOn = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        return gpsOn || netOn
    }

    fun getLocationAccessLevel(): LocationAccessLevel = when {
        hasFineLocationPermission() -> LocationAccessLevel.PRECISE_GRANTED
        hasCoarseLocationPermission() -> LocationAccessLevel.APPROXIMATE_ONLY
        else -> LocationAccessLevel.DENIED
    }

    /**
     * Construye una solicitud activa de FusedLocationProviderClient con Priority.PRIORITY_HIGH_ACCURACY
     * e intervalos de actualización en tiempo real sin retraso por lotes.
     */
    fun buildActiveHighAccuracyRequest(intervalMs: Long = ACTIVE_LOCATION_INTERVAL_MS): LocationRequest {
        val activeInterval = intervalMs.coerceAtLeast(MIN_ACTIVE_UPDATE_INTERVAL_MS)
        val minInterval = (activeInterval / 2L).coerceAtLeast(MIN_ACTIVE_UPDATE_INTERVAL_MS)
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, activeInterval)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(minInterval)
            .setMaxUpdateDelayMillis(activeInterval.coerceAtMost(MAX_ACTIVE_UPDATE_DELAY_MS))
            .setMinUpdateDistanceMeters(0f)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build()
    }

    /**
     * Determina si un objeto Location de Android proviene de un proveedor simulado (Mock)
     * o de un proveedor sintético de emulador, para ignorarlo y capturar solo GPS real.
     */
    fun isMockOrEmulatorLocation(loc: Location): Boolean {
        val isMockFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            loc.isMock
        } else {
            @Suppress("DEPRECATION")
            loc.isFromMockProvider
        }
        val extras = loc.extras
        val hasMockExtra = extras?.getBoolean("mockLocation", false) == true ||
            extras?.getBoolean("is_mock", false) == true ||
            extras?.getBoolean("mock", false) == true

        return isMockOrEmulatorData(
            lat = loc.latitude,
            lng = loc.longitude,
            isMockFlag = isMockFlag,
            hasMockExtra = hasMockExtra,
            providerName = loc.provider
        )
    }

    /**
     * Flujo reactivo basado en callbackFlow sobre LocationServices (FusedLocationProviderClient)
     * con Priority.PRIORITY_HIGH_ACCURACY e intervalos activos, filtrando ubicaciones simuladas.
     */
    @SuppressLint("MissingPermission")
    fun observeRealtimeCoordinates(intervalMs: Long = ACTIVE_LOCATION_INTERVAL_MS): Flow<TelemetryLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            close()
            return@callbackFlow
        }

        val request = buildActiveHighAccuracyRequest(intervalMs)

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val realLocations = result.locations.filterNot { isMockOrEmulatorLocation(it) }
                val bestReal = realLocations.maxByOrNull { it.time } ?: realLocations.lastOrNull()
                if (bestReal != null) {
                    onRealLocationReceived(bestReal, fromFused = true)
                    trySend(_locationFlow.value)
                } else if (result.locations.isNotEmpty() && _locationFlow.value.isSimulated) {
                    _providerStatus.value = LocationProviderStatus.MOCK_OR_EMULATOR_IGNORED
                }
            }
        }

        _providerStatus.value = LocationProviderStatus.SEARCHING_GPS
        emitBestImmediateLastKnownLocation()

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener {
                startHardwareLocationListeners()
            }

        awaitClose {
            runCatching { fusedClient.removeLocationUpdates(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRealGpsUpdates() {
        val accessLevel = getLocationAccessLevel()
        if (accessLevel == LocationAccessLevel.DENIED) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            return
        }

        stopRealGpsUpdates()

        if (!isLocationServicesEnabled()) {
            _providerStatus.value = LocationProviderStatus.GPS_DISABLED
        } else {
            _providerStatus.value = if (accessLevel == LocationAccessLevel.APPROXIMATE_ONLY) {
                LocationProviderStatus.APPROXIMATE_ACTIVE
            } else {
                LocationProviderStatus.SEARCHING_GPS
            }
        }

        // 1. Emitir de inmediato la última posición real conocida (ignorando cualquier ubicación mock/emulador)
        emitBestImmediateLastKnownLocation()

        // 2. Forzar lectura activa en tiempo real con CurrentLocationRequest y PRIORITY_HIGH_ACCURACY
        val tokenSource = CancellationTokenSource()
        activeCancellationToken = tokenSource
        runCatching {
            val currentReq = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setGranularity(Granularity.GRANULARITY_FINE)
                .setMaxUpdateAgeMillis(0L)
                .setDurationMillis(5000L)
                .build()

            fusedClient.getCurrentLocation(currentReq, tokenSource.token)
                .addOnSuccessListener { freshLoc ->
                    if (freshLoc != null) {
                        if (!isMockOrEmulatorLocation(freshLoc)) {
                            onRealLocationReceived(freshLoc, fromFused = true)
                        } else if (_locationFlow.value.isSimulated) {
                            _providerStatus.value = LocationProviderStatus.MOCK_OR_EMULATOR_IGNORED
                        }
                    }
                }
        }

        // 3. Suscribir actualizaciones activas continuas en FusedLocationProviderClient con PRIORITY_HIGH_ACCURACY
        val request = buildActiveHighAccuracyRequest(ACTIVE_LOCATION_INTERVAL_MS)

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val realLocations = result.locations.filterNot { isMockOrEmulatorLocation(it) }
                val bestReal = realLocations.maxByOrNull { it.time } ?: realLocations.lastOrNull()
                if (bestReal != null) {
                    onRealLocationReceived(bestReal, fromFused = true)
                } else if (result.locations.isNotEmpty() && _locationFlow.value.isSimulated) {
                    _providerStatus.value = LocationProviderStatus.MOCK_OR_EMULATOR_IGNORED
                }
            }
        }
        fusedCallback = callback
        runCatching {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }

        // 4. Suscribir simultáneamente LocationManager (GPS_PROVIDER y NETWORK_PROVIDER) filtrando mocks
        startHardwareLocationListeners()
    }

    @SuppressLint("MissingPermission")
    private fun emitBestImmediateLastKnownLocation() {
        if (!hasLocationPermission()) return

        val candidates = mutableListOf<Location>()
        runCatching {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeUnless { isMockOrEmulatorLocation(it) }
                ?.let { candidates.add(it) }
            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.takeUnless { isMockOrEmulatorLocation(it) }
                ?.let { candidates.add(it) }
        }
        candidates.maxByOrNull { it.time }?.let { bestHardwareLoc ->
            onRealLocationReceived(bestHardwareLoc, fromFused = false)
        }

        runCatching {
            fusedClient.lastLocation.addOnSuccessListener { lastFused ->
                if (lastFused != null) {
                    if (!isMockOrEmulatorLocation(lastFused)) {
                        onRealLocationReceived(lastFused, fromFused = true)
                    } else if (_locationFlow.value.isSimulated) {
                        _providerStatus.value = LocationProviderStatus.MOCK_OR_EMULATOR_IGNORED
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startHardwareLocationListeners() {
        if (!hasLocationPermission()) return
        val lm = locationManager ?: return

        val gpsListener = LocationListener { loc ->
            if (!isMockOrEmulatorLocation(loc)) {
                onRealLocationReceived(loc, fromFused = false)
            } else if (_locationFlow.value.isSimulated) {
                _providerStatus.value = LocationProviderStatus.MOCK_OR_EMULATOR_IGNORED
            }
        }
        gpsHardwareListener = gpsListener
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    ACTIVE_LOCATION_INTERVAL_MS,
                    0f,
                    gpsListener,
                    Looper.getMainLooper()
                )
            }
        }

        val netListener = LocationListener { loc ->
            if (!isMockOrEmulatorLocation(loc)) {
                val current = _locationFlow.value
                if (current.isSimulated || loc.accuracy <= current.accuracyMeters + 25f) {
                    onRealLocationReceived(loc, fromFused = false)
                }
            }
        }
        networkHardwareListener = netListener
        runCatching {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    ACTIVE_LOCATION_INTERVAL_MS,
                    0f,
                    netListener,
                    Looper.getMainLooper()
                )
            }
        }
    }

    private fun onRealLocationReceived(loc: Location, fromFused: Boolean) {
        if (isMockOrEmulatorLocation(loc)) {
            if (_locationFlow.value.isSimulated) {
                _providerStatus.value = LocationProviderStatus.MOCK_OR_EMULATOR_IGNORED
            }
            return
        }
        _providerStatus.value = when {
            !hasFineLocationPermission() -> LocationProviderStatus.APPROXIMATE_ACTIVE
            fromFused -> LocationProviderStatus.FUSED_ACTIVE
            else -> LocationProviderStatus.LEGACY_GPS_ACTIVE
        }
        val telemetry = buildTelemetryFromLocation(loc)
        _locationFlow.value = telemetry
        resolveAddressInBackground(loc.latitude, loc.longitude)
    }

    fun stopRealGpsUpdates() {
        activeCancellationToken?.cancel()
        activeCancellationToken = null
        fusedCallback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        fusedCallback = null
        gpsHardwareListener?.let { runCatching { locationManager?.removeUpdates(it) } }
        gpsHardwareListener = null
        networkHardwareListener?.let { runCatching { locationManager?.removeUpdates(it) } }
        networkHardwareListener = null
    }

    private fun cacheKey(lat: Double, lng: Double): String =
        "${(lat * 500).roundToInt()}_${(lng * 500).roundToInt()}"

    private fun buildTelemetryFromLocation(loc: Location): TelemetryLocation {
        val prev = _locationFlow.value
        val pt = GeoPoint(loc.latitude, loc.longitude)
        val bearing = when {
            loc.hasBearing() && loc.bearing != 0f -> loc.bearing
            !prev.isSimulated && GeoMath.haversineKm(GeoPoint(prev.lat, prev.lng), pt) > 0.005 ->
                GeoMath.bearingDegrees(GeoPoint(prev.lat, prev.lng), pt)
            else -> prev.bearingDeg
        }

        val key = cacheKey(loc.latitude, loc.longitude)
        val cachedAddress = addressCache[key]
        val fallbackAddress = cachedAddress ?: resolveOfflineGeoContext(loc.latitude, loc.longitude)

        return TelemetryLocation(
            lat = loc.latitude,
            lng = loc.longitude,
            speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6) else 0.0,
            bearingDeg = bearing,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
            currentRoadName = fallbackAddress.roadName,
            currentTown = fallbackAddress.townName,
            currentStateCode = fallbackAddress.stateCode,
            isSimulated = false,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Determina el contexto geográfico sin inventar "Mexicali / Tijuana (BCN)" cuando las
     * coordenadas reales están fuera de Baja California o antes de completar la geocodificación inversa.
     */
    private fun resolveOfflineGeoContext(lat: Double, lng: Double): ResolvedGeoAddress {
        val pt = GeoPoint(lat, lng)
        val isInsideMexicoBounds = lat in 14.45..32.73 && lng in -117.15..-86.65

        if (!isInsideMexicoBounds) {
            return ResolvedGeoAddress(
                roadName = String.format(Locale.US, "Posición GPS (%.4f, %.4f)", lat, lng),
                townName = String.format(Locale.US, "Lat %.4f, Lng %.4f", lat, lng),
                stateCode = "GPS"
            )
        }

        val nearestNode = MexicoFreightCatalog.networkNodes.minByOrNull {
            GeoMath.haversineKm(pt, GeoPoint(it.lat, it.lng))
        }
        val nearestNodeDistKm = nearestNode?.let {
            GeoMath.haversineKm(pt, GeoPoint(it.lat, it.lng))
        } ?: Double.MAX_VALUE

        val nearestState = MexicoFreightCatalog.all32States.minByOrNull {
            GeoMath.haversineKm(pt, GeoPoint(it.centerLat, it.centerLng))
        }
        val nearestStateDistKm = nearestState?.let {
            GeoMath.haversineKm(pt, GeoPoint(it.centerLat, it.centerLng))
        } ?: Double.MAX_VALUE

        val stateCode = if (nearestState != null && nearestStateDistKm <= 240.0) {
            nearestState.code
        } else {
            nearestNode?.stateCode ?: "MEX"
        }

        val townName = when {
            nearestNode != null && nearestNodeDistKm <= 45.0 -> nearestNode.name
            nearestState != null && nearestStateDistKm <= 110.0 -> nearestState.name
            else -> String.format(Locale.US, "Coordenadas %.4f, %.4f", lat, lng)
        }

        val roadName = when {
            nearestState != null && nearestStateDistKm <= 120.0 ->
                "Corredor ${nearestState.mainCorridors.firstOrNull() ?: nearestState.name}"
            else -> String.format(Locale.US, "Vialidad GPS (%.4f, %.4f)", lat, lng)
        }

        return ResolvedGeoAddress(
            roadName = roadName,
            townName = townName,
            stateCode = stateCode
        )
    }

    private fun resolveAddressInBackground(lat: Double, lng: Double) {
        val key = cacheKey(lat, lng)
        if (addressCache.containsKey(key)) return

        controllerScope.launch {
            var resolvedRoad: String? = null
            var resolvedTown: String? = null
            var resolvedStateName: String? = null
            var resolvedCountryCode: String? = null

            // 1. Intentar con Geocoder nativo de Android
            runCatching {
                if (Geocoder.isPresent()) {
                    @Suppress("DEPRECATION")
                    val results = Geocoder(context, Locale("es", "MX")).getFromLocation(lat, lng, 1)
                    val addr = results?.firstOrNull()
                    if (addr != null) {
                        resolvedRoad = addr.thoroughfare?.takeIf { it.isNotBlank() }
                            ?: addr.featureName?.takeIf { it.isNotBlank() && it != addr.locality }
                        resolvedTown = addr.locality?.takeIf { it.isNotBlank() }
                            ?: addr.subAdminArea?.takeIf { it.isNotBlank() }
                            ?: addr.subLocality?.takeIf { it.isNotBlank() }
                        resolvedStateName = addr.adminArea?.takeIf { it.isNotBlank() }
                        resolvedCountryCode = addr.countryCode?.uppercase(Locale.US)
                    }
                }
            }

            // 2. Si faltan datos de calle o ciudad, consultar Nominatim Reverse Geocoding (OSM)
            if (resolvedRoad.isNullOrBlank() || resolvedTown.isNullOrBlank()) {
                runCatching {
                    val nom = CartographicApiClient.nominatimApi.reverseGeocode(lat, lng)
                    val addr = nom.address
                    if (addr != null) {
                        if (resolvedRoad.isNullOrBlank()) {
                            resolvedRoad = addr.road ?: addr.highway ?: addr.suburb ?: addr.neighbourhood
                        }
                        if (resolvedTown.isNullOrBlank()) {
                            resolvedTown = addr.city ?: addr.town ?: addr.municipality ?: addr.village ?: addr.county
                        }
                        if (resolvedStateName.isNullOrBlank()) {
                            resolvedStateName = addr.state
                        }
                        if (resolvedCountryCode.isNullOrBlank()) {
                            resolvedCountryCode = addr.countryCode?.uppercase(Locale.US)
                        }
                    }
                }
            }

            if (!resolvedRoad.isNullOrBlank() || !resolvedTown.isNullOrBlank() || !resolvedStateName.isNullOrBlank()) {
                val fallback = resolveOfflineGeoContext(lat, lng)
                val matchedMxState = resolvedStateName?.let { stateName ->
                    MexicoFreightCatalog.all32States.firstOrNull { st ->
                        stateName.contains(st.name, ignoreCase = true) ||
                            st.name.contains(stateName, ignoreCase = true)
                    }
                }
                val stateCode = when {
                    matchedMxState != null -> matchedMxState.code
                    !resolvedStateName.isNullOrBlank() && resolvedCountryCode != null && resolvedCountryCode != "MX" ->
                        "${resolvedStateName!!.take(12)} ($resolvedCountryCode)"
                    !resolvedStateName.isNullOrBlank() -> resolvedStateName!!.take(12)
                    else -> fallback.stateCode
                }

                val town = listOfNotNull(
                    resolvedTown?.takeIf { it.isNotBlank() },
                    resolvedStateName?.takeIf { it.isNotBlank() && it != resolvedTown }
                ).joinToString(", ").ifBlank { fallback.townName }

                val road = resolvedRoad?.takeIf { it.isNotBlank() } ?: town

                val finalAddress = ResolvedGeoAddress(
                    roadName = road,
                    townName = town,
                    stateCode = stateCode
                )
                addressCache[key] = finalAddress

                val cur = _locationFlow.value
                if (!cur.isSimulated && cacheKey(cur.lat, cur.lng) == key) {
                    _locationFlow.value = cur.copy(
                        currentRoadName = finalAddress.roadName,
                        currentTown = finalAddress.townName,
                        currentStateCode = finalAddress.stateCode
                    )
                }
            }
        }
    }

    fun publishSimulatedTelemetry(
        lat: Double,
        lng: Double,
        speedKmh: Double,
        bearingDeg: Float,
        roadName: String,
        town: String,
        stateCode: String
    ) {
        _providerStatus.value = LocationProviderStatus.SIMULATED
        _locationFlow.value = TelemetryLocation(
            lat = lat,
            lng = lng,
            speedKmh = speedKmh,
            bearingDeg = bearingDeg,
            accuracyMeters = 4.0f,
            currentRoadName = roadName,
            currentTown = town,
            currentStateCode = stateCode,
            isSimulated = true,
            timestampMs = System.currentTimeMillis()
        )
    }
}
