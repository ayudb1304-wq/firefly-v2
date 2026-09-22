package com.firefly.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A group member we have heard from this session (ARCHITECTURE.md §4 `members`). */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val senderId: Int,
    val name: String?,
    val lat: Double?,
    val lon: Double?,
    /** Accuracy bucket 0–5 as defined in PROTOCOL.md §2. */
    val accuracy: Int,
    /** Wall-clock millis when we last received any packet from this sender. */
    val lastSeen: Long,
    val hops: Int,
    val rssi: Int,
)
