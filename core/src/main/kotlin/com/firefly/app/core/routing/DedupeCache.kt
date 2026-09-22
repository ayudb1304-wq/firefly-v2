package com.firefly.app.core.routing

/**
 * LRU of recently seen `(senderId, seq)` pairs (PROTOCOL.md §5 step 2,
 * ARCHITECTURE.md §4). Every received packet passes through [checkAndInsert]
 * before any other processing. Not thread-safe; the radio pipeline is single-threaded.
 */
class DedupeCache(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private val seen = object : LinkedHashMap<Int, Long>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>): Boolean = size > capacity
    }

    /** @return true if this is the first time we see the pair (caller should process it). */
    fun checkAndInsert(senderId: Int, seq: Int, nowMillis: Long): Boolean {
        val key = key(senderId, seq)
        val last = seen[key]
        if (last != null && nowMillis - last < ttlMillis) return false
        seen[key] = nowMillis
        return true
    }

    fun contains(senderId: Int, seq: Int, nowMillis: Long): Boolean {
        val last = seen[key(senderId, seq)] ?: return false
        return nowMillis - last < ttlMillis
    }

    val size: Int get() = seen.size

    fun clear() = seen.clear()

    private fun key(senderId: Int, seq: Int): Int = (senderId shl 16) or (seq and 0xFFFF)

    companion object {
        const val DEFAULT_CAPACITY = 512
        const val DEFAULT_TTL_MILLIS = 5 * 60 * 1000L
    }
}
