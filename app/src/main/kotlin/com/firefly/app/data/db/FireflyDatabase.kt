package com.firefly.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MemberEntity::class, PingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FireflyDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun pingDao(): PingDao

    companion object {
        const val NAME = "firefly.db"
    }
}
