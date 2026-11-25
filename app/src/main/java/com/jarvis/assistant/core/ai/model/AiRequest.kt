package com.jarvis.assistant.core.ai.model

data class AiRequest(
    val messages: List<AiMessage>,
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 1024
)