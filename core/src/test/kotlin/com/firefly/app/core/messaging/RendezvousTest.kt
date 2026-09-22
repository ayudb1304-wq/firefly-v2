package com.firefly.app.core.messaging

import com.firefly.app.core.geo.GeoMath
import com.firefly.app.core.geo.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RendezvousTest {
    private val me = LatLon(18.9950, 72.8250)
    private val them = LatLon(18.9930, 72.8290)
    private val pois = listOf(
        Rendezvous.Place(1, "Main Stage", LatLon(18.9950, 72.8250)),
        Rendezvous.Place(4, "Bar 2", LatLon(18.9940, 72.8270)),
        Rendezvous.Place(9, "Main Gate", LatLon(18.9905, 72.8215)),
    )

    @Test
    fun `midpoint is halfway`() {
        val m = GeoMath.midpoint(me.lat, me.lon, them.lat, them.lon)
        val a = GeoMath.distanceMetres(me.lat, me.lon, m.lat, m.lon)
        val b = GeoMath.distanceMetres(them.lat, them.lon, m.lat, m.lon)
        assertEquals(a, b, 0.5)
        assertEquals(18.9940, m.lat, 1e-6)
        assertEquals(72.8270, m.lon, 1e-6)
    }

    @Test
    fun `picks the POI nearest the midpoint`() {
        val s = Rendezvous.suggest(me, them, theirLastSeenMillis = 1_000, pois = pois, nowMillis = 2_000)
        val poi = s.poi ?: error("expected a POI")
        assertEquals(4, poi.index)
        assertEquals("Bar 2", poi.name)
        assertEquals(poi.position, s.point)
        assertFalse(s.theirPositionStale)
        assertEquals(s.distanceFromMeMetres, s.distanceFromThemMetres, 1.0)
    }

    @Test
    fun `without POIs proposes the midpoint itself`() {
        val s = Rendezvous.suggest(me, them, 1_000, emptyList(), 2_000)
        assertNull(s.poi)
        assertEquals(s.midpoint, s.point)
    }

    @Test
    fun `flags a stale position`() {
        val s = Rendezvous.suggest(me, them, theirLastSeenMillis = 0, pois = pois, nowMillis = Rendezvous.STALE_AFTER_MS + 1)
        assertTrue(s.theirPositionStale)
    }

    @Test
    fun `angle delta wraps`() {
        assertEquals(10.0, GeoMath.angleDelta(5.0, 355.0), 1e-9)
        assertEquals(-10.0, GeoMath.angleDelta(355.0, 5.0), 1e-9)
        assertEquals(180.0, GeoMath.angleDelta(180.0, 0.0), 1e-9)
    }
}
