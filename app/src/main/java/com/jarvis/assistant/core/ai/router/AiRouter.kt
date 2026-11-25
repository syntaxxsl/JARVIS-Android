package com.jarvis.assistant.core.ai.router

import com.jarvis.assistant.core.ai.model.AiRequest
import com.jarvis.assistant.core.ai.model.AiResponse
import com.jarvis.assistant.core.ai.provider.AiProvider
import com.jarvis.assistant.core.common.model.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AiRouter(private val providers: Map<String, AiProvider>) {
    private val _activeProvider = MutableStateFlow<String>("offline")
    val activeProvider: StateFlow<String> = _activeProvider

    fun setActiveProvider(id: String) {
        if (providers.containsKey(id)) _activeProvider.value = id
    }

    suspend fun chat(request: AiRequest): Result<AiResponse> {
        val provider = providers[_activeProvider.value] ?: providers["offline"]!!
        val result = provider.chat(request)
        if (result is Result.Failure) {
            val fallback = providers["offline"]
            if (fallback != null && _activeProvider.value != "offline") {
                return fallback.chat(request)
            }
        }
        return result
    }

    fun availableProviders(): List<String> = providers.keys.toList()
}