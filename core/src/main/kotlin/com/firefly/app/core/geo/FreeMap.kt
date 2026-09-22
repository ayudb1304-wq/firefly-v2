package com.firefly.app.core.geo

/**
 * Policy for the venue-less "free map": a north-up square centred near me that
 * zooms to keep the whole group in view. Pure so it can be unit-tested.
 */
object FreeMap {
    const val MIN_WIDTH_M = 200.0
    const val MAX_WIDTH_M = 5_000.0

    /** How much of the map the group may fill before we zoom out. */
    private const val FILL = 0.7

    /** Ignore width changes smaller than this fraction, so the map does not breathe on GPS noise. */
    private const val HYSTERESIS = 0.25

    /** Recentre when I drift beyond this fraction of the half-width from the centre. */
    private const val RECENTRE_AT = 0.6

    /** Width needed to show every member at [maxMemberDistanceM] from the centre. */
    fun widthFor(maxMemberDistanceM: Double?): Double {
        if (maxMemberDistanceM == null || maxMemberDistanceM <= 0) return MIN_WIDTH_M
        return (2 * maxMemberDistanceM / FILL).coerceIn(MIN_WIDTH_M, MAX_WIDTH_M)
    }

    /** Apply hysteresis against the width currently shown. */
    fun nextWidth(currentWidthM: Double?, maxMemberDistanceM: Double?): Double {
        val wanted = widthFor(maxMemberDistanceM)
        if (currentWidthM == null) return wanted
        val ratio = wanted / currentWidthM
        return if (ratio > 1 + HYSTERESIS || ratio < 1 - HYSTERESIS) wanted else currentWidthM
    }

    /** Whether the centre should jump to [me] given the current frame. */
    fun shouldRecentre(currentCentre: LatLon?, me: LatLon, widthM: Double): Boolean {
        if (currentCentre == null) return true
        val d = GeoMath.distanceMetres(currentCentre.lat, currentCentre.lon, me.lat, me.lon)
        return d > widthM / 2 * RECENTRE_AT
    }

    /** Grid spacing in metres: the smallest of a pleasant set that is at least [minPx] on screen. */
    fun gridSpacingMetres(pixelsPerMetre: Double, minPx: Double): Double {
        val candidates = doubleArrayOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0)
        return candidates.firstOrNull { it * pixelsPerMetre >= minPx } ?: candidates.last()
    }
}
