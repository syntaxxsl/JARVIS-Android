package com.jarvis.assistant.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jarvis.assistant.core.data.local.dao.ChatMessageDao
import com.jarvis.assistant.core.data.local.dao.MemoryDao
import com.jarvis.assistant.core.data.local.dao.TaskDao
import com.jarvis.assistant.core.data.local.entity.ChatMessageEntity
import com.jarvis.assistant.core.data.local.entity.MemoryEntity
import com.jarvis.assistant.core.data.local.entity.TaskEntity

@Database(
    entities = [ChatMessageEntity::class, MemoryEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jarvis_db"
                ).build().also { instance = it }
            }
        }
    }
}
