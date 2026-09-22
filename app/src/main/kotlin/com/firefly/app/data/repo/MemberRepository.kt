package com.firefly.app.data.repo

import com.firefly.app.core.protocol.Packet
import com.firefly.app.data.db.MemberDao
import com.firefly.app.data.db.MemberEntity
import kotlinx.coroutines.flow.Flow

/** Group members heard this session. Written by the service, read by the map. */
class MemberRepository(private val dao: MemberDao) {

    val members: Flow<List<MemberEntity>> = dao.observeAll()

    suspend fun onPacket(packet: Packet, rssi: Int, nowMillis: Long) {
        val existing = dao.get(packet.senderId)
        val hasPos = packet.hasPosition
        dao.upsert(
            MemberEntity(
                senderId = packet.senderId,
                name = packet.name ?: existing?.name,
                lat = if (hasPos) packet.latitude else existing?.lat,
                lon = if (hasPos) packet.longitude else existing?.lon,
                accuracy = if (hasPos) packet.accuracyBucket else existing?.accuracy ?: 0,
                lastSeen = nowMillis,
                hops = packet.hops,
                rssi = rssi,
            ),
        )
    }

    suspend fun clear() = dao.clear()
}
