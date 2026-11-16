package com.example.argon.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.argon.domain.model.CargoType
import com.example.argon.domain.model.SpecialRestriction
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckType
import com.example.ui.theme.LocalArgonColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleProfileScreen(
    profiles: List<TruckProfile>,
    activeProfile: TruckProfile,
    onSelectProfile: (Long) -> Unit,
    onSaveProfile: (TruckProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current

    var name by remember(activeProfile.id) { mutableStateOf(activeProfile.name) }
    var heightText by remember(activeProfile.id) { mutableStateOf(activeProfile.heightMeters.toString()) }
    var widthText by remember(activeProfile.id) { mutableStateOf(activeProfile.widthMeters.toString()) }
    var lengthText by remember(activeProfile.id) { mutableStateOf(activeProfile.lengthMeters.toString()) }
    var grossWeightText by remember(activeProfile.id) { mutableStateOf(activeProfile.grossWeightTons.toString()) }
    var axleCountText by remember(activeProfile.id) { mutableStateOf(activeProfile.axleCount.toString()) }
    var hasAxleLoad by remember(activeProfile.id) { mutableStateOf(activeProfile.axleLoadTons != null) }
    var axleLoadText by remember(activeProfile.id) { mutableStateOf((activeProfile.axleLoadTons ?: 10.0).toString()) }
    var truckType by remember(activeProfile.id) { mutableStateOf(activeProfile.truckType) }
    var cargoType by remember(activeProfile.id) { mutableStateOf(activeProfile.cargoType) }
    var specialRestrictions by remember(activeProfile.id) { mutableStateOf(activeProfile.specialRestrictions) }
    var savedToast by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_argon_cab_banner),
                    contentDescription = "Unidad pesada ARGÓN",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "IDENTIDAD DEL TRACTOCAMIÓN",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary
                    )
                    Text(
                        text = "${activeProfile.truckType.nomCode} · ${activeProfile.heightMeters}m alto · ${activeProfile.grossWeightTons}t bruto",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            }
        }

        item {
            Text(
                text = "Perfiles de Flota Guardados",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { p ->
                    FilterChip(
                        selected = p.id == activeProfile.id,
                        onClick = { onSelectProfile(p.id) },
                        label = { Text("${p.truckType.nomCode}: ${p.name.take(22)}") },
                        leadingIcon = {
                            Icon(Icons.Default.LocalShipping, contentDescription = null)
                        }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Dimensiones y Pesos (NOM-012-SCT-2-2017)", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; savedToast = false },
                        label = { Text("Nombre de configuración") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vehicle_name_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it; savedToast = false },
                            label = { Text("Altura total (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vehicle_height_input")
                        )
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it; savedToast = false },
                            label = { Text("Ancho total (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vehicle_width_input")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lengthText,
                            onValueChange = { lengthText = it; savedToast = false },
                            label = { Text("Longitud total (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vehicle_length_input")
                        )
                        OutlinedTextField(
                            value = grossWeightText,
                            onValueChange = { grossWeightText = it; savedToast = false },
                            label = { Text("Peso bruto (t)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vehicle_weight_input")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = axleCountText,
                            onValueChange = { axleCountText = it; savedToast = false },
                            label = { Text("Núm. de ejes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vehicle_axles_input")
                        )
                        OutlinedTextField(
                            value = axleLoadText,
                            onValueChange = { axleLoadText = it; savedToast = false },
                            enabled = hasAxleLoad,
                            label = { Text("Peso/eje (t)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vehicle_axle_load_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dato de peso por eje disponible", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = hasAxleLoad,
                            onCheckedChange = { hasAxleLoad = it; savedToast = false }
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tipo de Camión", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TruckType.entries.forEach { t ->
                            FilterChip(
                                selected = truckType == t,
                                onClick = { truckType = t; savedToast = false },
                                label = { Text("${t.nomCode} · ${t.displayName}") }
                            )
                        }
                    }

                    Text("Tipo de Carga", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CargoType.entries.forEach { c ->
                            FilterChip(
                                selected = cargoType == c,
                                onClick = { cargoType = c; savedToast = false },
                                label = { Text(c.displayName) }
                            )
                        }
                    }

                    Text("Restricciones Especiales de Circulación", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SpecialRestriction.entries.forEach { r ->
                            val selected = r in specialRestrictions
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    specialRestrictions = if (selected) specialRestrictions - r else specialRestrictions + r
                                    savedToast = false
                                },
                                label = { Text(r.displayName) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val updated = activeProfile.copy(
                        name = name.ifBlank { activeProfile.name },
                        heightMeters = heightText.toDoubleOrNull()?.coerceIn(2.0, 6.0) ?: activeProfile.heightMeters,
                        widthMeters = widthText.toDoubleOrNull()?.coerceIn(1.8, 4.5) ?: activeProfile.widthMeters,
                        lengthMeters = lengthText.toDoubleOrNull()?.coerceIn(5.0, 42.0) ?: activeProfile.lengthMeters,
                        grossWeightTons = grossWeightText.toDoubleOrNull()?.coerceIn(3.5, 120.0) ?: activeProfile.grossWeightTons,
                        axleCount = axleCountText.toIntOrNull()?.coerceIn(2, 14) ?: activeProfile.axleCount,
                        axleLoadTons = if (hasAxleLoad) axleLoadText.toDoubleOrNull()?.coerceIn(2.0, 20.0) else null,
                        truckType = truckType,
                        cargoType = cargoType,
                        specialRestrictions = specialRestrictions,
                        isActive = true
                    )
                    onSaveProfile(updated)
                    savedToast = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_vehicle_profile_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Perfil y Recalcular Cartografía")
            }

            if (savedToast) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.safeGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Perfil guardado y aplicado al motor de rutas.", color = colors.safeGreen)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
