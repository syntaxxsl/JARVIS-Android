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
        val tokens = normalize(query).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }.distinct().take(18)
        if (tokens.isEmpty()) return emptyList()
        return list(context).mapNotNull { doc ->
            val normalized = normalize(doc.text)
            val title = normalize(doc.title)
            val score = tokens.sumOf { token ->
                (if (title.contains(token)) 4 else 0) +
                    Regex(Regex.escape(token)).findAll(normalized).count().coerceAtMost(8)
            }
            if (score == 0) null else {
                val first = tokens.firstOrNull { normalized.contains(it) }
                val position = first?.let(normalized::indexOf)?.coerceAtLeast(0) ?: 0
                val start = (position - 280).coerceAtLeast(0)
                val end = (position + 900).coerceAtMost(doc.text.length)
                doc to doc.text.substring(start, end).trim()
            }
        }.sortedByDescending { (doc, _) ->
            val haystack = normalize(doc.title + " " + doc.text)
            tokens.sumOf { token -> haystack.windowed(token.length).count { it == token }.coerceAtMost(8) }
        }.take(limit)
    }

    private fun normalize(value: String): String = value.lowercase(Locale("tr", "TR"))
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
}
