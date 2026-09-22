package com.firefly.app.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoMathTest {
    @Test
    fun `distance and bearing between Mumbai landmarks`() {
        // Gateway of India -> CST, roughly 2.5 km north-ish.
        val d = GeoMath.distanceMetres(18.9220, 72.8347, 18.9398, 72.8355)
        assertEquals(1_980.0, d, 60.0)
        val b = GeoMath.bearingDegrees(18.9220, 72.8347, 18.9398, 72.8355)
        assertEquals(2.5, b, 2.0)
        assertEquals(0.0, GeoMath.distanceMetres(19.0, 72.8, 19.0, 72.8), 1e-9)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, GeoMath.distanceMetres(0.0, 0.0, 1.0, 0.0), 50.0)
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 1e-6)
        assertEquals(270.0, GeoMath.bearingDegrees(0.0, 1.0, 0.0, 0.0), 1e-6)
    }
}
