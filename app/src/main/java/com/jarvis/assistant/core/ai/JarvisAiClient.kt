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

    suspend fun listModels(apiKey: String, baseUrl: String): List<String> {
        require(apiKey.isNotBlank()) { "Model listesini almak için API anahtarı gir." }
        val normalized = baseUrl.trim().trimEnd('/')
        val parsedUrl = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Geçerli bir API adresi gir.")
        val secureRemote = parsedUrl.scheme.equals("https", ignoreCase = true)
        val localLoopback = parsedUrl.scheme.equals("http", ignoreCase = true) &&
            parsedUrl.host?.lowercase() in setOf("localhost", "127.0.0.1")
        require(secureRemote || localLoopback) {
            "Uzak API adresleri HTTPS kullanmalı. HTTP yalnızca bu cihazdaki localhost servisi için açıktır."
        }
        val request = Request.Builder()
            .url("$normalized/models")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
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
                                    ?: ("Model listesi alınamadı (HTTP " + res.code + ")."))
                            )
                            return
                        }
                        val models = runCatching {
                            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                            (0 until data.length()).mapNotNull { index ->
                                data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
                            }.distinct().sorted()
                        }.getOrElse {
                            continuation.resumeWithException(IllegalStateException("Sağlayıcının model listesi yanıtı tanınamadı."))
                            return
                        }
                        if (models.isEmpty()) {
                            continuation.resumeWithException(IllegalStateException("Sağlayıcı kullanılabilir model döndürmedi."))
                        } else {
                            continuation.resume(models)
                        }
                    }
                }
            })
        }
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
            "Sen JARVIS'sin: Türkçe konuşan, sakin, profesyonel ve dikkatli bir Android kişisel asistansın. Önce isteği ve bağlamı sınıflandır; karmaşık işleri küçük adımlara ayır; verilen yerel belge alıntılarını yalnızca destekledikleri iddialar için kullan. Güncel web erişimin yoksa güncel bilgiye eriştiğini iddia etme. Kaynak, tarih, sürüm veya teknik ayrıntı uydurma; belirsizliği açıkça belirt. Birden fazla makul çözüm varsa artı-eksi yönlerini karşılaştır ve en güvenli uygulanabilir yolu öner. Kod verirken mevcut proje mimarisine uy, hataları ve kenar durumlarını kontrol et. Kullanıcı onayı olmadan harici işlem yaptığını iddia etme."))
        var remainingContextChars = 18_000
        val selectedMessages = mutableListOf<Pair<String, String>>()
        messages.takeLast(24).asReversed().forEach { (role, content) ->
            if (remainingContextChars > 0) {
                val bounded = content.take(remainingContextChars)
                if (bounded.isNotBlank()) selectedMessages.add(role to bounded)
                remainingContextChars -= bounded.length
            }
        }
        selectedMessages.asReversed().forEach { (role, content) ->
            array.put(JSONObject().put("role", role).put("content", content))
        }
        val payload = JSONObject()
            .put("model", model.trim())
            .put("messages", array)
            .put("temperature", 0.7)
            .put("max_tokens", 1600)
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
