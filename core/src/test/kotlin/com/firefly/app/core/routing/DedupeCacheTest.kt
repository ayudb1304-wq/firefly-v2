package com.firefly.app.core.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DedupeCacheTest {
    @Test
    fun `first sight is new, repeat is duplicate`() {
        val c = DedupeCache()
        assertTrue(c.checkAndInsert(0x1A2B, 42, 1_000))
        assertFalse(c.checkAndInsert(0x1A2B, 42, 2_000))
        assertTrue(c.checkAndInsert(0x1A2B, 43, 2_000))
        assertTrue(c.checkAndInsert(0x1A2C, 42, 2_000), "different sender, same seq")
    }

    @Test
    fun `entries expire after the TTL`() {
        val c = DedupeCache(ttlMillis = 5 * 60 * 1000L)
        assertTrue(c.checkAndInsert(1, 1, 0))
        assertFalse(c.checkAndInsert(1, 1, 5 * 60 * 1000L - 1))
        assertTrue(c.checkAndInsert(1, 1, 5 * 60 * 1000L))
    }

    @Test
    fun `capacity evicts least recently used`() {
        val c = DedupeCache(capacity = 3, ttlMillis = Long.MAX_VALUE / 2)
        c.checkAndInsert(1, 1, 0)
        c.checkAndInsert(1, 2, 0)
        c.checkAndInsert(1, 3, 0)
        assertFalse(c.checkAndInsert(1, 1, 1), "touch (1,1) so (1,2) is now eldest")
        c.checkAndInsert(1, 4, 1)
        assertEquals(3, c.size)
        assertTrue(c.checkAndInsert(1, 2, 2), "(1,2) was evicted")
        assertFalse(c.checkAndInsert(1, 1, 2), "(1,1) survived")
    }

    @Test
    fun `seq wraps do not collide across senders`() {
        val c = DedupeCache()
        assertTrue(c.checkAndInsert(0xFFFF, 0, 0))
        assertTrue(c.checkAndInsert(0xFFFE, 0x10000 and 0xFFFF, 0), "keys are packed into distinct ints")
    }
}
