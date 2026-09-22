package com.firefly.app.core.routing

import com.firefly.app.core.geo.GeoMath
import com.firefly.app.core.geo.LatLon
import com.firefly.app.core.protocol.Packet

/**
 * PROTOCOL.md §5 steps 4–6 as a pure function. Called after dedupe and after
 * local state has been updated. ACKing a packet addressed to me is handled by
 * the caller; this only says whether to put the packet back on air.
 */
object RelayPolicy {

    sealed interface Decision {
        data object Relay : Decision
        data class Drop(val reason: Reason) : Decision
    }

    enum class Reason { OWN_PACKET, TTL_EXHAUSTED, I_AM_TARGET, NOT_CLOSER }

    /** Geo-routing gate margin: I must be at least this much closer to the target than the sender was. */
    const val CLOSER_BY_METRES = 15.0

    /** A target position older than this counts as unknown → fall back to flooding. */
    const val TARGET_POSITION_MAX_AGE_MS = 10 * 60_000L

    /** What we know about the packet's target, if anything. */
    data class TargetInfo(val position: LatLon, val lastSeenMillis: Long)

    fun decide(
        packet: Packet,
        myId: Int,
        myPosition: LatLon?,
        target: TargetInfo?,
        nowMillis: Long,
    ): Decision {
        if (packet.senderId == myId) return Decision.Drop(Reason.OWN_PACKET)
        if (packet.ttl == 0) return Decision.Drop(Reason.TTL_EXHAUSTED)
        if (packet.priority) return Decision.Relay
        if (packet.isBroadcast) return Decision.Relay
        if (packet.target == myId) return Decision.Drop(Reason.I_AM_TARGET)

        // Targeted packet for someone else: geo-routing gate when we know enough, else flood.
        val targetPos = target?.takeIf { nowMillis - it.lastSeenMillis in 0..TARGET_POSITION_MAX_AGE_MS }?.position
            ?: return Decision.Relay
        val me = myPosition ?: return Decision.Relay
        if (!packet.hasPosition) return Decision.Relay // origin unknown: cannot compare, flood
        val senderPos = LatLon(packet.latitude, packet.longitude)

        val mine = GeoMath.distanceMetres(me.lat, me.lon, targetPos.lat, targetPos.lon)
        val theirs = GeoMath.distanceMetres(senderPos.lat, senderPos.lon, targetPos.lat, targetPos.lon)
        return if (mine < theirs - CLOSER_BY_METRES) Decision.Relay else Decision.Drop(Reason.NOT_CLOSER)
    }
}
