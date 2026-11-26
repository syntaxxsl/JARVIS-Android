package com.jarvis.assistant.core.knowledge

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class KnowledgeDocument(
    val id: String,
    val title: String,
    val text: String,
    val updatedAt: Long
)

object KnowledgeStore {
    private const val PREFS = "jarvis_knowledge_v1"
    private const val KEY_DOCS = "documents"
    private const val MAX_DOCUMENTS = 20
    private const val MAX_CHARS_PER_DOCUMENT = 20_000

    private fun prefs(context: Context) = runCatching {
        val master = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context.applicationContext, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun list(context: Context): List<KnowledgeDocument> {
        val raw = prefs(context)?.getString(KEY_DOCS, "[]") ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id")
                val title = item.optString("title")
                val text = item.optString("text")
                if (id.isBlank() || title.isBlank() || text.isBlank()) null
                else KnowledgeDocument(id, title, text, item.optLong("updatedAt"))
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, title: String, content: String): Boolean {
        val cleanTitle = title.trim().take(180)
        val cleanText = content.trim().take(MAX_CHARS_PER_DOCUMENT)
        if (cleanTitle.isBlank() || cleanText.isBlank()) return false
        val storage = prefs(context) ?: return false
        val docs = list(context).filterNot { it.title.equals(cleanTitle, true) }.toMutableList()
        docs.add(0, KnowledgeDocument(UUID.randomUUID().toString(), cleanTitle, cleanText, System.currentTimeMillis()))
        val array = JSONArray()
        docs.take(MAX_DOCUMENTS).forEach { doc ->
            array.put(JSONObject().put("id", doc.id).put("title", doc.title)
                .put("text", doc.text).put("updatedAt", doc.updatedAt))
        }
        return runCatching { storage.edit().putString(KEY_DOCS, array.toString()).commit() }.getOrDefault(false)
    }

    fun delete(context: Context, id: String): Boolean {
        val storage = prefs(context) ?: return false
        val array = JSONArray()
        list(context).filterNot { it.id == id }.forEach { doc ->
            array.put(JSONObject().put("id", doc.id).put("title", doc.title)
                .put("text", doc.text).put("updatedAt", doc.updatedAt))
        }
        return runCatching { storage.edit().putString(KEY_DOCS, array.toString()).commit() }.getOrDefault(false)
    }

    fun search(context: Context, query: String, limit: Int = 3): List<Pair<KnowledgeDocument, String>> {
        val queryTokens = tokenize(query).distinct().take(24)
        if (queryTokens.isEmpty()) return emptyList()
        val docs = list(context)
        if (docs.isEmpty()) return emptyList()
        data class Chunk(val doc: KnowledgeDocument, val text: String, val tokens: List<String>)
        val chunks = docs.flatMap { doc ->
            val paragraphs = doc.text.split(Regex("\\n\\s*\\n"))
                .filter { it.isNotBlank() }
            val source = if (paragraphs.isEmpty()) listOf(doc.text) else paragraphs
            val pieces = mutableListOf<String>()
            source.forEach { paragraph ->
                if (paragraph.length <= 1100) pieces.add(paragraph)
                else paragraph.chunked(900).forEach { pieces.add(it) }
            }
            pieces.map { text -> Chunk(doc, text, tokenize(text)) }
        }.filter { it.tokens.isNotEmpty() }
        if (chunks.isEmpty()) return emptyList()

        val averageLength = chunks.map { it.tokens.size }.average().coerceAtLeast(1.0)
        val documentFrequency = queryTokens.associateWith { token ->
            chunks.count { token in it.tokens }
        }
        val scored = chunks.mapNotNull { chunk ->
            var score = 0.0
            queryTokens.forEach { token ->
                val frequency = chunk.tokens.count { it == token }
                if (frequency > 0) {
                    val df = documentFrequency[token] ?: 0
                    val idf = kotlin.math.ln(1.0 + (chunks.size - df + 0.5) / (df + 0.5))
                    val lengthNorm = frequency * 2.2 /
                        (frequency + 1.2 * (0.25 + 0.75 * chunk.tokens.size / averageLength))
                    score += idf * lengthNorm
                    if (token in tokenize(chunk.doc.title)) score += 1.1
                }
            }
            if (score <= 0.0) null else Triple(chunk, score, queryTokens.count { it in chunk.tokens })
        }.sortedByDescending { it.second }

        return scored.distinctBy { it.first.doc.id }.take(limit).map { (chunk, score, matched) ->
            val excerpt = chunk.text.trim().take(1100)
            chunk.doc to excerpt
        }
    }

    fun topConcepts(context: Context, limit: Int = 12): List<Pair<String, Int>> {
        val stopWords = setOf("ve", "veya", "ama", "fakat", "icin", "ile", "bir", "bu", "su", "şu",
            "olan", "olarak", "daha", "gibi", "kadar", "cok", "az", "the", "and", "for", "with",
            "from", "that", "this", "are", "was", "were", "have", "has", "not")
        return list(context).flatMap { tokenize(it.title + " " + it.text) }
            .filter { it.length >= 4 && it !in stopWords }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key to it.value }
    }

    private fun tokenize(value: String): List<String> = normalize(value)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 }

    private fun normalize(value: String): String = value.lowercase(Locale("tr", "TR"))
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
}
