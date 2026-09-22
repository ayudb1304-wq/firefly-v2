package com.firefly.app.core.protocol

/** Accuracy bucket carried in `flags` bits 3–0 (PROTOCOL.md §2). */
object AccuracyBucket {
    const val UNKNOWN = 0
    const val UNDER_10M = 1
    const val UNDER_25M = 2
    const val UNDER_50M = 3
    const val UNDER_100M = 4
    const val OVER_100M = 5
    const val MAX = 5

    fun fromMetres(accuracyMetres: Float?): Int = when {
        accuracyMetres == null || accuracyMetres.isNaN() || accuracyMetres <= 0f -> UNKNOWN
        accuracyMetres < 10f -> UNDER_10M
        accuracyMetres < 25f -> UNDER_25M
        accuracyMetres < 50f -> UNDER_50M
        accuracyMetres < 100f -> UNDER_100M
        else -> OVER_100M
    }

    /** A radius to draw on the map for a bucket (upper bound, metres). */
    fun radiusMetres(bucket: Int): Float = when (bucket) {
        UNDER_10M -> 10f
        UNDER_25M -> 25f
        UNDER_50M -> 50f
        UNDER_100M -> 100f
        OVER_100M -> 150f
        else -> 0f
    }
}
