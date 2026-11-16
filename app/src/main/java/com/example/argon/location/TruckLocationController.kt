package com.example.argon.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.routing.GeoMath
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

enum class LocationAccessLevel(val label: String) {
    DENIED("Sin permiso de ubicación"),
    APPROXIMATE_ONLY("Solo ubicación aproximada"),
    PRECISE_GRANTED("Ubicación precisa (Fine Location)")
}

enum class LocationProviderStatus(val label: String) {
    PERMISSION_REQUIRED("Permiso GPS requerido"),
    APPROXIMATE_ACTIVE("GPS Aproximado (Coarse)"),
    SEARCHING_GPS("Buscando señal satelital..."),
    FUSED_ACTIVE("LocationServices Alta Precisión"),
    LEGACY_GPS_ACTIVE("GPS Nativo (LocationManager)"),
    SIMULATED("Simulación GPS")
}

class TruckLocationController(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _locationFlow = MutableStateFlow(
        TelemetryLocation(
            lat = 19.4326,
            lng = -99.1332,
            speedKmh = 0.0,
            bearingDeg = 340f,
            accuracyMeters = 10f,
            currentRoadName = "Circuito Exterior / México-Querétaro MEX-57D",
            currentTown = "CDMX Norte / Vallejo",
            currentStateCode = "CMX",
            isSimulated = true,
            timestampMs = System.currentTimeMillis()
        )
    )
    val locationFlow: StateFlow<TelemetryLocation> = _locationFlow.asStateFlow()

    private val _providerStatus = MutableStateFlow(
        if (hasLocationPermission()) LocationProviderStatus.SIMULATED
        else LocationProviderStatus.PERMISSION_REQUIRED
    )
    val providerStatus: StateFlow<LocationProviderStatus> = _providerStatus.asStateFlow()

    private var fusedCallback: LocationCallback? = null
    private var legacyListener: LocationListener? = null

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

    fun getLocationAccessLevel(): LocationAccessLevel = when {
        hasFineLocationPermission() -> LocationAccessLevel.PRECISE_GRANTED
        hasCoarseLocationPermission() -> LocationAccessLevel.APPROXIMATE_ONLY
        else -> LocationAccessLevel.DENIED
    }

    /**
     * Flujo reactivo basado en callbackFlow sobre LocationServices (FusedLocationProviderClient)
     * para obtener coordenadas precisas en tiempo real.
     */
    @SuppressLint("MissingPermission")
    fun observeRealtimeCoordinates(intervalMs: Long = 2000L): Flow<TelemetryLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            _providerStatus.value = LocationProviderStatus.PERMISSION_REQUIRED
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(1000L))
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    _providerStatus.value = LocationProviderStatus.FUSED_ACTIVE
                    val telemetry = buildTelemetryFromLocation(loc)
                    _locationFlow.value = telemetry
                    trySend(telemetry)
                }
            }
        }

        _providerStatus.value = LocationProviderStatus.SEARCHING_GPS
        fusedClient.lastLocation.addOnSuccessListener { last ->
            if (last != null) {
                _providerStatus.value = LocationProviderStatus.FUSED_ACTIVE
                val telemetry = buildTelemetryFromLocation(last)
                _locationFlow.value = telemetry
                trySend(telemetry)
            }
        }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener {
                startLegacyLocationManagerFallback()
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
        _providerStatus.value = if (accessLevel == LocationAccessLevel.APPROXIMATE_ONLY) {
            LocationProviderStatus.APPROXIMATE_ACTIVE
        } else {
            LocationProviderStatus.SEARCHING_GPS
        }

        fusedClient.lastLocation.addOnSuccessListener { last ->
            if (last != null) {
                _providerStatus.value = if (accessLevel == LocationAccessLevel.PRECISE_GRANTED) {
                    LocationProviderStatus.FUSED_ACTIVE
                } else {
                    LocationProviderStatus.APPROXIMATE_ACTIVE
                }
                _locationFlow.value = buildTelemetryFromLocation(last)
            }
        }

        val priority = if (accessLevel == LocationAccessLevel.PRECISE_GRANTED) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    _providerStatus.value = if (hasFineLocationPermission()) {
                        LocationProviderStatus.FUSED_ACTIVE
                    } else {
                        LocationProviderStatus.APPROXIMATE_ACTIVE
                    }
                    _locationFlow.value = buildTelemetryFromLocation(loc)
                }
            }
        }
        fusedCallback = callback
        runCatching {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    startLegacyLocationManagerFallback()
                }
        }.onFailure {
            startLegacyLocationManagerFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLegacyLocationManagerFallback() {
        if (!hasLocationPermission()) return
        val listener = LocationListener { loc ->
            _providerStatus.value = LocationProviderStatus.LEGACY_GPS_ACTIVE
            _locationFlow.value = buildTelemetryFromLocation(loc)
        }
        legacyListener = listener
        runCatching {
            val provider = when {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true -> LocationManager.GPS_PROVIDER
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true -> LocationManager.NETWORK_PROVIDER
                else -> LocationManager.GPS_PROVIDER
            }
            locationManager?.requestLocationUpdates(provider, 2000L, 3f, listener, Looper.getMainLooper())
        }
    }

    fun stopRealGpsUpdates() {
        fusedCallback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        fusedCallback = null
        legacyListener?.let { runCatching { locationManager?.removeUpdates(it) } }
        legacyListener = null
    }

    private fun buildTelemetryFromLocation(loc: Location): TelemetryLocation {
        val prev = _locationFlow.value
        val pt = GeoPoint(loc.latitude, loc.longitude)
        val bearing = if (loc.hasBearing()) loc.bearing else GeoMath.bearingDegrees(
            GeoPoint(prev.lat, prev.lng),
            pt
        )
        val nearestState = MexicoFreightCatalog.all32States.minByOrNull {
            GeoMath.haversineKm(pt, GeoPoint(it.centerLat, it.centerLng))
        }
        return TelemetryLocation(
            lat = loc.latitude,
            lng = loc.longitude,
            speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6) else 0.0,
            bearingDeg = bearing,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 12f,
            currentRoadName = prev.currentRoadName,
            currentTown = nearestState?.capitalOrHub ?: prev.currentTown,
            currentStateCode = nearestState?.code ?: prev.currentStateCode,
            isSimulated = false,
            timestampMs = System.currentTimeMillis()
        )
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
