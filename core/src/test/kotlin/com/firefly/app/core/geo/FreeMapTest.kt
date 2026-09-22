package com.firefly.app.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreeMapTest {
    @Test
    fun `width grows with the farthest member and is clamped`() {
        assertEquals(200.0, FreeMap.widthFor(null))
        assertEquals(200.0, FreeMap.widthFor(30.0))
        assertEquals(2 * 140 / 0.7, FreeMap.widthFor(140.0), 1e-9)
        assertEquals(5000.0, FreeMap.widthFor(10_000.0))
    }

    @Test
    fun `hysteresis ignores small changes`() {
        assertEquals(400.0, FreeMap.nextWidth(400.0, 150.0), "150 m needs 428 m, within 25 %")
        assertEquals(2 * 300 / 0.7, FreeMap.nextWidth(400.0, 300.0), 1e-9)
        assertEquals(200.0, FreeMap.nextWidth(400.0, 20.0))
        assertEquals(200.0, FreeMap.nextWidth(null, null))
    }

    @Test
    fun `recentre only after drifting far from the centre`() {
        val c = LatLon(19.0, 72.8)
        assertTrue(FreeMap.shouldRecentre(null, c, 400.0))
        val near = LatLon(19.0 + 50 / 111_195.0, 72.8) // 50 m north
        assertFalse(FreeMap.shouldRecentre(c, near, 400.0))
        val far = LatLon(19.0 + 150 / 111_195.0, 72.8) // 150 m north, > 0.6 × 200
        assertTrue(FreeMap.shouldRecentre(c, far, 400.0))
    }

    @Test
    fun `grid spacing picks a readable step`() {
        assertEquals(20.0, FreeMap.gridSpacingMetres(3.0, 60.0))
        assertEquals(100.0, FreeMap.gridSpacingMetres(0.5, 50.0))
        assertEquals(2000.0, FreeMap.gridSpacingMetres(0.001, 50.0))
    }
}
