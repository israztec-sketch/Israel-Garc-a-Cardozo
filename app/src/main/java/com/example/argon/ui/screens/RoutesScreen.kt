package com.example.argon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.data.local.CockpitPreferences
import com.example.argon.domain.model.DataCompleteness
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckRouteOption
import com.example.argon.ui.ArgonUiState
import com.example.ui.theme.LocalArgonColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutesScreen(
    uiState: ArgonUiState,
    profile: TruckProfile,
    preferences: CockpitPreferences,
    onSetCorridor: (String, String) -> Unit,
    onSetDestinationFromGps: (String) -> Unit,
    onUseCurrentGpsAsOrigin: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectRoute: (TruckRouteOption) -> Unit,
    onStartNavigationAndGoToCab: () -> Unit,
    onToggleOfflineFirst: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current
    val allStates = MexicoFreightCatalog.all32States
    var originMenuOpen by remember { mutableStateOf(false) }
    var destMenuOpen by remember { mutableStateOf(false) }

    val originState = allStates.firstOrNull { it.code == uiState.originCode }
    val destState = allStates.firstOrNull { it.code == uiState.destinationCode }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                shape = RoundedCornerShape(16.dp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Planificador Pesado (${profile.truckType.nomCode})", style = MaterialTheme.typography.titleMedium)
                            val googleStatusText = when {
                                preferences.offlineFirstMode -> "Google Routes API en pausa (Modo Offline)"
                                hasConfiguredMapsApiKey() -> "Google Maps SDK + Routes API (TRAFFIC_AWARE) activos"
                                else -> "Google Routes API requiere MAPS_API_KEY válida en Secrets (Valhalla / Red Local activos)"
                            }
                            Text(
                                text = googleStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!preferences.offlineFirstMode && hasConfiguredMapsApiKey()) colors.safeGreen else colors.accentSecondary
                            )
                        }
                        FilterChip(
                            selected = preferences.offlineFirstMode,
                            onClick = onToggleOfflineFirst,
                            label = { Text(if (preferences.offlineFirstMode) "Solo Offline" else "Híbrido Online") },
                            leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { originMenuOpen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("origin_state_picker")
                            ) {
                                val originText = if (uiState.useCurrentGpsAsOrigin) {
                                    "Origen: Mi GPS Real"
                                } else {
                                    "Origen: ${originState?.code ?: uiState.originCode}"
                                }
                                Text(originText)
                            }
                            DropdownMenu(
                                expanded = originMenuOpen,
                                onDismissRequest = { originMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Mi Ubicación GPS Actual (Tiempo Real)",
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accentPrimary
                                        )
                                    },
                                    onClick = {
                                        originMenuOpen = false
                                        onUseCurrentGpsAsOrigin(true)
                                    }
                                )
                                HorizontalDivider()
                                allStates.forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text("${st.code} · ${st.name}") },
                                        onClick = {
                                            originMenuOpen = false
                                            onSetCorridor(st.code, uiState.destinationCode)
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                onSetCorridor(uiState.destinationCode, uiState.originCode)
                            }
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Invertir corredor")
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { destMenuOpen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("destination_state_picker")
                            ) {
                                Text("Destino: ${destState?.code ?: uiState.destinationCode}")
                            }
                            DropdownMenu(
                                expanded = destMenuOpen,
                                onDismissRequest = { destMenuOpen = false }
                            ) {
                                allStates.forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text("${st.code} · ${st.name}") },
                                        onClick = {
                                            destMenuOpen = false
                                            if (uiState.useCurrentGpsAsOrigin) {
                                                onSetDestinationFromGps(st.code)
                                            } else {
                                                onSetCorridor(uiState.originCode, st.code)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Buscar ciudad, parque industrial o estado destino") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("place_search_input")
                    )

                    if (uiState.searchQuery.isNotBlank() && uiState.searchResults.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            uiState.searchResults.take(4).forEach { place ->
                                Surface(
                                    color = colors.panelElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (uiState.useCurrentGpsAsOrigin) {
                                                onSetDestinationFromGps(place.stateCode)
                                            } else {
                                                onSetCorridor(uiState.originCode, place.stateCode)
                                            }
                                            onSearchQueryChange("")
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("${place.name} [${place.stateCode}]", style = MaterialTheme.typography.titleMedium)
                                        Text("${place.subtitle} · ${place.source}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // Corredores rápidos desde GPS actual o corredor troncal
                    Text("Destinos y Corredores Rápidos:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { onUseCurrentGpsAsOrigin(true) },
                            label = { Text("Desde Mi GPS Actual") }
                        )
                        AssistChip(onClick = { onSetCorridor("CMX", "QUE") }, label = { Text("CDMX → QRO") })
                        AssistChip(onClick = { onSetCorridor("CMX", "NLE") }, label = { Text("CDMX → MTY") })
                        AssistChip(onClick = { onSetCorridor("CMX", "VER") }, label = { Text("CDMX → VER (Maltrata)") })
                        AssistChip(onClick = { onSetCorridor("DUR", "SIN") }, label = { Text("DUR → SIN (Baluarte)") })
                        AssistChip(onClick = { onSetCorridor("JAL", "TAM") }, label = { Text("GDL → Nuevo Laredo") })
                    }

                    if (uiState.isCalculating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        items(uiState.routeOptions, key = { it.id }) { route ->
            val isSelected = uiState.selectedRoute?.id == route.id
            val audit = route.safetyAudit
            val cardBorder = when {
                !audit.isCompatible -> colors.dangerRed
                isSelected -> colors.accentSecondary
                else -> colors.borderSubtle
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRoute(route) }
                    .testTag("route_card_${route.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (audit.isCompatible) Icons.Default.VerifiedUser else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (audit.isCompatible) colors.safeGreen else colors.dangerRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(route.label, style = MaterialTheme.typography.titleMedium)
                                Text(route.routingSource.label, style = MaterialTheme.typography.labelSmall, color = colors.accentSecondary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${route.distanceKm} km", style = MaterialTheme.typography.titleLarge, color = colors.accentPrimary)
                            Text("${route.estimatedMinutes} min · $${route.tollCostMxn.toInt()} MXN", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text(route.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Estado de Veracidad y Completitud de Datos
                    Surface(
                        color = when {
                            !audit.isCompatible -> colors.dangerRed.copy(alpha = 0.16f)
                            audit.safetyGuaranteed -> colors.safeGreen.copy(alpha = 0.16f)
                            else -> colors.warningAmber.copy(alpha = 0.18f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    !audit.isCompatible -> Icons.Default.Block
                                    audit.safetyGuaranteed -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = null,
                                tint = when {
                                    !audit.isCompatible -> colors.dangerRed
                                    audit.safetyGuaranteed -> colors.safeGreen
                                    else -> colors.warningAmber
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !audit.isCompatible -> "BLOQUEADA: ${audit.rejectionReasons.joinToString(" · ")}"
                                    audit.dataCompleteness == DataCompleteness.FULLY_VERIFIED ->
                                        "Cartografía verificada (gálibo, peso, longitud y tipo de vía compatibles)"
                                    else ->
                                        "NO SE GARANTIZA SEGURIDAD TOTAL: ${audit.missingDataWarnings.firstOrNull() ?: "Faltan datos de gálibo/peso"}"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }

                    // Distinción obligatoria: Restricción Legal vs Recomendación de Conducción
                    if (audit.legalRestrictionsFound.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("Restricciones Legales en Corredor:", style = MaterialTheme.typography.labelLarge, color = colors.dangerRed)
                        audit.legalRestrictionsFound.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Gavel, contentDescription = null, tint = colors.dangerRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${r.title} [${r.sourceCitation}]", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (audit.drivingAdvisories.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderSubtle)
                        Text("Recomendaciones de Conducción:", style = MaterialTheme.typography.labelLarge, color = colors.warningAmber)
                        audit.drivingAdvisories.forEach { adv ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = colors.warningAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${adv.title} — ${adv.description}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (isSelected && audit.isCompatible) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onStartNavigationAndGoToCab,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("activate_selected_route_btn")
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navegar Esta Ruta en Cabina")
                        }
                    }
                }
            }
        }

        // Maniobras paso a paso de la ruta seleccionada
        uiState.selectedRoute?.let { route ->
            item {
                Text(
                    text = "Hoja de Ruta y Maniobras (${route.maneuvers.size} pasos · Estados: ${route.passedStates.joinToString(" → ")})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(route.maneuvers, key = { it.id }) { m ->
                Surface(
                    color = colors.panelSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("[${m.highwayShield}] ${m.instruction}", style = MaterialTheme.typography.bodyLarge)
                            Text("Zona: ${m.townName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            m.curveAdvisory?.let {
                                Text("Aviso: $it", style = MaterialTheme.typography.labelSmall, color = colors.warningAmber)
                            }
                        }
                        Text("${(m.distanceMeters / 1000.0).toInt()} km", style = MaterialTheme.typography.labelLarge, color = colors.accentPrimary)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
