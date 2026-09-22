package com.firefly.app.core.routing

import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RelayQueueTest {
    private fun p(seq: Int, target: Int = Protocol.TARGET_BROADCAST, priority: Boolean = false) =
        Packet(type = 1, groupId = 1, senderId = 0x2222, seq = seq, ttl = 5, hops = 1, target = target, priority = priority)

    @Test
    fun `priority order is SOS, targeted, broadcast, then FIFO`() {
        val q = RelayQueue()
        q.offer(p(1), 0)
        q.offer(p(2, target = 0x3333), 0)
        q.offer(p(3, priority = true), 0)
        q.offer(p(4), 0)
        assertEquals(3, q.poll(1)!!.packet.seq)
        assertEquals(2, q.poll(1)!!.packet.seq)
        assertEquals(1, q.poll(1)!!.packet.seq)
        assertEquals(4, q.poll(1)!!.packet.seq)
        assertNull(q.poll(1))
    }

    @Test
    fun `rate limit admits 10 per 5 s then makes callers wait`() {
        val q = RelayQueue()
        repeat(12) { q.offer(p(it), 0) }
        repeat(10) { assertNotNull(q.poll(100L * it)) }
        assertNull(q.poll(1_000), "11th within the window is refused")
        assertEquals(4_000L, q.waitMillis(1_000))
        assertNotNull(q.poll(5_000), "window slid: first send at t=0 expired")
        assertEquals(1, q.size)
    }

    @Test
    fun `stale entries are dropped unsent`() {
        val q = RelayQueue(maxAgeMillis = 5_000)
        q.offer(p(1), 0)
        q.offer(p(2), 4_000)
        val e = q.poll(6_000)
        assertEquals(2, e!!.packet.seq)
        assertEquals(1, q.droppedStale)
    }

    @Test
    fun `when full the lowest priority is evicted for a more urgent packet, equal priority is refused`() {
        val q = RelayQueue(capacity = 2)
        assertTrue(q.offer(p(1), 0))
        assertTrue(q.offer(p(2), 0))
        assertFalse(q.offer(p(3), 0), "same rank as the worst → refused")
        assertTrue(q.offer(p(4, priority = true), 0), "SOS evicts a broadcast")
        assertEquals(2, q.size)
        assertEquals(4, q.poll(1)!!.packet.seq)
        assertEquals(1, q.poll(1)!!.packet.seq, "the eldest broadcast survived, the newest was evicted")
        assertEquals(2, q.droppedFull)
    }

    @Test
    fun `jitter is within 50 to 300 ms and never zero`() {
        val q = RelayQueue(random = Random(9))
        repeat(2000) { assertTrue(q.jitterMillis() in 50L..300L) }
    }
}
