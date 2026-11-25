package com.jarvis.assistant.core.ai.provider

import com.jarvis.assistant.core.ai.model.AiRequest
import com.jarvis.assistant.core.ai.model.AiResponse
import com.jarvis.assistant.core.common.model.Result

interface AiProvider {
    val type: com.jarvis.assistant.core.ai.model.AiProviderType
    suspend fun chat(request: AiRequest): Result<AiResponse>
}