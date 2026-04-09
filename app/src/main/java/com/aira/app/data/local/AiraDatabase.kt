package com.aira.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CommandHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AiraDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
}

