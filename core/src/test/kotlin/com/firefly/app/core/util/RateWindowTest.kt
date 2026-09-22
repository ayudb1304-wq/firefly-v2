package com.firefly.app.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RateWindowTest {
    @Test
    fun `counts within the window and prunes outside it`() {
        val w = RateWindow(10_000)
        for (t in 0 until 20) w.record(t * 1_000L)
        assertEquals(11, w.count(19_000), "t=9..19 inclusive")
        assertEquals(0, w.count(40_000))
    }

    @Test
    fun `per second uses the observed span, floored at one second`() {
        val w = RateWindow(60_000)
        repeat(5) { w.record(100L * it) }
        assertEquals(5.0, w.perSecond(400), 1e-9)
        val w2 = RateWindow(60_000)
        for (t in 0..30) w2.record(t * 1_000L)
        assertEquals(31_000.0 / 30_000.0, w2.perSecond(30_000), 1e-9)
    }
}
