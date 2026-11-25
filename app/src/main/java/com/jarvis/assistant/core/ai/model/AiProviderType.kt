package com.jarvis.assistant.core.ai.model

enum class AiProviderType(val id: String, val displayName: String) {
    OPENAI("openai", "OpenAI"),
    GEMINI("gemini", "Gemini"),
    OPENROUTER("openrouter", "OpenRouter"),
    GROQ("groq", "Groq"),
    HUGGINGFACE("huggingface", "Hugging Face"),
    CUSTOM("custom", "Özel (OpenAI uyumlu)"),
    OFFLINE("offline", "Çevrimdışı")
}