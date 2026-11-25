package com.jarvis.assistant.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jarvis.assistant.core.data.local.entity.ChatMessageEntity
import com.jarvis.assistant.core.data.local.entity.MemoryEntity
import com.jarvis.assistant.core.data.local.entity.TaskEntity
import javax.annotation.PreDestroy
import javax.inject.Singleton

@Database(entities = [ChatMessageEntity::class, MemoryEntity::class, TaskEntity::class], version = 1, exportSchema = false)
@Singleton
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): com.jarvis.assistant.core.data.local.dao.ChatMessageDao
    abstract fun memoryDao(): com.jarvis.assistant.core.data.local.dao.MemoryDao
    abstract fun taskDao(): com.jarvis.assistant.core.data.local.dao.TaskDao

    @PreDestroy
    open fun onClose() {
        close()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jarvis_db"
                ).build()
                INSTANCE = database
                database
            }
        }
    }
}