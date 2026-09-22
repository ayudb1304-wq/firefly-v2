package com.firefly.app.core.routing

import com.firefly.app.core.geo.LatLon
import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.core.routing.RelayPolicy.Decision
import com.firefly.app.core.routing.RelayPolicy.Reason
import com.firefly.app.core.routing.RelayPolicy.TargetInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelayPolicyTest {
    private val me = 0x1111
    private val alice = 0x2222
    private val bob = 0x3333
    private val now = 1_000_000L

    // Positions along a north–south line (1e-3° lat ≈ 111 m).
    private val aliceAt = LatLon(19.0000, 72.8000)   // origin, south
    private val meAt = LatLon(19.0010, 72.8000)      // 111 m north of Alice
    private val bobAt = LatLon(19.0020, 72.8000)     // 222 m north of Alice, 111 m from me

    private fun packet(
        from: Int = alice, target: Int = Protocol.TARGET_BROADCAST, ttl: Int = 6,
        priority: Boolean = false, pos: LatLon? = aliceAt, type: Int = Protocol.Type.PING,
    ) = Packet(
        type = type, groupId = 1, senderId = from, seq = 1, ttl = ttl, hops = 0, priority = priority,
        latE6 = pos?.let { Packet.toE6(it.lat) } ?: 0, lonE6 = pos?.let { Packet.toE6(it.lon) } ?: 0, target = target,
    )

    private fun decide(p: Packet, myPos: LatLon? = meAt, target: TargetInfo? = TargetInfo(bobAt, now - 1_000)) =
        RelayPolicy.decide(p, me, myPos, target, now)

    @Test
    fun `case table from PROTOCOL md section 5`() {
        // 4. own packet
        assertEquals(Decision.Drop(Reason.OWN_PACKET), decide(packet(from = me)))
        // 5. TTL exhausted
        assertEquals(Decision.Drop(Reason.TTL_EXHAUSTED), decide(packet(ttl = 0)))
        // 6a. priority always relays, even targeted and even if I am not closer
        assertEquals(Decision.Relay, decide(packet(target = bob, priority = true), myPos = LatLon(18.9, 72.8)))
        // 6b. broadcast relays (beacons, names, group pings)
        assertEquals(Decision.Relay, decide(packet()))
        assertEquals(Decision.Relay, decide(packet(type = Protocol.Type.BEACON)))
        // 6c-i. I am the target: never relay
        assertEquals(Decision.Drop(Reason.I_AM_TARGET), decide(packet(target = me)))
        // 6c-ii. geo gate: I (111 m from Bob) am closer than Alice (222 m) by more than 15 m → relay
        assertEquals(Decision.Relay, decide(packet(target = bob)))
        // geo gate: I am farther from Bob than Alice → drop
        assertEquals(Decision.Drop(Reason.NOT_CLOSER), decide(packet(target = bob), myPos = LatLon(18.9990, 72.8000)))
        // geo gate: closer but within the 15 m margin → drop
        val barely = LatLon(19.0000 + 0.00010, 72.8000) // ~11 m closer than Alice
        assertEquals(Decision.Drop(Reason.NOT_CLOSER), decide(packet(target = bob), myPos = barely))
        // 6c-iii. target unknown → flood
        assertEquals(Decision.Relay, decide(packet(target = bob), target = null))
        // target position stale (> 10 min) → flood
        assertEquals(Decision.Relay, decide(packet(target = bob), target = TargetInfo(bobAt, now - 11 * 60_000L)))
        // my own position unknown → flood
        assertEquals(Decision.Relay, decide(packet(target = bob), myPos = null))
        // sender position absent from the packet → flood
        assertEquals(Decision.Relay, decide(packet(target = bob, pos = null)))
    }

    @Test
    fun `ACKs to others obey the geo gate too`() {
        val ack = packet(type = Protocol.Type.ACK, target = bob)
        assertEquals(Decision.Relay, decide(ack))
        assertEquals(Decision.Drop(Reason.NOT_CLOSER), decide(ack, myPos = LatLon(18.9990, 72.8000)))
    }
}
