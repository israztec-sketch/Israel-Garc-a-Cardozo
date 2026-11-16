package com.example.argon.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.argon.data.local.CockpitPreferences
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.NavigationStatus
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckRouteOption
import com.example.argon.location.LocationAccessLevel
import com.example.argon.ui.ArgonUiState
import com.example.argon.ui.RestrictedPoiAlertSeverity
import com.example.ui.theme.LocalArgonColors
import kotlin.math.roundToInt

@Composable
fun CabinaScreen(
    uiState: ArgonUiState,
    profile: TruckProfile,
    location: TelemetryLocation,
    preferences: CockpitPreferences,
    lastVoiceAnnouncement: String,
    restrictions: List<RoadRestriction>,
    pois: List<TruckPoi>,
    onRequestGpsPermission: () -> Unit,
    onConfirmPermissionRationale: () -> Unit,
    onDismissPermissionRationale: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissProximityWarning: () -> Unit,
    onRerouteAroundRestrictedPoi: () -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onSimulateDeviation: () -> Unit,
    onToggleNightMode: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleSimulationMode: () -> Unit,
    onToggleScreenOffMode: (Boolean) -> Unit,
    onOpenRoutes: () -> Unit,
    loadTile: suspend (Int, Int, Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current
    val googleMapsConfigured = remember { hasConfiguredMapsApiKey() }
    var zoomLevel by remember { mutableFloatStateOf(if (!preferences.offlineFirstMode && googleMapsConfigured) 11.5f else 7.0f) }
    var recenterTrigger by remember { mutableStateOf<GeoPoint?>(null) }
    var followTruck by remember { mutableStateOf(true) }
    var useGoogleMaps by remember(preferences.offlineFirstMode, googleMapsConfigured) {
        mutableStateOf(!preferences.offlineFirstMode && googleMapsConfigured)
    }

    if (uiState.screenOffMode) {
        ScreenOffDrivingOverlay(
            uiState = uiState,
            location = location,
            onRestoreScreen = { onToggleScreenOffMode(false) }
        )
        return
    }

    val currentManeuver = uiState.selectedRoute?.maneuvers?.getOrNull(uiState.currentManeuverIndex)

    Box(modifier = modifier.fillMaxSize()) {
        if (useGoogleMaps) {
            ArgonGoogleMapView(
                location = location,
                hasLocationPermission = uiState.hasLocationPermission,
                selectedRoute = uiState.selectedRoute,
                allRoutes = uiState.routeOptions,
                restrictions = restrictions,
                pois = pois,
                zoomLevel = zoomLevel,
                followTruck = followTruck,
                nightMode = preferences.nightMode,
                centerOverride = recenterTrigger,
                loadTile = loadTile,
                onUseOfflineMap = { useGoogleMaps = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Mapa vectorial y teselas cacheadas para operación 100% sin internet
            TruckMapCanvas(
                location = location,
                selectedRoute = uiState.selectedRoute,
                allRoutes = uiState.routeOptions,
                restrictions = restrictions,
                pois = pois,
                zoomLevel = zoomLevel,
                centerOverride = recenterTrigger,
                loadTile = loadTile,
                modifier = Modifier.fillMaxSize()
            )
        }

        // HUD Superior: Maniobra, Escudo Carretero, Población y Alertas
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface.copy(alpha = 0.94f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = colors.accentPrimary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = currentManeuver?.highwayShield ?: "MEX",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = currentManeuver?.let { formatMeters(uiState.distanceToNextManeuverMeters) } ?: "Listo",
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.accentPrimary
                            )
                        }

                        Surface(
                            color = colors.panelElevated,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${profile.truckType.nomCode} · ${profile.heightMeters}m · ${profile.grossWeightTons}t",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentManeuver?.instruction ?: "Selecciona corredor en Rutas para iniciar guiado pesado",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zona: ${location.currentTown} (${location.currentStateCode})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(
                                    java.util.Locale.US,
                                    "%.4f, %.4f · ±%.0fm",
                                    location.lat,
                                    location.lng,
                                    location.accuracyMeters
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentSecondary
                            )
                        }
                        if (preferences.simulationMode) {
                            Surface(
                                color = colors.warningAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable {
                                        if (!uiState.hasLocationPermission) onRequestGpsPermission()
                                        else onToggleSimulationMode()
                                    }
                                    .testTag("gps_mode_badge")
                            ) {
                                Text(
                                    text = "SIMULACIÓN GPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.warningAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            val gpsBadgeColor = when (uiState.locationAccessLevel) {
                                LocationAccessLevel.PRECISE_GRANTED -> colors.safeGreen
                                LocationAccessLevel.APPROXIMATE_ONLY -> colors.warningAmber
                                LocationAccessLevel.DENIED -> colors.dangerRed
                            }
                            Surface(
                                color = gpsBadgeColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable {
                                        when {
                                            uiState.isPermissionPermanentlyDenied -> onOpenAppSettings()
                                            uiState.locationAccessLevel != LocationAccessLevel.PRECISE_GRANTED -> onRequestGpsPermission()
                                            else -> onToggleSimulationMode()
                                        }
                                    }
                                    .testTag("gps_mode_badge")
                            ) {
                                Text(
                                    text = when {
                                        uiState.isPermissionPermanentlyDenied -> "ABRIR AJUSTES GPS"
                                        uiState.locationAccessLevel == LocationAccessLevel.DENIED -> "ACTIVAR GPS PRECISO"
                                        uiState.locationAccessLevel == LocationAccessLevel.APPROXIMATE_ONLY -> "MEJORAR A PRECISO"
                                        else -> uiState.locationProviderStatus.label
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = gpsBadgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.showLocationRationale) {
                AlertDialog(
                    onDismissRequest = onDismissPermissionRationale,
                    title = { Text("Ubicación Precisa (Fine Location)") },
                    text = {
                        Text(
                            "Argón Truck necesita acceso a ubicación precisa en primer plano para calcular la posición exacta del tractocamión en carreteras federales, anticipar puentes de gálibo bajo, básculas SCT y recalcular la ruta ante desvíos."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = onConfirmPermissionRationale,
                            modifier = Modifier.testTag("confirm_location_rationale_btn")
                        ) {
                            Text("Conceder Ubicación Precisa")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissPermissionRationale) {
                            Text("Ahora no")
                        }
                    },
                    modifier = Modifier.testTag("location_rationale_dialog")
                )
            }

            // Banner obligatorio de veracidad / datos faltantes / alerta vial
            val route = uiState.selectedRoute
            val showSafetyBanner = uiState.activeAlertBanner != null || (route != null && !route.safetyAudit.safetyGuaranteed)
            if (showSafetyBanner) {
                val bannerText = uiState.activeAlertBanner
                    ?: route?.safetyAudit?.missingDataWarnings?.firstOrNull()
                    ?: "Precaución: Datos cartográficos parciales; verificar señalización en sitio."
                val isCritical = route != null && !route.safetyAudit.isCompatible
                Surface(
                    color = if (isCritical) colors.dangerRed.copy(alpha = 0.94f) else colors.warningAmber.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta de restricción",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Botonera lateral derecha: Zoom, Modo Nocturno, Voz, Pantalla Apagada, GPS
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { zoomLevel = (zoomLevel + 1.0f).coerceAtMost(17.5f) },
                modifier = Modifier.testTag("zoom_in_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Acercar mapa")
            }
            FilledTonalIconButton(
                onClick = { zoomLevel = (zoomLevel - 1.0f).coerceAtLeast(4.8f) },
                modifier = Modifier.testTag("zoom_out_btn")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Alejar mapa")
            }
            FilledTonalIconButton(
                onClick = {
                    followTruck = true
                    recenterTrigger = GeoPoint(location.lat, location.lng)
                    if (!uiState.hasLocationPermission) onRequestGpsPermission()
                },
                modifier = Modifier.testTag("recenter_gps_btn")
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = "Centrar en camión")
            }
            FilledTonalIconButton(
                onClick = { useGoogleMaps = !useGoogleMaps },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (useGoogleMaps) colors.accentSecondary else colors.panelElevated
                ),
                modifier = Modifier.testTag("toggle_map_engine_btn")
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = if (useGoogleMaps) "Cambiar a Mapa Offline ARGÓN" else "Cambiar a Google Maps SDK",
                    tint = if (useGoogleMaps) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
            FilledTonalIconButton(
                onClick = onToggleNightMode,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (preferences.nightMode) colors.accentPrimary else colors.panelElevated
                ),
                modifier = Modifier.testTag("night_mode_btn")
            ) {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = "Modo nocturno",
                    tint = if (preferences.nightMode) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
            FilledTonalIconButton(
                onClick = onToggleVoice,
                modifier = Modifier.testTag("voice_toggle_btn")
            ) {
                Icon(
                    if (preferences.voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Alternar guía por voz"
                )
            }
            FilledTonalIconButton(
                onClick = { onToggleScreenOffMode(true) },
                modifier = Modifier.testTag("screen_off_btn")
            ) {
                Icon(Icons.Default.ScreenLockPortrait, contentDescription = "Modo conducción pantalla apagada")
            }
        }

        // Panel inferior de Telemetría, ETA, Distancia Restante y Controles de Conducción
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.panelSurface.copy(alpha = 0.96f)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelemetryStat("VELOCIDAD", "${location.speedKmh.roundToInt()} km/h", colors.accentSecondary)
                    TelemetryStat("RESTANTE", "${uiState.remainingDistanceKm} km", MaterialTheme.colorScheme.onSurface)
                    TelemetryStat("ETA", "${uiState.etaRemainingMinutes} min", colors.accentPrimary)
                    TelemetryStat("ESTADO", uiState.navigationStatus.label.take(14), colors.safeGreen)
                }

                Text(
                    text = "Voz: $lastVoiceAnnouncement",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.navigationStatus == NavigationStatus.NAVIGATING) {
                        Button(
                            onClick = onStopNavigation,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.dangerRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_nav_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detener")
                        }
                        OutlinedButton(
                            onClick = onSimulateDeviation,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("simulate_reroute_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simular Desvío")
                        }
                    } else {
                        Button(
                            onClick = onStartNavigation,
                            enabled = uiState.selectedRoute?.safetyAudit?.isCompatible == true,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_nav_button")
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Iniciar Guía")
                        }
                        OutlinedButton(
                            onClick = onOpenRoutes,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_routes_button")
                        ) {
                            Icon(Icons.Default.Route, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Corredor ${uiState.originCode}→${uiState.destinationCode}")
                        }
                    }
                }
            }
        }

        uiState.activeProximityWarning?.let { warning ->
            val isCritical = warning.severity == RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK
            val accentColor = if (isCritical) colors.dangerRed else colors.warningAmber
            AlertDialog(
                onDismissRequest = onDismissProximityWarning,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = warning.categoryLabel,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            color = accentColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = warning.severity.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = warning.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${warning.categoryLabel} · ${warning.roadName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Distancia aproximada: ${warning.distanceMeters} m",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentSecondary
                        )
                        Text(
                            text = warning.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Fuente: ${warning.sourceCitation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismissProximityWarning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = if (isCritical) Color.White else Color.Black
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dismiss_restricted_warning_btn")
                    ) {
                        Text("Entendido")
                    }
                },
                dismissButton = if (isCritical) {
                    {
                        OutlinedButton(
                            onClick = onRerouteAroundRestrictedPoi,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("reroute_from_warning_btn")
                        ) {
                            Text("Recalcular Ruta Segura")
                        }
                    }
                } else null,
                modifier = Modifier.testTag("restricted_poi_warning_dialog")
            )
        }
    }
}

@Composable
private fun ScreenOffDrivingOverlay(
    uiState: ArgonUiState,
    location: TelemetryLocation,
    onRestoreScreen: () -> Unit
) {
    val currentManeuver = uiState.selectedRoute?.maneuvers?.getOrNull(uiState.currentManeuverIndex)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MODO CONDUCCIÓN · PANTALLA ATENUADA",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B7280)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Siguiente maniobra",
                tint = Color(0xFFFF9F1C),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = formatMeters(uiState.distanceToNextManeuverMeters),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9F1C)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentManeuver?.roadName ?: location.currentRoadName,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFE5E7EB)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${location.speedKmh.roundToInt()} km/h  ·  Restan ${uiState.remainingDistanceKm} km (${uiState.etaRemainingMinutes} min)",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF9CA3AF)
            )
            uiState.activeAlertBanner?.let { alert ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color(0xFF451A03), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = alert,
                        color = Color(0xFFFBBF24),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onRestoreScreen,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("restore_screen_btn")
        ) {
            Icon(Icons.Default.DarkMode, contentDescription = null, tint = Color(0xFFFF9F1C))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restaurar Cabina Completa", color = Color(0xFFFF9F1C))
        }
    }
}

@Composable
private fun TelemetryStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

private fun formatMeters(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format("%.1f km", meters / 1000.0)
    } else {
        "${meters.roundToInt()} m"
    }
}
