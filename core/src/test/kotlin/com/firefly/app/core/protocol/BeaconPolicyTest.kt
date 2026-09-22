package com.firefly.app.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BeaconPolicyTest {
    @Test
    fun `interval depends on movement and accuracy`() {
        assertEquals(3_000L, BeaconPolicy.baseIntervalMillis(1.5f, AccuracyBucket.UNDER_10M))
        assertEquals(10_000L, BeaconPolicy.baseIntervalMillis(0.3f, AccuracyBucket.UNDER_50M))
        assertEquals(10_000L, BeaconPolicy.baseIntervalMillis(null, AccuracyBucket.UNKNOWN))
        assertEquals(30_000L, BeaconPolicy.baseIntervalMillis(2.0f, AccuracyBucket.UNDER_100M), "poor GPS wins over movement")
        assertEquals(30_000L, BeaconPolicy.baseIntervalMillis(0f, AccuracyBucket.OVER_100M))
    }

    @Test
    fun `jitter stays within plus or minus 20 percent`() {
        val rnd = Random(42)
        repeat(1000) {
            val v = BeaconPolicy.withJitter(10_000L, rnd)
            assertTrue(v in 8_000L..12_000L, "got $v")
        }
    }
}
