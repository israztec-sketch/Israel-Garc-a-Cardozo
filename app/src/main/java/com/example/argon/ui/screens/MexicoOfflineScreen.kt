package com.example.argon.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.MexicoStateRegion
import com.example.argon.domain.model.RestrictionCrossing
import com.example.argon.domain.model.RestrictionPointFilter
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckPoiCategory
import com.example.argon.domain.routing.GeoMath
import com.example.argon.ui.ArgonUiState
import com.example.ui.theme.LocalArgonColors
import kotlin.math.roundToInt

@Composable
fun MexicoOfflineScreen(
    uiState: ArgonUiState,
    location: TelemetryLocation,
    regions: List<MexicoStateRegion>,
    pois: List<TruckPoi>,
    restrictions: List<RoadRestriction>,
    recentCrossings: List<RestrictionCrossing>,
    onDownloadState: (String) -> Unit,
    onDeleteState: (String) -> Unit,
    onSetPoiFilter: (TruckPoiCategory?) -> Unit,
    onSetRestrictionFilter: (RestrictionPointFilter) -> Unit,
    onRegisterRestrictionPoint: (
        title: String,
        category: RestrictionPointFilter,
        roadName: String,
        maxHeightMeters: Double?,
        gradePercent: Double?,
        lat: Double,
        lng: Double,
        stateCode: String
    ) -> Unit,
    onDeleteRestrictionPoint: (String) -> Unit,
    onClearCrossingHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var stateFilter by remember { mutableStateOf("") }

    var showAddRestrictionDialog by remember { mutableStateOf(false) }

    val downloadedCount = regions.count { it.isDownloaded }
    val filteredRegions = regions.filter {
        stateFilter.isBlank() ||
            it.name.contains(stateFilter, ignoreCase = true) ||
            it.code.contains(stateFilter, ignoreCase = true) ||
            it.capitalOrHub.contains(stateFilter, ignoreCase = true)
    }

    val filteredPois = pois.filter {
        uiState.selectedPoiFilter == null || it.category == uiState.selectedPoiFilter
    }

    val userPt = GeoPoint(location.lat, location.lng)
    val filteredRestrictions = restrictions.filter { res ->
        when (uiState.selectedRestrictionFilter) {
            RestrictionPointFilter.ALL -> true
            RestrictionPointFilter.LOW_BRIDGES -> res.isLowBridgeOrWeightLimit
            RestrictionPointFilter.STEEP_GRADES -> res.isSteepGrade
            RestrictionPointFilter.PROHIBITED_ZONES -> res.isProhibitedZone
        }
    }.sortedBy { GeoMath.haversineKm(userPt, GeoPoint(it.lat, it.lng)) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("32 Estados ($downloadedCount)") },
                modifier = Modifier.testTag("tab_32_states")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Servicios (${pois.size})") },
                modifier = Modifier.testTag("tab_truck_pois")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Restricciones (${restrictions.size})") },
                modifier = Modifier.testTag("tab_room_restrictions")
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = colors.panelSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Almacenamiento Cartográfico Descargable", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Estados descargados: $downloadedCount de 32 · Caché local en disco: ${uiState.offlineStorageKb} KB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.accentSecondary
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = stateFilter,
                        onValueChange = { stateFilter = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Filtrar estado o corredor (ej. Nuevo León, MEX-57D)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(filteredRegions, key = { it.code }) { st ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = colors.panelElevated, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = st.code,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.accentPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(st.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Text("Nodo: ${st.capitalOrHub}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Corredores: ${st.mainCorridors.joinToString(", ")} · ${st.estimatedSizeMb} MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (uiState.downloadingStateCode == st.code) {
                                CircularProgressIndicator()
                            } else if (st.isDownloaded) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Descargado", tint = colors.safeGreen)
                                    IconButton(
                                        onClick = { onDeleteState(st.code) },
                                        modifier = Modifier.testTag("delete_state_${st.code}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar paquete offline")
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { onDownloadState(st.code) },
                                    modifier = Modifier.testTag("download_state_${st.code}")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Descargar mapa offline ${st.name}", tint = colors.accentPrimary)
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
            }
        } else if (selectedTab == 1) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedPoiFilter == null,
                        onClick = { onSetPoiFilter(null) },
                        label = { Text("Todos (9 tipos)") }
                    )
                    TruckPoiCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = uiState.selectedPoiFilter == cat,
                            onClick = { onSetPoiFilter(cat) },
                            label = { Text(cat.shortCode) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPois, key = { it.id }) { poi ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = colors.safeGreen)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(poi.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Surface(color = colors.panelElevated, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = poi.category.shortCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text("${poi.stateCode} · ${poi.highwayRef}", style = MaterialTheme.typography.labelLarge, color = colors.accentSecondary)
                                Text(poi.details, style = MaterialTheme.typography.bodyMedium)
                                poi.tollEstimateMxn?.let {
                                    Text("Tarifa estimada caseta: $${it.toInt()} MXN", style = MaterialTheme.typography.labelSmall, color = colors.accentPrimary)
                                }
                                Text("Fuente verificable: ${poi.verifiedSource}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(14.dp)) }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RestrictionPointFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.selectedRestrictionFilter == filter,
                            onClick = { onSetRestrictionFilter(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("filter_res_${filter.name}")
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            color = colors.panelSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Base Local Room: Puntos de Restricción Vial",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Índice espacial lat/lng activo · Cruces registrados: ${recentCrossings.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.accentSecondary
                                )
                                Button(
                                    onClick = { showAddRestrictionDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentPrimary,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("add_local_restriction_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Registrar Restricción en Ubicación Actual")
                                }
                            }
                        }
                    }

                    if (recentCrossings.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cruces Detectados con tu Ubicación (${recentCrossings.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.warningAmber
                                )
                                TextButton(
                                    onClick = onClearCrossingHistory,
                                    modifier = Modifier.testTag("clear_crossings_btn")
                                ) {
                                    Text("Limpiar")
                                }
                            }
                        }
                        items(recentCrossings.take(10), key = { "cross_${it.id}" }) { crossing ->
                            Surface(
                                color = colors.panelElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "${crossing.restrictionCategory} · ${crossing.distanceMeters} m",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.warningAmber
                                    )
                                    Text(
                                        text = crossing.restrictionTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = String.format(
                                            java.util.Locale.US,
                                            "%s (%s) · Usuario %.4f, %.4f",
                                            crossing.roadName,
                                            crossing.stateCode,
                                            crossing.userLat,
                                            crossing.userLng
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(filteredRestrictions, key = { it.id }) { res ->
                        val distKm = GeoMath.haversineKm(userPt, GeoPoint(res.lat, res.lng))
                        val distLabel = if (distKm < 1.0) "${(distKm * 1000.0).roundToInt()} m" else "${(distKm * 10.0).roundToInt() / 10.0} km"
                        val badgeLabel = when {
                            res.isLowBridgeOrWeightLimit -> "Puente / Peso"
                            res.isSteepGrade -> "Pendiente ${res.gradePercent}%"
                            res.isProhibitedZone -> "Zona Prohibida"
                            else -> res.kind.label
                        }
                        val badgeColor = if (res.isSteepGrade && !res.isProhibitedZone && !res.isLowBridgeOrWeightLimit) {
                            colors.warningAmber
                        } else {
                            colors.dangerRed
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = badgeColor)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(res.title, style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (res.id.startsWith("LOCAL_")) {
                                        IconButton(
                                            onClick = { onDeleteRestrictionPoint(res.id) },
                                            modifier = Modifier.testTag("delete_restriction_${res.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar restricción local")
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = badgeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${res.stateCode} · a $distLabel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.accentSecondary
                                    )
                                }
                                Text(res.roadName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(res.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "Coords: %.4f, %.4f · %s",
                                        res.lat,
                                        res.lng,
                                        res.sourceCitation
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(14.dp)) }
                }
            }
        }
    }

    if (showAddRestrictionDialog) {
        AddRestrictionPointDialog(
            location = location,
            onDismiss = { showAddRestrictionDialog = false },
            onSave = { title, cat, roadName, maxH, gradePct ->
                onRegisterRestrictionPoint(
                    title,
                    cat,
                    roadName,
                    maxH,
                    gradePct,
                    location.lat,
                    location.lng,
                    location.currentStateCode
                )
                showAddRestrictionDialog = false
            }
        )
    }
}

@Composable
private fun AddRestrictionPointDialog(
    location: TelemetryLocation,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: RestrictionPointFilter,
        roadName: String,
        maxHeightMeters: Double?,
        gradePercent: Double?
    ) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(RestrictionPointFilter.LOW_BRIDGES) }
    var title by remember { mutableStateOf("") }
    var roadName by remember { mutableStateOf(location.currentRoadName) }
    var metricText by remember { mutableStateOf("3.80") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Punto de Restricción (Room)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "Ubicación GPS: %.4f, %.4f (%s)",
                        location.lat,
                        location.lng,
                        location.currentStateCode
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        RestrictionPointFilter.LOW_BRIDGES,
                        RestrictionPointFilter.STEEP_GRADES,
                        RestrictionPointFilter.PROHIBITED_ZONES
                    ).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                metricText = if (cat == RestrictionPointFilter.STEEP_GRADES) "7.5" else "3.80"
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Descripción breve (ej. Puente Ferrocarril)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_restriction_title")
                )
                OutlinedTextField(
                    value = roadName,
                    onValueChange = { roadName = it },
                    label = { Text("Carretera o vialidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedCategory != RestrictionPointFilter.PROHIBITED_ZONES) {
                    OutlinedTextField(
                        value = metricText,
                        onValueChange = { metricText = it },
                        label = {
                            Text(
                                if (selectedCategory == RestrictionPointFilter.LOW_BRIDGES) "Gálibo máximo (m)"
                                else "Pendiente (%)"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val metricVal = metricText.toDoubleOrNull()
                    onSave(
                        title,
                        selectedCategory,
                        roadName,
                        if (selectedCategory == RestrictionPointFilter.LOW_BRIDGES) metricVal else null,
                        if (selectedCategory == RestrictionPointFilter.STEEP_GRADES) metricVal else null
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_add_restriction_btn")
            ) {
                Text("Guardar en Room")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar")
            }
        },
        modifier = Modifier.testTag("add_restriction_dialog")
    )
}
