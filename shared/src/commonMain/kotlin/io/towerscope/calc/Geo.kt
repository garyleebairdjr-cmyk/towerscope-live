package io.towerscope.calc

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    private fun Double.rad() = this * PI / 180.0
    private fun Double.deg() = this * 180.0 / PI

    /** Great-circle distance in metres (haversine). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1).rad()
        val dLon = (lon2 - lon1).rad()
        val a = sin(dLat / 2).let { it * it } +
            cos(lat1.rad()) * cos(lat2.rad()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from true north (0..360). */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1.rad()
        val phi2 = lat2.rad()
        val dLon = (lon2 - lon1).rad()
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (atan2(y, x).deg() + 360.0) % 360.0
    }

    /** Bounding box (latMin, lonMin, latMax, lonMax) of a square with the given half side. */
    fun boundingBox(lat: Double, lon: Double, halfSideM: Double): DoubleArray {
        val dLat = (halfSideM / EARTH_RADIUS_M).deg()
        val dLon = (halfSideM / (EARTH_RADIUS_M * cos(lat.rad()).coerceAtLeast(0.01))).deg()
        return doubleArrayOf(lat - dLat, lon - dLon, lat + dLat, lon + dLon)
    }

    private val cardinals = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun cardinal(deg: Double): String = cardinals[(((deg % 360 + 360) % 360) / 22.5 + 0.5).toInt() % 16]

    /** Smallest signed angle a→b in degrees (-180..180). */
    fun angleDelta(a: Double, b: Double): Double = ((b - a + 540.0) % 360.0) - 180.0
}
