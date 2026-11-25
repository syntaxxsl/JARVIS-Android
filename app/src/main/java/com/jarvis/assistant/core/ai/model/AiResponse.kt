package com.jarvis.assistant.core.ai.model

data class AiResponse(
    val content: String,
    val provider: String,
    val model: String
)