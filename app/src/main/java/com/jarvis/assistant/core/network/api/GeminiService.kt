package com.jarvis.assistant.core.network.api

import com.jarvis.assistant.core.network.dto.gemini.GeminiRequest
import com.jarvis.assistant.core.network.dto.gemini.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}