package com.firefly.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MemberEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FireflyDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao

    companion object {
        const val NAME = "firefly.db"
    }
}
