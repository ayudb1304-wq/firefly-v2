package com.firefly.app.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapProjectionTest {
    // Same corners as venue/venue.example.json
    private val proj = MapProjection(
        imageWidth = 2000, imageHeight = 2000,
        topLeft = LatLon(19.0000, 72.8200), topRight = LatLon(19.0000, 72.8300),
        bottomLeft = LatLon(18.9900, 72.8200), bottomRight = LatLon(18.9900, 72.8300),
    )

    @Test
    fun `corners map to image corners`() {
        val tl = proj.toPixel(19.0000, 72.8200)
        val br = proj.toPixel(18.9900, 72.8300)
        assertEquals(0.0, tl.x, 1e-6); assertEquals(0.0, tl.y, 1e-6)
        assertEquals(2000.0, br.x, 1e-6); assertEquals(2000.0, br.y, 1e-6)
    }

    @Test
    fun `centre maps to centre and back`() {
        val c = proj.toPixel(18.9950, 72.8250)
        assertEquals(1000.0, c.x, 1e-6)
        assertEquals(1000.0, c.y, 1e-6)
        val g = proj.toGeo(1000.0, 1000.0)
        assertEquals(18.9950, g.lat, 1e-9)
        assertEquals(72.8250, g.lon, 1e-9)
    }

    @Test
    fun `main stage POI lands where expected and y grows southwards`() {
        val p = proj.toPixel(18.9950, 72.8250)
        val south = proj.toPixel(18.9940, 72.8250)
        assertTrue(south.y > p.y)
        assertEquals(200.0, south.y - p.y, 1e-6)
    }

    @Test
    fun `pixels per metre is sensible for a 1 km wide map`() {
        // 0.01° of longitude at 19°N ≈ 1052 m across 2000 px → ~1.9 px/m
        assertEquals(1.9, proj.pixelsPerMetre(), 0.1)
    }

    @Test
    fun `slightly rotated calibration still round trips`() {
        val rotated = MapProjection(
            imageWidth = 1000, imageHeight = 800,
            topLeft = LatLon(19.0010, 72.8200), topRight = LatLon(19.0000, 72.8300),
            bottomLeft = LatLon(18.9910, 72.8190), bottomRight = LatLon(18.9900, 72.8290),
        )
        val g = LatLon(18.9955, 72.8248)
        val px = rotated.toPixel(g.lat, g.lon)
        val back = rotated.toGeo(px.x, px.y)
        assertEquals(g.lat, back.lat, 1e-9)
        assertEquals(g.lon, back.lon, 1e-9)
    }

    @Test
    fun `contains and centre`() {
        assertTrue(proj.contains(18.9950, 72.8250))
        assertTrue(proj.contains(19.0000, 72.8200))
        assertTrue(!proj.contains(19.0001, 72.8250))
        assertTrue(!proj.contains(18.9950, 72.8301))
        val c = proj.centre()
        assertEquals(18.9950, c.lat, 1e-9)
        assertEquals(72.8250, c.lon, 1e-9)
    }

    @Test
    fun `centredOn builds a map of the requested width around a point`() {
        val p = MapProjection.centredOn(28.6139, 77.2090, 400.0, 1200, 1200)
        val c = p.centre()
        assertEquals(28.6139, c.lat, 1e-9)
        assertEquals(77.2090, c.lon, 1e-9)
        val left = p.toGeo(0.0, 600.0)
        val right = p.toGeo(1200.0, 600.0)
        assertEquals(400.0, GeoMath.distanceMetres(left.lat, left.lon, right.lat, right.lon), 1.0)
        val top = p.toGeo(600.0, 0.0)
        val bottom = p.toGeo(600.0, 1200.0)
        assertEquals(400.0, GeoMath.distanceMetres(top.lat, top.lon, bottom.lat, bottom.lon), 1.0)
        assertEquals(3.0, p.pixelsPerMetre(), 0.05)
        assertTrue(p.contains(28.6139, 77.2090))
    }
}
