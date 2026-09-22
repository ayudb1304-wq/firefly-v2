package com.firefly.app.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 helpers. Venue-scale distances, so a spherical earth is plenty. */
object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Geographic midpoint of two points (spherical; exact enough at venue scale). */
    fun midpoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double): LatLon {
        val p1 = Math.toRadians(lat1); val l1 = Math.toRadians(lon1)
        val p2 = Math.toRadians(lat2); val l2 = Math.toRadians(lon2)
        val bx = cos(p2) * cos(l2 - l1)
        val by = cos(p2) * sin(l2 - l1)
        val lat = atan2(sin(p1) + sin(p2), sqrt((cos(p1) + bx) * (cos(p1) + bx) + by * by))
        val lon = l1 + atan2(by, cos(p1) + bx)
        return LatLon(Math.toDegrees(lat), ((Math.toDegrees(lon) + 540) % 360) - 180)
    }

    /** Signed smallest difference a − b in degrees, in (−180, 180]. */
    fun angleDelta(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180) d -= 360
        if (d <= -180) d += 360
        return d
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from north in [0, 360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
