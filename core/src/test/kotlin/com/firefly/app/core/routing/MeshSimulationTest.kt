package com.firefly.app.core.routing

import com.firefly.app.core.geo.LatLon
import com.firefly.app.core.protocol.Codebook
import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.PacketCodec
import com.firefly.app.core.protocol.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Phase 3 exit test in miniature: A and C are out of range, B hears both.
 * Every node runs the real dedupe + policy + queue; "radio" is a link table.
 */
class MeshSimulationTest {
    private class Node(val id: Int, val pos: LatLon) {
        val dedupe = DedupeCache()
        val queue = RelayQueue()
        val received = mutableListOf<Packet>()
        val known = mutableMapOf<Int, RelayPolicy.TargetInfo>()
        var relayed = 0
        var deduped = 0
    }

    private val a = Node(0xAAAA, LatLon(19.0000, 72.8000))
    private val b = Node(0xBBBB, LatLon(19.0010, 72.8000))
    private val c = Node(0xCCCC, LatLon(19.0020, 72.8000))
    private val nodes = listOf(a, b, c)
    private val links = mapOf(a to listOf(b), b to listOf(a, c), c to listOf(b))
    private val now = 10_000L

    /** Deliver bytes from [from] to its neighbours, running the receive pipeline on each. */
    private fun air(from: Node, bytes: ByteArray) {
        for (n in links.getValue(from)) {
            val p = PacketCodec.decode(bytes)!!
            if (!n.dedupe.checkAndInsert(p.senderId, p.seq, now)) { n.deduped++; continue }
            n.received += p
            if (p.hasPosition) n.known[p.senderId] = RelayPolicy.TargetInfo(LatLon(p.latitude, p.longitude), now)
            val d = RelayPolicy.decide(p, n.id, n.pos, n.known[p.target], now)
            if (d is RelayPolicy.Decision.Relay) n.queue.offer(p.relayed(), now)
        }
    }

    private fun drain() {
        var progressed = true
        while (progressed) {
            progressed = false
            for (n in nodes) {
                val e = n.queue.poll(now) ?: continue
                n.relayed++
                progressed = true
                air(n, PacketCodec.encode(e.packet))
            }
        }
    }

    @Test
    fun `broadcast ping from A reaches C via B with hops 1 and no storm`() {
        val ping = Packet(
            type = Protocol.Type.PING, groupId = 1, senderId = a.id, seq = 7, ttl = 6, hops = 0,
            latE6 = Packet.toE6(a.pos.lat), lonE6 = Packet.toE6(a.pos.lon), code = Codebook.WHERE_ARE_YOU, ackRequested = true,
        )
        a.dedupe.checkAndInsert(a.id, 7, now)
        air(a, PacketCodec.encode(ping))
        drain()

        val atC = c.received.single { it.senderId == a.id }
        assertEquals(1, atC.hops)
        assertEquals(5, atC.ttl)
        assertEquals(1, b.relayed, "B relays once")
        assertEquals(1, c.relayed, "C relays once more (broadcast), which B then dedupes")
        assertEquals(1, b.deduped, "B hears its own relay echoed back from C and drops it")
        assertEquals(0, a.relayed, "A drops the echo of its own packet")
        assertTrue(a.received.none { it.senderId == a.id })
    }

    @Test
    fun `targeted ping A to C is geo-routed through B, and C does not re-relay it`() {
        // Everyone has heard everyone's beacon once, so positions are known.
        for (n in nodes) for (m in nodes) if (n !== m) n.known[m.id] = RelayPolicy.TargetInfo(m.pos, now)
        val ping = Packet(
            type = Protocol.Type.PING, groupId = 1, senderId = a.id, seq = 8, ttl = 6, hops = 0,
            latE6 = Packet.toE6(a.pos.lat), lonE6 = Packet.toE6(a.pos.lon), code = Codebook.MEET_AT, target = c.id, ackRequested = true,
        )
        a.dedupe.checkAndInsert(a.id, 8, now)
        air(a, PacketCodec.encode(ping))
        drain()
        assertEquals(1, c.received.count { it.senderId == a.id && it.hops == 1 })
        assertEquals(1, b.relayed)
        assertEquals(0, c.relayed, "target does not relay")
    }

    @Test
    fun `TTL bounds propagation`() {
        val ping = Packet(type = Protocol.Type.PING, groupId = 1, senderId = a.id, seq = 9, ttl = 1, hops = 0, code = Codebook.WHERE_ARE_YOU)
        air(a, PacketCodec.encode(ping))
        drain()
        assertEquals(1, b.relayed)
        assertEquals(0, c.relayed, "C receives with TTL 0 and stops")
        assertEquals(0, c.received.single().ttl)
    }
}
