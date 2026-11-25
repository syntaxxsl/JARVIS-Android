package com.jarvis.assistant.core.network.api

import com.jarvis.assistant.core.network.dto.openai.OpenAiChatRequest
import com.jarvis.assistant.core.network.dto.openai.OpenAiChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiService {
    @POST("v1/chat/completions")
    suspend fun chat(@Body request: OpenAiChatRequest): OpenAiChatResponse
}