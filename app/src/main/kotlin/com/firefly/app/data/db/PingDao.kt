package com.firefly.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PingDao {
    @Query("SELECT * FROM pings ORDER BY ts DESC LIMIT 500")
    fun observeTimeline(): Flow<List<PingEntity>>

    @Insert
    suspend fun insert(ping: PingEntity): Long

    @Query("SELECT * FROM pings WHERE id = :id")
    suspend fun get(id: Long): PingEntity?

    @Query("SELECT * FROM pings WHERE direction = ${PingEntity.OUT} AND ts >= :sinceTs")
    suspend fun outgoingSince(sinceTs: Long): List<PingEntity>

    @Query("UPDATE pings SET status = ${PingEntity.SEEN}, ackCount = ackCount + 1 WHERE id = :id")
    suspend fun markSeen(id: Long)

    @Query("DELETE FROM pings")
    suspend fun clear()
}
