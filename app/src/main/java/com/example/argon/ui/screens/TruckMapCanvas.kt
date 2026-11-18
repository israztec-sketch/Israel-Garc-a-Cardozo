package com.example.argon.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.data.remote.OfflineMapStorage
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadClass
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckRouteOption
import com.example.ui.theme.LocalArgonColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

@Composable
fun TruckMapCanvas(
    location: TelemetryLocation,
    selectedRoute: TruckRouteOption?,
    allRoutes: List<TruckRouteOption>,
    restrictions: List<RoadRestriction>,
    pois: List<TruckPoi>,
    zoomLevel: Float,
    centerOverride: GeoPoint?,
    loadTile: suspend (Int, Int, Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val colors = LocalArgonColors.current
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var internalZoom by remember(zoomLevel) { mutableFloatStateOf(zoomLevel) }
    val tileCache = remember { mutableStateMapOf<String, Bitmap>() }

    LaunchedEffect(centerOverride) {
        if (centerOverride != null) {
            panOffset = Offset.Zero
        }
    }

    val focusPoint = GeoPoint(location.lat, location.lng)
    val tileZ = internalZoom.roundToInt().coerceIn(5, 11)

    LaunchedEffect(focusPoint.lat, focusPoint.lng, tileZ) {
        val (cx, cy) = OfflineMapStorage.latLngToTile(focusPoint.lat, focusPoint.lng, tileZ)
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = cx + dx
                val ty = cy + dy
                val key = "${tileZ}_${tx}_${ty}"
                if (!tileCache.containsKey(key)) {
                    val bmp = loadTile(tileZ, tx, ty)
                    if (bmp != null) {
                        tileCache[key] = bmp
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.cockpitBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    panOffset += pan
                    internalZoom = (internalZoom + (zoom - 1f) * 2.2f).coerceIn(4.8f, 13.0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = 256f * 2f.pow(internalZoom)
            val centerX = lngToWorldX(focusPoint.lng, scale)
            val centerY = latToWorldY(focusPoint.lat, scale)

            fun project(pt: GeoPoint): Offset {
                val wx = lngToWorldX(pt.lng, scale)
                val wy = latToWorldY(pt.lat, scale)
                return Offset(
                    x = size.width / 2f + (wx - centerX) + panOffset.x,
                    y = size.height / 2f + (wy - centerY) + panOffset.y
                )
            }

            // 1. Teselas OSM cacheadas (si disponibles offline/online) con tinte oscuro para cabina
            val (ctx, cty) = OfflineMapStorage.latLngToTile(focusPoint.lat, focusPoint.lng, tileZ)
            val tileWorldSize = scale / (1 shl tileZ).toFloat()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val tx = ctx + dx
                    val ty = cty + dy
                    val bmp = tileCache["${tileZ}_${tx}_${ty}"]
                    if (bmp != null) {
                        val left = size.width / 2f + (tx * tileWorldSize - centerX) + panOffset.x
                        val top = size.height / 2f + (ty * tileWorldSize - centerY) + panOffset.y
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(tileWorldSize.roundToInt() + 1, tileWorldSize.roundToInt() + 1),
                            alpha = if (colors.isNightMode) 0.22f else 0.35f
                        )
                    }
                }
            }

            // 2. Cuadrícula cartográfica sutil
            val gridColor = colors.borderSubtle.copy(alpha = 0.28f)
            val step = 90f
            var gx = 0f
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += step
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += step
            }

            // 3. Red troncal de carreteras pesadas de México
            MexicoFreightCatalog.freightEdges.forEach { edge ->
                if (edge.id.endsWith("_REV")) return@forEach
                val pts = edge.geometry.map { project(it) }
                if (pts.size >= 2) {
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val roadColor = when (edge.roadClass) {
                        RoadClass.AUTOPISTA_CUOTA, RoadClass.LIBRAMIENTO, RoadClass.CORREDOR_CARGA ->
                            colors.accentPrimary.copy(alpha = 0.45f)
                        RoadClass.CARRETERA_FEDERAL ->
                            colors.accentSecondary.copy(alpha = 0.38f)
                        RoadClass.CALLE_RESIDENCIAL, RoadClass.URBANA_RESTRINGIDA ->
                            colors.dangerRed.copy(alpha = 0.45f)
                        RoadClass.CARRETERA_ESTATAL ->
                            Color(0xFF64748B).copy(alpha = 0.40f)
                    }
                    val dash = if (edge.roadClass == RoadClass.CALLE_RESIDENCIAL || !edge.hgvAllowed) {
                        PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    } else null
                    drawPath(
                        path = path,
                        color = roadColor,
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash)
                    )
                }
            }

            // 4. Rutas bloqueadas / alternas
            allRoutes.filter { it.id != selectedRoute?.id }.forEach { alt ->
                val pts = alt.waypoints.map { project(it) }
                if (pts.size >= 2) {
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val altColor = if (alt.safetyAudit.isCompatible) {
                        Color(0xFF94A3B8).copy(alpha = 0.65f)
                    } else {
                        colors.dangerRed.copy(alpha = 0.85f)
                    }
                    drawPath(
                        path = path,
                        color = altColor,
                        style = Stroke(
                            width = 6f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                        )
                    )
                }
            }

            // 5. Ruta seleccionada verificada para tractocamión
            selectedRoute?.let { route ->
                val pts = route.waypoints.map { project(it) }
                if (pts.size >= 2) {
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val routeColor = when {
                        !route.safetyAudit.isCompatible -> colors.dangerRed
                        !route.safetyAudit.safetyGuaranteed -> colors.warningAmber
                        else -> colors.accentSecondary
                    }
                    drawPath(
                        path = path,
                        color = routeColor.copy(alpha = 0.25f),
                        style = Stroke(width = 20f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = path,
                        color = routeColor,
                        style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // 6. Nodos estatales / logísticos
            MexicoFreightCatalog.all32States.forEach { st ->
                val pos = project(GeoPoint(st.centerLat, st.centerLng))
                drawCircle(color = colors.panelElevated, radius = 7f, center = pos)
                drawCircle(color = colors.accentPrimary, radius = 3.5f, center = pos)
            }

            // 7. POIs verificados (Gasolineras, Diésel, Básculas, Casetas, Paradores)
            pois.forEach { poi ->
                val pos = project(GeoPoint(poi.lat, poi.lng))
                drawCircle(color = colors.safeGreen, radius = 8f, center = pos)
                drawCircle(color = Color.White, radius = 3f, center = pos)
            }

            // 8. Restricciones legales y advertencias de conducción
            restrictions.forEach { res ->
                val pos = project(GeoPoint(res.lat, res.lng))
                val markerColor = if (res.kind == RestrictionKind.LEGAL_PROHIBITION) {
                    colors.dangerRed
                } else {
                    colors.warningAmber
                }
                drawCircle(color = markerColor.copy(alpha = 0.25f), radius = 18f, center = pos)
                drawCircle(color = markerColor, radius = 9f, center = pos)
            }

            // 9. Cursor del tractocamión orientado por rumbo
            val truckPos = project(GeoPoint(location.lat, location.lng))
            drawCircle(color = colors.accentPrimary.copy(alpha = 0.25f), radius = 26f, center = truckPos)
            rotate(degrees = location.bearingDeg, pivot = truckPos) {
                val arrow = Path().apply {
                    moveTo(truckPos.x, truckPos.y - 18f)
                    lineTo(truckPos.x + 13f, truckPos.y + 13f)
                    lineTo(truckPos.x, truckPos.y + 6f)
                    lineTo(truckPos.x - 13f, truckPos.y + 13f)
                    close()
                }
                drawPath(arrow, color = colors.accentPrimary)
                drawPath(arrow, color = Color.White, style = Stroke(width = 2.5f))
            }
        }
    }
}

private fun lngToWorldX(lng: Double, scale: Float): Float =
    ((lng + 180.0) / 360.0 * scale).toFloat()

private fun latToWorldY(lat: Double, scale: Float): Float {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = Math.toRadians(clamped)
    val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    return (y * scale).toFloat()
}
