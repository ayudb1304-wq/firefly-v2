package com.firefly.app.core.protocol

import kotlin.random.Random

/** Beacon sending cadence (PROTOCOL.md §4, PRD B1). Pure function of movement and GPS quality. */
object BeaconPolicy {
    const val MOVING_MS = 3_000L
    const val STATIONARY_MS = 10_000L
    const val POOR_ACCURACY_MS = 30_000L

    /** Speed above which a user counts as moving (metres per second). */
    const val MOVING_SPEED_MPS = 1.0f

    const val JITTER_FRACTION = 0.20

    fun baseIntervalMillis(speedMps: Float?, accuracyBucket: Int): Long = when {
        accuracyBucket >= AccuracyBucket.UNDER_100M -> POOR_ACCURACY_MS
        speedMps != null && speedMps > MOVING_SPEED_MPS -> MOVING_MS
        else -> STATIONARY_MS
    }

    /** Apply ±20% uniform jitter so co-located phones do not synchronise. */
    fun withJitter(baseMillis: Long, random: Random = Random.Default): Long {
        val spread = (baseMillis * JITTER_FRACTION).toLong()
        return baseMillis + random.nextLong(-spread, spread + 1)
    }

    fun nextIntervalMillis(speedMps: Float?, accuracyBucket: Int, random: Random = Random.Default): Long =
        withJitter(baseIntervalMillis(speedMps, accuracyBucket), random)
}
