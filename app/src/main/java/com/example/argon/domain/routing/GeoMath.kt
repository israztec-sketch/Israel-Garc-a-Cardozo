package com.example.argon.domain.routing

import com.example.argon.domain.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val sinLat = sin(dLat / 2.0)
        val sinLng = sin(dLng / 2.0)
        val h = sinLat.pow(2.0) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinLng.pow(2.0)
        val c = 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
        return EARTH_RADIUS_KM * c
    }

    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon = Math.toRadians(to.lng - from.lng)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360.0) % 360.0).toFloat()
    }

    fun minDistanceToPolylineMeters(point: GeoPoint, polyline: List<GeoPoint>): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        if (polyline.size == 1) return haversineKm(point, polyline.first()) * 1000.0
        var minMeters = Double.MAX_VALUE
        for (i in 0 until polyline.lastIndex) {
            val d = pointToSegmentMeters(point, polyline[i], polyline[i + 1])
            if (d < minMeters) minMeters = d
        }
        return minMeters
    }

    private fun pointToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val latScale = 111_320.0
        val lngScale = 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2.0))
        val ax = 0.0
        val ay = 0.0
        val bx = (b.lng - a.lng) * lngScale
        val by = (b.lat - a.lat) * latScale
        val px = (p.lng - a.lng) * lngScale
        val py = (p.lat - a.lat) * latScale
        val ab2 = bx * bx + by * by
        val t = if (ab2 <= 1e-6) 0.0 else ((px * bx + py * by) / ab2).coerceIn(0.0, 1.0)
        val cx = ax + t * bx
        val cy = ay + t * by
        return sqrt((px - cx).pow(2.0) + (py - cy).pow(2.0))
    }

    /** Decodes Valhalla / Google polyline (precision = 1e6 for Valhalla, 1e5 for Google) */
    fun decodePolyline(encoded: String, precision: Double = 1e6): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dLat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dLng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dLng

            poly.add(GeoPoint(lat / precision, lng / precision))
        }
        return poly
    }
}
