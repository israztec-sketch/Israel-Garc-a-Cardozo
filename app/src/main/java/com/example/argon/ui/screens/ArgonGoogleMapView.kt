package com.example.argon.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckRouteOption
import com.example.ui.theme.LocalArgonColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

import kotlinx.coroutines.delay

private const val COCKPIT_DARK_MAP_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#111622"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#94a3b8"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0b0e14"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#2b3a55"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#ff9f1c"},{"weight":0.8}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#1e293b"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#07101f"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#0f1f1c"}]}
]
"""

enum class MapsApiKeyStatus {
    VALID_FORMAT,
    MISSING_OR_PLACEHOLDER,
    INVALID_FORMAT
}

fun getMapsApiKeyStatus(rawKey: String = BuildConfig.MAPS_API_KEY): MapsApiKeyStatus {
    val key = rawKey.trim()
    if (key.isEmpty() || key == "MY_MAPS_API_KEY") {
        return MapsApiKeyStatus.MISSING_OR_PLACEHOLDER
    }
    val looksLikeGoogleApiKey = key.startsWith("AIza") && key.length >= 30 && key.none { it.isWhitespace() }
    return if (looksLikeGoogleApiKey) MapsApiKeyStatus.VALID_FORMAT else MapsApiKeyStatus.INVALID_FORMAT
}

fun hasConfiguredMapsApiKey(rawKey: String = BuildConfig.MAPS_API_KEY): Boolean {
    return getMapsApiKeyStatus(rawKey) == MapsApiKeyStatus.VALID_FORMAT
}

@Composable
fun ArgonGoogleMapView(
    location: TelemetryLocation,
    hasLocationPermission: Boolean,
    selectedRoute: TruckRouteOption?,
    allRoutes: List<TruckRouteOption>,
    restrictions: List<RoadRestriction>,
    pois: List<TruckPoi>,
    zoomLevel: Float,
    followTruck: Boolean,
    nightMode: Boolean,
    centerOverride: GeoPoint? = null,
    loadTile: suspend (Int, Int, Int) -> Bitmap? = { _, _, _ -> null },
    onUseOfflineMap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current
    val keyStatus = remember { getMapsApiKeyStatus() }
    val truckLatLng = LatLng(location.lat, location.lng)
    var mapReady by remember { mutableStateOf(false) }
    var authTimeoutFallback by remember { mutableStateOf(false) }
    var noticeDismissed by remember { mutableStateOf(false) }

    if (keyStatus != MapsApiKeyStatus.VALID_FORMAT || authTimeoutFallback) {
        Box(modifier = modifier.fillMaxSize()) {
            TruckMapCanvas(
                location = location,
                selectedRoute = selectedRoute,
                allRoutes = allRoutes,
                restrictions = restrictions,
                pois = pois,
                zoomLevel = zoomLevel,
                centerOverride = centerOverride,
                loadTile = loadTile,
                modifier = Modifier.fillMaxSize()
            )

            if (!noticeDismissed) {
                val message = when {
                    authTimeoutFallback ->
                        "Google Maps SDK no autorizó la clave o falta habilitar Maps SDK for Android para com.aistudio.argontruck.mxgpsx. Mostrando mapa vectorial ARGÓN."
                    keyStatus == MapsApiKeyStatus.INVALID_FORMAT ->
                        "MAPS_API_KEY en el panel Secrets de AI Studio no tiene formato válido de Google Maps (debe iniciar con AIza...). Mostrando mapa ARGÓN."
                    else ->
                        "Configura MAPS_API_KEY en el panel Secrets de AI Studio para activar Google Maps SDK. Mostrando mapa ARGÓN."
                }
                Surface(
                    color = colors.panelSurface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("maps_api_fallback_notice")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Mapa Offline ARGÓN Activo",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.warningAmber
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                noticeDismissed = true
                                onUseOfflineMap()
                            }) {
                                Text("Continuar en Mapa ARGÓN")
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(keyStatus) {
        runCatching {
            MapsInitializer.initialize(appContext, MapsInitializer.Renderer.LATEST) { }
        }
    }

    LaunchedEffect(mapReady) {
        if (!mapReady) {
            delay(6500L)
            if (!mapReady) {
                authTimeoutFallback = true
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.Builder()
            .target(truckLatLng)
            .zoom(zoomLevel.coerceIn(4f, 18f))
            .bearing(location.bearingDeg)
            .tilt(35f)
            .build()
    }

    LaunchedEffect(location.lat, location.lng, zoomLevel, followTruck, centerOverride, mapReady) {
        if ((followTruck || centerOverride != null) && mapReady) {
            runCatching {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(truckLatLng)
                            .zoom(zoomLevel.coerceIn(4f, 18f))
                            .bearing(location.bearingDeg)
                            .tilt(35f)
                            .build()
                    ),
                    durationMs = 650
                )
            }
        }
    }

    val mapStyle = remember(nightMode) {
        runCatching { MapStyleOptions(COCKPIT_DARK_MAP_STYLE) }.getOrNull()
    }

    val mapProperties = remember(hasLocationPermission, mapStyle) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            isTrafficEnabled = true,
            mapType = MapType.NORMAL,
            mapStyleOptions = mapStyle
        )
    }

    val uiSettings = remember {
        MapUiSettings(
            compassEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = true,
            tiltGesturesEnabled = true
        )
    }

    val truckMarkerState = remember { MarkerState(position = truckLatLng) }
    LaunchedEffect(truckLatLng) {
        truckMarkerState.position = truckLatLng
    }

    val truckBitmapDescriptor: BitmapDescriptor? = remember(mapReady) {
        if (!mapReady) null else runCatching { createTruckArrowDescriptor() }.getOrNull()
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_compose"),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapLoaded = { mapReady = true }
        ) {
            // 1. Círculo de precisión GPS en tiempo real (LocationServices)
            Circle(
                center = truckLatLng,
                radius = location.accuracyMeters.toDouble().coerceAtLeast(25.0),
                fillColor = colors.accentSecondary.copy(alpha = 0.14f),
                strokeColor = colors.accentSecondary.copy(alpha = 0.7f),
                strokeWidth = 2f
            )

            // 2. Superposición del tractocamión con coordenadas y rumbo en tiempo real
            Marker(
                state = truckMarkerState,
                title = "Tractocamión ARGÓN",
                snippet = "${location.currentRoadName} (${if (location.isSimulated) "Simulación" else "LocationServices"})",
                icon = truckBitmapDescriptor,
                rotation = location.bearingDeg,
                flat = true,
                anchor = Offset(0.5f, 0.5f)
            )

            // 3. Rutas alternativas o bloqueadas
            allRoutes.filter { it.id != selectedRoute?.id }.forEach { alt ->
                if (alt.waypoints.size >= 2) {
                    Polyline(
                        points = alt.waypoints.map { LatLng(it.lat, it.lng) },
                        color = if (alt.safetyAudit.isCompatible) Color(0xFF94A3B8) else colors.dangerRed,
                        width = 8f,
                        pattern = listOf(Dash(22f), Gap(14f))
                    )
                }
            }

            // 4. Ruta principal compatible para tractocamión
            selectedRoute?.let { route ->
                if (route.waypoints.size >= 2) {
                    val lineColor = when {
                        !route.safetyAudit.isCompatible -> colors.dangerRed
                        !route.safetyAudit.safetyGuaranteed -> colors.warningAmber
                        else -> colors.accentSecondary
                    }
                    Polyline(
                        points = route.waypoints.map { LatLng(it.lat, it.lng) },
                        color = lineColor,
                        width = 14f
                    )
                }
            }

            // 5. Restricciones cartográficas verificadas
            restrictions.forEach { res ->
                Marker(
                    state = MarkerState(position = LatLng(res.lat, res.lng)),
                    title = res.title,
                    snippet = "${res.kind.label} · ${res.sourceCitation}",
                    icon = if (mapReady) {
                        runCatching {
                            val hue = if (res.kind == RestrictionKind.LEGAL_PROHIBITION) {
                                BitmapDescriptorFactory.HUE_RED
                            } else {
                                BitmapDescriptorFactory.HUE_ORANGE
                            }
                            BitmapDescriptorFactory.defaultMarker(hue)
                        }.getOrNull()
                    } else null
                )
            }

            // 6. Puntos de interés pesados (Diésel, Paradores, Básculas, Casetas)
            pois.forEach { poi ->
                Marker(
                    state = MarkerState(position = LatLng(poi.lat, poi.lng)),
                    title = poi.name,
                    snippet = "${poi.category.displayName} · ${poi.highwayRef}",
                    icon = if (mapReady) {
                        runCatching {
                            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        }.getOrNull()
                    } else null
                )
            }
        }
    }
}

private fun createTruckArrowDescriptor(): BitmapDescriptor {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FF9F1C")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val path = Path().apply {
        moveTo(size / 2f, 8f)
        lineTo(size - 12f, size - 12f)
        lineTo(size / 2f, size - 22f)
        lineTo(12f, size - 12f)
        close()
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
