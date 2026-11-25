package com.jarvis.assistant.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object JarvisAiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun chatWithFallback(
        primaryKey: String,
        primaryModel: String,
        primaryUrl: String,
        backupKey: String,
        backupModel: String,
        backupUrl: String,
        messages: List<Pair<String, String>>
    ): String {
        var primaryError: Exception? = null
        if (primaryKey.isNotBlank()) {
            try {
                return chat(primaryKey, primaryModel, messages, primaryUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                primaryError = e
            }
        }
        if (backupKey.isNotBlank()) {
            try {
                val response = chat(backupKey, backupModel, messages, backupUrl)
                return "Yedek AI sağlayıcısına otomatik geçiş yapıldı.\n\n$response"
            } catch (backupError: CancellationException) {
                throw backupError
            } catch (backupError: Exception) {
                val first = primaryError?.message ?: "Birincil sağlayıcı anahtarı ayarlanmamış."
                throw IllegalStateException("Birincil sağlayıcı: $first\nYedek sağlayıcı: ${backupError.message ?: "bağlantı hatası"}")
            }
        }
        throw IllegalStateException(primaryError?.message ?: "AI sağlayıcısı yapılandırılmamış. Yerel komutlar kullanılabilir.")
    }

    suspend fun chat(apiKey: String, model: String, messages: List<Pair<String, String>>, baseUrl: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "Önce Kontrol bölümünden API anahtarı kaydet." }
            val normalized = baseUrl.trim().trimEnd('/')
            require(normalized.startsWith("https://")) { "API adresi https:// ile başlamalı." }
            val array = JSONArray()
            array.put(JSONObject().put("role", "system").put("content",
                "Sen JARVIS'sin. Türkçe konuşan, sakin, profesyonel, dikkatli bir Android kişisel asistansın. Kullanıcı onayı olmadan harici işlem yaptığını iddia etme. Bilmediğin konuda uydurma."))
            messages.takeLast(20).forEach { (role, content) ->
                array.put(JSONObject().put("role", role).put("content", content))
            }
            val payload = JSONObject()
                .put("model", model.ifBlank { "openrouter/free" })
                .put("messages", array)
                .put("temperature", 0.7)
                .put("max_tokens", 1200)
            val request = Request.Builder()
                .url("$normalized/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "AI servisi hata verdi (HTTP ${response.code}).")
                }
                val text = JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                text?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("AI boş yanıt döndürdü.")
            }
        }
}