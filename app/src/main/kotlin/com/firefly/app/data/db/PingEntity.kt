package com.firefly.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One codebook message, sent or received (ARCHITECTURE.md §4 `pings`). */
@Entity(tableName = "pings", indices = [Index("ts"), Index("direction", "ts")])
data class PingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seq: Int,
    /** Originator of the message. For outgoing pings this is me. */
    val senderId: Int,
    /** Recipient; 0xFFFF = whole group. */
    val target: Int,
    val code: Int,
    val arg: Int,
    /** Sender's position when sent, if known. */
    val lat: Double?,
    val lon: Double?,
    val direction: Int,
    val status: Int,
    val ts: Long,
    val ackCount: Int = 0,
    val hops: Int = 0,
    val rssi: Int = 0,
) {
    companion object {
        const val OUT = 0
        const val IN = 1

        const val SENT = 0
        const val SEEN = 1
        const val RECEIVED = 2
    }
}
