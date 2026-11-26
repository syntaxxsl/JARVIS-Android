package com.jarvis.assistant.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object JarvisAiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
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

    suspend fun chat(
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        baseUrl: String
    ): String {
        require(apiKey.isNotBlank()) { "Önce AI Router bölümünden API anahtarı gir." }
        require(model.isNotBlank()) { "Seçilen sağlayıcı için model adını gir." }
        val normalized = baseUrl.trim().trimEnd('/')
        val parsedUrl = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Geçerli bir API adresi gir.")
        val secureRemote = parsedUrl.scheme.equals("https", ignoreCase = true)
        val localLoopback = parsedUrl.scheme.equals("http", ignoreCase = true) &&
            parsedUrl.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
        require(secureRemote || localLoopback) {
            "Uzak API adresleri HTTPS kullanmalı. HTTP yalnızca bu cihazdaki localhost servisi için açıktır."
        }
        val array = JSONArray()
        array.put(JSONObject().put("role", "system").put("content",
            "Sen JARVIS'sin. Türkçe konuşan, sakin, profesyonel, dikkatli bir Android kişisel asistansın. Kullanıcı onayı olmadan harici işlem yaptığını iddia etme. Bilmediğin konuda uydurma."))
        messages.takeLast(20).forEach { (role, content) ->
            array.put(JSONObject().put("role", role).put("content", content))
        }
        val payload = JSONObject()
            .put("model", model.trim())
            .put("messages", array)
            .put("temperature", 0.7)
            .put("max_tokens", 1200)
        val request = Request.Builder()
            .url("$normalized/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string().orEmpty()
                        if (!continuation.isActive) return
                        if (!res.isSuccessful) {
                            val detail = runCatching {
                                JSONObject(body).optJSONObject("error")?.optString("message")
                            }.getOrNull()
                            continuation.resumeWithException(
                                IllegalStateException(detail?.takeIf { it.isNotBlank() }
                                    ?: "AI servisi hata verdi (HTTP ${res.code}).")
                            )
                            return
                        }
                        val answer = runCatching {
                            JSONObject(body).optJSONArray("choices")
                                ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                        }.getOrNull()
                        val text = answer?.takeIf { it.isNotBlank() }
                        if (text == null) {
                            continuation.resumeWithException(IllegalStateException("AI boş yanıt döndürdü."))
                        } else {
                            continuation.resume(text)
                        }
                    }
                }
            })
        }
    }
}
