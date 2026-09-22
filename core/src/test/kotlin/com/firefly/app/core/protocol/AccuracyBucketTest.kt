package com.firefly.app.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccuracyBucketTest {
    @Test
    fun `buckets follow the protocol table`() {
        assertEquals(0, AccuracyBucket.fromMetres(null))
        assertEquals(0, AccuracyBucket.fromMetres(0f))
        assertEquals(1, AccuracyBucket.fromMetres(9.9f))
        assertEquals(2, AccuracyBucket.fromMetres(10f))
        assertEquals(2, AccuracyBucket.fromMetres(24.9f))
        assertEquals(3, AccuracyBucket.fromMetres(25f))
        assertEquals(4, AccuracyBucket.fromMetres(50f))
        assertEquals(4, AccuracyBucket.fromMetres(99.9f))
        assertEquals(5, AccuracyBucket.fromMetres(100f))
        assertEquals(5, AccuracyBucket.fromMetres(5000f))
    }
}
