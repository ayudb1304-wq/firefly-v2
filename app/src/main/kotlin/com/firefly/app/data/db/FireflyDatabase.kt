package com.firefly.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MemberEntity::class, PingEntity::class, PacketLogEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class FireflyDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun pingDao(): PingDao
    abstract fun packetLogDao(): PacketLogDao

    companion object {
        const val NAME = "firefly.db"
    }
}
