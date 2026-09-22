package com.firefly.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE senderId = :senderId")
    suspend fun get(senderId: Int): MemberEntity?

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Query("DELETE FROM members")
    suspend fun clear()
}
