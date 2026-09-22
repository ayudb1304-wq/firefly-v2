package com.firefly.app.core.util

import java.util.ArrayDeque

/** Events per second over a sliding window, for the stats screen and congestion detection. */
class RateWindow(private val windowMillis: Long = 60_000L) {
    private val times = ArrayDeque<Long>()

    fun record(nowMillis: Long) {
        times.addLast(nowMillis)
        prune(nowMillis)
    }

    fun count(nowMillis: Long): Int {
        prune(nowMillis)
        return times.size
    }

    fun perSecond(nowMillis: Long): Double {
        prune(nowMillis)
        if (times.isEmpty()) return 0.0
        val span = (nowMillis - times.peekFirst()).coerceAtLeast(1_000L).coerceAtMost(windowMillis)
        return times.size * 1000.0 / span
    }

    private fun prune(nowMillis: Long) {
        while (times.isNotEmpty() && nowMillis - times.peekFirst() > windowMillis) times.pollFirst()
    }
}
