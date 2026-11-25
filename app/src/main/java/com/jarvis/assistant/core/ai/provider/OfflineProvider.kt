package com.jarvis.assistant.core.ai.provider

import com.jarvis.assistant.core.ai.model.AiProviderType
import com.jarvis.assistant.core.ai.model.AiRequest
import com.jarvis.assistant.core.ai.model.AiResponse
import com.jarvis.assistant.core.common.model.Result
import com.jarvis.assistant.core.ai.offline.OfflineCommandHandler

class OfflineProvider(private val handler: OfflineCommandHandler) : AiProvider {
    override val type: AiProviderType = AiProviderType.OFFLINE
    override suspend fun chat(request: AiRequest): Result<AiResponse> {
        val lastUser = request.messages.lastOrNull { it.role == "user" }?.content ?: ""
        val reply = handler.handle(lastUser)
        return Result.Success(AiResponse(reply, type.id, "offline"))
    }
}