package com.jarvis.assistant.core.repository

import com.jarvis.assistant.core.data.local.AppDatabase
import com.jarvis.assistant.core.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.first

class ChatRepository(private val database: AppDatabase) {
    suspend fun insertMessage(
        conversationId: String,
        role: String,
        content: String,
        provider: String? = null
    ) {
        val message = ChatMessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            provider = provider
        )
        database.chatMessageDao().insert(message)
    }

    suspend fun getMessages(conversationId: String): List<ChatMessageEntity> {
        return database.chatMessageDao().observe(conversationId).first()
    }
}
