package com.firefly.app.core.messaging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LighthousePatternTest {
    @Test
    fun `three colours, first two differ, deterministic`() {
        for (id in listOf(1, 0x1A2B, 0x3C4D, 0xFFFE, 12345)) {
            val p = LighthousePattern.forSender(id)
            assertEquals(3, p.size)
            assertNotEquals(p[0], p[1])
            assertEquals(p, LighthousePattern.forSender(id))
        }
    }

    @Test
    fun `neighbouring ids get different patterns and the space is well used`() {
        val patterns = (1..2000).map { LighthousePattern.forSender(it) }
        assertTrue(patterns.zipWithNext().count { (a, b) -> a == b } < 20, "adjacent ids rarely collide")
        assertTrue(patterns.toSet().size > 150, "most of the 180 valid patterns appear")
    }

    @Test
    fun `label reads as a rhythm`() {
        val p = listOf(LighthousePattern.Colour.RED, LighthousePattern.Colour.WHITE, LighthousePattern.Colour.RED)
        assertEquals("red-white-red", LighthousePattern.label(p))
    }
}
