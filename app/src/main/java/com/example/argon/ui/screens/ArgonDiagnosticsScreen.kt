package com.example.argon.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.argon.api.ArgonNavigationContentProvider
import com.example.argon.ui.ArgonUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalArgonColors

@Composable
fun ArgonDiagnosticsScreen(
    uiState: ArgonUiState,
    onRefreshSnapshot: () -> Unit,
    onRunAcceptanceTests: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current
    val context = LocalContext.current
    var providerQueryResult by remember { mutableStateOf("Pulsa 'Consultar ContentProvider ARGÓN' para leer en vivo") }

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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Api, contentDescription = null, tint = colors.accentSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Interfaz Independiente ARGÓN", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Authority: ${ArgonNavigationContentProvider.AUTHORITY}\nURIs: /location, /vehicle, /route, /restrictions, /status, /snapshot",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onRefreshSnapshot()
                                providerQueryResult = queryArgonProvider(context)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("query_argon_provider_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Consultar API ARGÓN")
                        }
                        Button(
                            onClick = onRunAcceptanceTests,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary, contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("run_acceptance_tests_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pruebas Reales")
                        }
                    }
                }
            }
        }

        if (uiState.acceptanceResults.isNotEmpty()) {
            item {
                val passed = uiState.acceptanceResults.count { it.passed }
                Text(
                    text = "Informe de Pruebas de Aceptación ($passed/${uiState.acceptanceResults.size} aprobadas)",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (passed == uiState.acceptanceResults.size) colors.safeGreen else colors.warningAmber
                )
            }
            items(uiState.acceptanceResults, key = { it.id }) { check ->
                Surface(
                    color = colors.panelSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (check.passed) colors.safeGreen else colors.dangerRed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(check.title, style = MaterialTheme.typography.titleMedium)
                            Text(check.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Lectura Directa ContentResolver", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = providerQueryResult,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.safeGreen
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.panelSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Snapshot JSON (Protocolo ARGON-TRUCK-1.0)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.argonJsonPreview,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun queryArgonProvider(context: android.content.Context): String {
    return runCatching {
        val sb = StringBuilder()
        listOf("location", "vehicle", "route", "status").forEach { path ->
            val uri = Uri.withAppendedPath(ArgonNavigationContentProvider.BASE_URI, path)
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val cols = c.columnNames.mapIndexed { idx, name -> "$name=${c.getString(idx)}" }
                    sb.appendLine("/$path -> ${cols.joinToString(", ")}")
                }
            }
        }
        sb.toString().ifBlank { "Sin datos devueltos." }
    }.getOrElse { "Error consultando ContentProvider: ${it.message}" }
}
