package com.firefly.app.data.repo

import android.util.Log
import com.firefly.app.core.messaging.AckMatcher
import com.firefly.app.core.protocol.AccuracyBucket
import com.firefly.app.core.protocol.Codebook
import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.PacketCodec
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.data.db.PingDao
import com.firefly.app.data.db.PingEntity
import com.firefly.app.location.Fix
import com.firefly.app.radio.SeqCounter
import com.firefly.app.radio.TxQueue
import com.firefly.app.radio.TxRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Codebook messages in and out (PRD C1/C2). Builds packets, records them, and
 * hands them to the radio via [TxQueue]. The service calls the `on*` methods.
 */
class PingRepository(
    private val dao: PingDao,
    private val tx: TxQueue,
    private val seq: SeqCounter,
    private val session: () -> GroupSession?,
    private val fix: () -> Fix?,
    private val scope: CoroutineScope,
) {
    val timeline: Flow<List<PingEntity>> = dao.observeTimeline()

    private val _sosActive = MutableStateFlow(false)
    /** PRD F1 / PROTOCOL.md §4: while active, HELP is re-sent every 60 s (each send is ×5 over 10 s). */
    val sosActive: StateFlow<Boolean> = _sosActive.asStateFlow()
    private var sosJob: Job? = null

    fun startSos() {
        if (sosJob?.isActive == true) return
        _sosActive.value = true
        sosJob = scope.launch {
            while (isActive) {
                send(Codebook.HELP, 0, Protocol.TARGET_BROADCAST)
                delay(SOS_REPEAT_MS)
            }
        }
    }

    fun stopSos() {
        sosJob?.cancel(); sosJob = null
        _sosActive.value = false
    }

    /** Send a codebook message. Returns the stored row, or null if not in a group or the arg is invalid. */
    suspend fun send(code: Int, arg: Int, target: Int): PingEntity? {
        val s = session() ?: return null
        val entry = Codebook.entry(code) ?: return null
        if (!Codebook.isValidArg(code, arg)) return null
        val now = System.currentTimeMillis()
        val f = fix()
        val packet = Packet(
            type = Protocol.Type.PING,
            groupId = s.groupId,
            senderId = s.senderId,
            seq = seq.next(),
            ttl = Codebook.ttl(code),
            hops = 0,
            latE6 = f?.let { Packet.toE6(it.lat) } ?: 0,
            lonE6 = f?.let { Packet.toE6(it.lon) } ?: 0,
            priority = entry.priority,
            ackRequested = entry.wantsAck,
            accuracyBucket = AccuracyBucket.fromMetres(f?.accuracyMetres),
            code = code,
            arg = arg,
            target = target,
            ts = Packet.tsByte(now),
        )
        val row = PingEntity(
            seq = packet.seq, senderId = s.senderId, target = target, code = code, arg = arg,
            lat = f?.lat, lon = f?.lon, direction = PingEntity.OUT, status = PingEntity.SENT, ts = now,
        )
        val id = dao.insert(row)
        tx.enqueue(TxRequest(PacketCodec.encode(packet), Codebook.repeats(code), Codebook.spacingMillis(code), entry.priority, "PING $code"))
        Log.i(TAG, "TX ping code=$code arg=$arg target=${"%04X".format(target)} seq=${packet.seq}")
        return row.copy(id = id)
    }

    /** Record an incoming PING addressed to me or the group. */
    suspend fun onIncoming(packet: Packet, rssi: Int, nowMillis: Long): PingEntity {
        val row = PingEntity(
            seq = packet.seq, senderId = packet.senderId, target = packet.target, code = packet.code, arg = packet.arg,
            lat = if (packet.hasPosition) packet.latitude else null, lon = if (packet.hasPosition) packet.longitude else null,
            direction = PingEntity.IN, status = PingEntity.RECEIVED, ts = nowMillis, hops = packet.hops, rssi = rssi,
        )
        val id = dao.insert(row)
        return row.copy(id = id)
    }

    /** Reply to a PING that asked for an ACK (PROTOCOL.md §3 type 0x3). */
    fun sendAck(forPacket: Packet, nowMillis: Long) {
        val s = session() ?: return
        val f = fix()
        val ack = Packet(
            type = Protocol.Type.ACK,
            groupId = s.groupId,
            senderId = s.senderId,
            seq = seq.next(),
            ttl = Protocol.Ttl.ACK,
            hops = 0,
            latE6 = f?.let { Packet.toE6(it.lat) } ?: 0,
            lonE6 = f?.let { Packet.toE6(it.lon) } ?: 0,
            accuracyBucket = AccuracyBucket.fromMetres(f?.accuracyMetres),
            code = Codebook.ACK,
            arg = forPacket.seq and 0xFF,
            target = forPacket.senderId,
            ts = Packet.tsByte(nowMillis),
        )
        tx.enqueue(TxRequest(PacketCodec.encode(ack), Codebook.repeats(Codebook.ACK), Codebook.spacingMillis(Codebook.ACK), label = "ACK"))
    }

    /** An ACK addressed to me: flip the matching outgoing ping to SEEN. Returns the ping id, or null. */
    suspend fun onAck(packet: Packet, nowMillis: Long): Long? {
        val candidates = dao.outgoingSince(nowMillis - AckMatcher.WINDOW_MS)
            .map { AckMatcher.Outgoing(it.id, it.seq, it.target, it.ts) }
        val id = AckMatcher.match(candidates, packet.senderId, packet.arg, nowMillis) ?: return null
        dao.markSeen(id)
        Log.i(TAG, "ACK from ${"%04X".format(packet.senderId)} matched ping $id")
        return id
    }

    /** Broadcast my display name (PRD A2). */
    fun sendName(name: String) {
        val s = session() ?: return
        val packet = Packet(
            type = Protocol.Type.NAME, groupId = s.groupId, senderId = s.senderId, seq = seq.next(),
            ttl = Protocol.Ttl.NAME, hops = 0, name = name, ts = Packet.tsByte(System.currentTimeMillis()),
        )
        tx.enqueue(TxRequest(PacketCodec.encode(packet), repeats = 1, spacingMillis = 0, label = "NAME"))
        Log.i(TAG, "TX name '$name' seq=${packet.seq}")
    }

    suspend fun clear() { stopSos(); dao.clear() }

    private companion object {
        const val TAG = "Firefly/Ping"
        const val SOS_REPEAT_MS = 60_000L
    }
}
