package com.firefly.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Field-test log (CLAUDE.md: first-class). One row per radio event.
 * Ring-buffered at [FieldLogRepository.MAX_ROWS]; exported as CSV from the Stats screen.
 */
@Entity(tableName = "packet_log", indices = [Index("ts")])
data class PacketLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    /** RX, DUP, FOREIGN, TX, RELAY, BATTERY, EVENT */
    val event: String,
    val type: Int? = null,
    val senderId: Int? = null,
    val seq: Int? = null,
    val hops: Int? = null,
    val ttl: Int? = null,
    val target: Int? = null,
    val code: Int? = null,
    val arg: Int? = null,
    val rssi: Int? = null,
    val latE6: Int? = null,
    val lonE6: Int? = null,
    val bytesHex: String? = null,
    /** Free text: battery level, degraded reason, crash summary. */
    val extra: String? = null,
) {
    companion object {
        const val RX = "RX"
        const val DUP = "DUP"
        const val FOREIGN = "FOREIGN"
        const val TX = "TX"
        const val RELAY = "RELAY"
        const val BATTERY = "BATTERY"
        const val EVENT = "EVENT"
    }
}
