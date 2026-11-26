package com.jarvis.assistant.core.research

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class ResearchWatch(val query: String, val updatedAt: Long, val sources: List<ResearchSource>)

object ResearchWatchStore {
    private const val PREFS = "jarvis_research_watch"
    private const val KEY = "watches"

    private fun prefs(context: Context) = runCatching {
        val master = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context.applicationContext, PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun list(context: Context): List<ResearchWatch> {
        val raw = prefs(context)?.getString(KEY, "[]") ?: return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val query = row.optString("query")
                if (query.isBlank()) return@mapNotNull null
                val items = row.optJSONArray("sources") ?: JSONArray()
                val sources = (0 until items.length()).mapNotNull { i ->
                    val item = items.optJSONObject(i) ?: return@mapNotNull null
                    ResearchSource(item.optString("title"), item.optString("summary"),
                        item.optString("url"), item.optString("sourceType"))
                }
                ResearchWatch(query, row.optLong("updatedAt"), sources)
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, query: String): Boolean {
        val clean = query.trim().take(180)
        if (clean.length < 3) return false
        val current = list(context).filterNot { it.query.equals(clean, true) }.toMutableList()
        current.add(0, ResearchWatch(clean, 0L, emptyList()))
        return write(context, current.take(10))
    }

    fun saveResults(context: Context, query: String, sources: List<ResearchSource>): Boolean {
        val current = list(context).toMutableList()
        val index = current.indexOfFirst { it.query.equals(query, true) }
        val entry = ResearchWatch(query, System.currentTimeMillis(), sources.take(16))
        if (index >= 0) current[index] = entry else current.add(0, entry)
        return write(context, current.take(10))
    }

    private fun write(context: Context, watches: List<ResearchWatch>): Boolean {
        val storage = prefs(context) ?: return false
        val array = JSONArray()
        watches.forEach { watch ->
            val sources = JSONArray()
            watch.sources.forEach { source ->
                sources.put(JSONObject().put("title", source.title).put("summary", source.summary)
                    .put("url", source.url).put("sourceType", source.sourceType))
            }
            array.put(JSONObject().put("query", watch.query).put("updatedAt", watch.updatedAt).put("sources", sources))
        }
        return runCatching { storage.edit().putString(KEY, array.toString()).commit() }.getOrDefault(false)
    }
}
