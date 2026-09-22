package com.firefly.app.core.routing

import com.firefly.app.core.protocol.Packet
import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.random.Random

/**
 * Packets waiting to be re-advertised (ARCHITECTURE.md §4 RelayQueue, PROTOCOL.md §5 steps 7–8).
 *
 * - Bounded to [capacity]; when full the lowest-priority entry is evicted.
 * - Priority: SOS > targeted > broadcast, then oldest first.
 * - Rate limit: at most [maxPerWindow] relays per [windowMillis].
 * - Entries older than [maxAgeMillis] are dropped unsent (a stale beacon is worthless).
 * - Every relay waits a random [jitterMinMillis]..[jitterMaxMillis] before going on air.
 *
 * Not thread-safe; the radio pipeline owns it.
 */
class RelayQueue(
    private val capacity: Int = 32,
    private val maxPerWindow: Int = 10,
    private val windowMillis: Long = 5_000L,
    private val maxAgeMillis: Long = 5_000L,
    private val random: Random = Random.Default,
) {
    class Entry internal constructor(val packet: Packet, val enqueuedAtMillis: Long, val rank: Int, internal val serial: Long)

    private var serial = 0L
    private val queue = PriorityQueue<Entry>(compareBy<Entry> { it.rank }.thenBy { it.serial })
    private val sentAt = ArrayDeque<Long>()

    var droppedFull: Int = 0; private set
    var droppedStale: Int = 0; private set

    val size: Int get() = queue.size

    /** @return false if the packet was not accepted (queue full of higher-priority traffic). */
    fun offer(packet: Packet, nowMillis: Long): Boolean {
        val entry = Entry(packet, nowMillis, rank(packet), serial++)
        if (queue.size >= capacity) {
            val worst = queue.maxWithOrNull(compareBy<Entry> { it.rank }.thenBy { it.serial }) ?: return false
            if (worst.rank < entry.rank || (worst.rank == entry.rank)) {
                droppedFull++
                return false
            }
            queue.remove(worst)
            droppedFull++
        }
        queue.add(entry)
        return true
    }

    /** Milliseconds until the rate limiter admits another relay; 0 if it would admit one now. */
    fun waitMillis(nowMillis: Long): Long {
        prune(nowMillis)
        if (sentAt.size < maxPerWindow) return 0
        return (sentAt.peekFirst() + windowMillis - nowMillis).coerceAtLeast(0)
    }

    /**
     * Take the next packet to relay, honouring rate limit and staleness.
     * @return null if the queue is empty or the rate limiter says wait (see [waitMillis]).
     */
    fun poll(nowMillis: Long): Entry? {
        prune(nowMillis)
        while (true) {
            val head = queue.peek() ?: return null
            if (nowMillis - head.enqueuedAtMillis > maxAgeMillis) {
                queue.poll(); droppedStale++; continue
            }
            if (sentAt.size >= maxPerWindow) return null
            queue.poll()
            sentAt.addLast(nowMillis)
            return head
        }
    }

    /** Random pre-relay delay (PROTOCOL.md §5 step 7). Never zero. */
    fun jitterMillis(): Long = random.nextLong(JITTER_MIN_MS, JITTER_MAX_MS + 1)

    fun clear() { queue.clear(); sentAt.clear() }

    private fun prune(nowMillis: Long) {
        while (sentAt.isNotEmpty() && nowMillis - sentAt.peekFirst() >= windowMillis) sentAt.pollFirst()
    }

    companion object {
        const val JITTER_MIN_MS = 50L
        const val JITTER_MAX_MS = 300L

        /** Lower is more urgent. */
        fun rank(p: Packet): Int = when {
            p.priority -> 0
            !p.isBroadcast -> 1
            else -> 2
        }
    }
}
