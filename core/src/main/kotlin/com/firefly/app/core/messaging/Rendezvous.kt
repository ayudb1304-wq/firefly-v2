package com.firefly.app.core.messaging

import com.firefly.app.core.geo.GeoMath
import com.firefly.app.core.geo.LatLon

/**
 * PRD D1: propose the place nearest the midpoint between two friends.
 * With a venue pack that is a named POI; without one it is the midpoint itself,
 * sent as a point (Codebook.POI_POINT) in the packet's position bytes.
 */
object Rendezvous {
    /** Either position older than this triggers a warning before proposing (PRD D1). */
    const val STALE_AFTER_MS = 10 * 60_000L

    data class Place(val index: Int, val name: String, val position: LatLon)

    data class Suggestion(
        /** Chosen POI, or null when proposing the raw midpoint. */
        val poi: Place?,
        /** Where to meet. Equals [poi]'s position when a POI was chosen. */
        val point: LatLon,
        val midpoint: LatLon,
        val distanceFromMeMetres: Double,
        val distanceFromThemMetres: Double,
        val theirPositionStale: Boolean,
    )

    fun suggest(
        me: LatLon,
        them: LatLon,
        theirLastSeenMillis: Long,
        pois: List<Place>,
        nowMillis: Long,
    ): Suggestion {
        val mid = GeoMath.midpoint(me.lat, me.lon, them.lat, them.lon)
        val poi = pois.minByOrNull { GeoMath.distanceMetres(mid.lat, mid.lon, it.position.lat, it.position.lon) }
        val point = poi?.position ?: mid
        return Suggestion(
            poi = poi,
            point = point,
            midpoint = mid,
            distanceFromMeMetres = GeoMath.distanceMetres(me.lat, me.lon, point.lat, point.lon),
            distanceFromThemMetres = GeoMath.distanceMetres(them.lat, them.lon, point.lat, point.lon),
            theirPositionStale = nowMillis - theirLastSeenMillis > STALE_AFTER_MS,
        )
    }
}
