package com.jarvis.assistant.core.ai.provider

import com.jarvis.assistant.core.ai.model.AiMessage
import com.jarvis.assistant.core.ai.model.AiProviderType
import com.jarvis.assistant.core.ai.model.AiRequest
import com.jarvis.assistant.core.ai.model.AiResponse
import com.jarvis.assistant.core.common.model.AppError
import com.jarvis.assistant.core.common.model.Result
import com.jarvis.assistant.core.network.api.GeminiService
import com.jarvis.assistant.core.network.dto.gemini.GeminiContent
import com.jarvis.assistant.core.network.dto.gemini.GeminiPart
import com.jarvis.assistant.core.network.dto.gemini.GeminiRequest

class GeminiProvider(
    private val service: GeminiService,
    private val apiKey: String,
    private val model: String
) : AiProvider {
    override val type: AiProviderType = AiProviderType.GEMINI

    override suspend fun chat(request: AiRequest): Result<AiResponse> {
        return try {
            val contents = request.messages.map { msg ->
                GeminiContent(listOf(GeminiPart(msg.content)))
            }
            val res = service.generate(model, apiKey, GeminiRequest(contents))
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return Result.Failure(AppError.Unknown("Boş yanıt"))
            Result.Success(AiResponse(text, type.id, model))
        } catch (e: Exception) {
            Result.Failure(AppError.Network(e.message ?: "Ağ hatası"))
        }
    }
}