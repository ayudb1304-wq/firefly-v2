package com.firefly.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketLogDao {
    @Insert
    suspend fun insertAll(rows: List<PacketLogEntity>)

    @Query("SELECT COUNT(*) FROM packet_log")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM packet_log")
    suspend fun count(): Int

    @Query("SELECT MIN(ts) FROM packet_log")
    suspend fun oldestTs(): Long?

    /** Page through in id order for streaming export. */
    @Query("SELECT * FROM packet_log WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<PacketLogEntity>

    @Query("DELETE FROM packet_log WHERE id IN (SELECT id FROM packet_log ORDER BY id LIMIT :n)")
    suspend fun deleteOldest(n: Int)

    @Query("SELECT COUNT(DISTINCT senderId) FROM packet_log WHERE event = 'RX' AND ts >= :sinceTs")
    fun observeUniqueSenders(sinceTs: Long): Flow<Int>

    @Query("DELETE FROM packet_log")
    suspend fun clear()
}
