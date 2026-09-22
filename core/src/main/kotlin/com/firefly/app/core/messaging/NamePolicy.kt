package com.firefly.app.core.messaging

/** NAME packet cadence (PRD A2): every 30 s for the first 5 minutes after joining, then every 5 minutes. */
object NamePolicy {
    const val EARLY_INTERVAL_MS = 30_000L
    const val LATE_INTERVAL_MS = 5 * 60_000L
    const val EARLY_PHASE_MS = 5 * 60_000L

    fun intervalMillis(sinceJoinMillis: Long): Long =
        if (sinceJoinMillis < EARLY_PHASE_MS) EARLY_INTERVAL_MS else LATE_INTERVAL_MS

    /** Display names are ≤ 8 printable ASCII characters; anything else is dropped or truncated. */
    fun sanitise(raw: String): String =
        raw.trim().filter { it.code in 0x20..0x7E }.take(MAX_LENGTH)

    const val MAX_LENGTH = 8
}
