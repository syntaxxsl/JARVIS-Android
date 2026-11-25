package com.jarvis.assistant.core.network.dto.openai

import com.google.gson.annotations.SerializedName

data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 1024
)

data class OpenAiMessageDto(
    val role: String,
    val content: String
)

data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>?
)

data class OpenAiChoice(
    val message: OpenAiMessageDto?
)