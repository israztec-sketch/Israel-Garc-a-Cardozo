package com.example.argon.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.data.local.OfflineRegionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class OfflineMapStorage(
    private val context: Context,
    private val offlineRegionDao: OfflineRegionDao
) {
    private val tilesDir: File by lazy {
        File(context.filesDir, "map_tiles").apply { mkdirs() }
    }

    private val packsDir: File by lazy {
        File(context.filesDir, "offline_packs").apply { mkdirs() }
    }

    suspend fun downloadStatePack(stateCode: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val state = MexicoFreightCatalog.all32States.firstOrNull { it.code == stateCode }
                ?: error("Estado no encontrado: $stateCode")

            val edges = MexicoFreightCatalog.freightEdges.filter { it.stateCode == stateCode }
            val restrictions = MexicoFreightCatalog.verifiedRestrictions.filter { it.stateCode == stateCode }
            val pois = MexicoFreightCatalog.verifiedPois.filter { it.stateCode == stateCode }

            // Guardar manifiesto cartográfico local para navegación sin conexión
            val packFile = File(packsDir, "pack_${stateCode.lowercase()}.json")
            val payload = buildString {
                append("{")
                append("\"stateCode\":\"$stateCode\",")
                append("\"stateName\":\"${state.name}\",")
                append("\"centerLat\":${state.centerLat},")
                append("\"centerLng\":${state.centerLng},")
                append("\"corridors\":${state.mainCorridors.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")},")
                append("\"edgeCount\":${edges.size},")
                append("\"restrictionCount\":${restrictions.size},")
                append("\"poiCount\":${pois.size}")
                append("}")
            }
            packFile.writeText(payload)

            // Pre-descargar teselas base alrededor del centro logístico si hay conexión (best-effort sin fallar si no hay red)
            val (tx, ty) = latLngToTile(state.centerLat, state.centerLng, 6)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    runCatching { fetchAndCacheTile(6, tx + dx, ty + dy) }
                }
            }

            offlineRegionDao.updateDownloadState(
                code = stateCode,
                downloaded = true,
                timestamp = System.currentTimeMillis(),
                segments = edges.size.coerceAtLeast(4),
                restrictions = restrictions.size.coerceAtLeast(1),
                pois = pois.size.coerceAtLeast(1)
            )
            edges.size
        }
    }

    suspend fun deleteStatePack(stateCode: String) = withContext(Dispatchers.IO) {
        val packFile = File(packsDir, "pack_${stateCode.lowercase()}.json")
        if (packFile.exists()) packFile.delete()
        offlineRegionDao.updateDownloadState(
            code = stateCode,
            downloaded = false,
            timestamp = null,
            segments = 0,
            restrictions = 0,
            pois = 0
        )
    }

    suspend fun getTileBitmap(z: Int, x: Int, y: Int, allowNetwork: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(tilesDir, "${z}_${x}_${y}.png")
        if (file.exists() && file.length() > 0L) {
            return@withContext BitmapFactory.decodeFile(file.absolutePath)
        }
        if (!allowNetwork) return@withContext null
        runCatching { fetchAndCacheTile(z, x, y) }.getOrNull()
    }

    private fun fetchAndCacheTile(z: Int, x: Int, y: Int): Bitmap? {
        val maxTile = (1 shl z)
        if (x < 0 || y < 0 || x >= maxTile || y >= maxTile) return null
        val file = File(tilesDir, "${z}_${x}_${y}.png")
        if (file.exists() && file.length() > 0L) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "ArgonGpsTruck/1.0 (Android Heavy Freight)")
            .build()
        CartographicApiClient.okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            FileOutputStream(file).use { it.write(bytes) }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    fun getDownloadedStorageBytes(): Long {
        val packsSize = packsDir.listFiles()?.sumOf { it.length() } ?: 0L
        val tilesSize = tilesDir.listFiles()?.sumOf { it.length() } ?: 0L
        return packsSize + tilesSize
    }

    companion object {
        fun latLngToTile(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
            val n = (1 shl zoom).toDouble()
            val x = floor((lng + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            val y = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
            return x.coerceIn(0, (1 shl zoom) - 1) to y.coerceIn(0, (1 shl zoom) - 1)
        }
    }
}
