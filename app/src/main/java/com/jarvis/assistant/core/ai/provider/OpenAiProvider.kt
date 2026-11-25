package com.jarvis.assistant.core.ai.provider

import com.jarvis.assistant.core.ai.model.AiMessage
import com.jarvis.assistant.core.ai.model.AiProviderType
import com.jarvis.assistant.core.ai.model.AiRequest
import com.jarvis.assistant.core.ai.model.AiResponse
import com.jarvis.assistant.core.common.model.AppError
import com.jarvis.assistant.core.common.model.Result
import com.jarvis.assistant.core.network.api.OpenAiService
import com.jarvis.assistant.core.network.dto.openai.OpenAiChatRequest
import com.jarvis.assistant.core.network.dto.openai.OpenAiMessageDto

class OpenAiProvider(
    private val service: OpenAiService,
    private val providerType: AiProviderType,
    private val apiKey: String,
    private val baseUrl: String
) : AiProvider {
    override val type: AiProviderType = providerType

    override suspend fun chat(request: AiRequest): Result<AiResponse> {
        return try {
            val dto = OpenAiChatRequest(
                model = request.model,
                messages = request.messages.map { OpenAiMessageDto(it.role, it.content) },
                temperature = request.temperature,
                maxTokens = request.maxTokens
            )
            val res = service.chat(dto)
            val content = res.choices?.firstOrNull()?.message?.content
                ?: return Result.Failure(AppError.Unknown("Boş yanıt"))
            Result.Success(AiResponse(content, providerType.id, request.model))
        } catch (e: Exception) {
            Result.Failure(AppError.Network(e.message ?: "Ağ hatası"))
        }
    }
}