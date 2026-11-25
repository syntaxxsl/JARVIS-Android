package com.jarvis.assistant.core.repository

import com.jarvis.assistant.core.ai.model.AiRequest
import com.jarvis.assistant.core.ai.model.AiResponse
import com.jarvis.assistant.core.common.model.Result
import com.jarvis.assistant.core.data.local.AppDatabase
import com.jarvis.assistant.core.data.local.entity.ChatMessageEntity

class ChatRepository(private val database: AppDatabase) {
    suspend fun insertMessage(conversationId: String, role: String, content: String, provider: String? = null) {
        val msg = ChatMessageEntity(conversationId = conversationId, role = role, content = content, provider = provider)
        database.chatMessageDao().insert(msg)
    }

    suspend fun getMessages(conversationId: String): List<ChatMessageEntity> {
        return database.chatMessageDao().observe(conversationId).firstOrEmpty()
    }
}