package com.example.argon.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Interfaz independiente ARGÓN (ContentProvider).
 * Permite a módulos externos de ARGÓN consultar de forma desacoplada:
 * - Ubicación actual (/location)
 * - Perfil del vehículo (/vehicle)
 * - Ruta seleccionada, distancia y tiempo estimado (/route)
 * - Restricciones encontradas (/restrictions)
 * - Estado de navegación (/status)
 * - Snapshot JSON completo (/snapshot)
 */
class ArgonNavigationContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.aistudio.argontruck.mxgpsx.provider"
        val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

        private const val CODE_LOCATION = 1
        private const val CODE_VEHICLE = 2
        private const val CODE_ROUTE = 3
        private const val CODE_RESTRICTIONS = 4
        private const val CODE_STATUS = 5
        private const val CODE_SNAPSHOT = 6

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "location", CODE_LOCATION)
            addURI(AUTHORITY, "vehicle", CODE_VEHICLE)
            addURI(AUTHORITY, "route", CODE_ROUTE)
            addURI(AUTHORITY, "restrictions", CODE_RESTRICTIONS)
            addURI(AUTHORITY, "status", CODE_STATUS)
            addURI(AUTHORITY, "snapshot", CODE_SNAPSHOT)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val snap = ArgonTelemetryBus.snapshot.value
        return when (uriMatcher.match(uri)) {
            CODE_LOCATION -> MatrixCursor(
                arrayOf("lat", "lng", "speed_kmh", "bearing_deg", "road_name", "state_code", "is_simulated", "updated_at_ms")
            ).apply {
                addRow(
                    arrayOf(
                        snap.lat,
                        snap.lng,
                        snap.speedKmh,
                        snap.bearingDeg,
                        snap.currentRoadName,
                        snap.currentStateCode,
                        if (snap.isSimulatedGps) 1 else 0,
                        snap.updatedAtMs
                    )
                )
            }
            CODE_VEHICLE -> MatrixCursor(
                arrayOf("name", "nom_code", "height_m", "width_m", "length_m", "gross_weight_t", "axles", "axle_load_t", "cargo_type")
            ).apply {
                addRow(
                    arrayOf(
                        snap.vehicleName,
                        snap.vehicleTypeNom,
                        snap.heightMeters,
                        snap.widthMeters,
                        snap.lengthMeters,
                        snap.grossWeightTons,
                        snap.axleCount,
                        snap.axleLoadTons ?: -1.0,
                        snap.cargoType
                    )
                )
            }
            CODE_ROUTE -> MatrixCursor(
                arrayOf("route_id", "route_label", "total_distance_km", "remaining_distance_km", "eta_minutes", "safety_guaranteed", "data_completeness")
            ).apply {
                addRow(
                    arrayOf(
                        snap.selectedRouteId ?: "",
                        snap.selectedRouteLabel ?: "",
                        snap.totalDistanceKm,
                        snap.remainingDistanceKm,
                        snap.etaRemainingMinutes,
                        if (snap.routeSafetyGuaranteed) 1 else 0,
                        snap.dataCompleteness
                    )
                )
            }
            CODE_RESTRICTIONS -> MatrixCursor(
                arrayOf("id", "title", "kind", "road_name", "max_height_m", "max_weight_t", "source_citation")
            ).apply {
                snap.restrictionsFound.forEach { r ->
                    addRow(
                        arrayOf(
                            r.id,
                            r.title,
                            r.kind,
                            r.roadName,
                            r.maxHeightMeters ?: -1.0,
                            r.maxWeightTons ?: -1.0,
                            r.sourceCitation
                        )
                    )
                }
            }
            CODE_STATUS -> MatrixCursor(
                arrayOf("navigation_status", "updated_at_ms", "missing_warnings_count")
            ).apply {
                addRow(arrayOf(snap.navigationStatus, snap.updatedAtMs, snap.missingDataWarnings.size))
            }
            CODE_SNAPSHOT -> MatrixCursor(arrayOf("json_payload")).apply {
                addRow(arrayOf(ArgonTelemetryBus.snapshotJson()))
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
