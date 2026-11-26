package com.jarvis.assistant.core.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class ResearchSource(val title: String, val summary: String, val url: String, val sourceType: String)

object ResearchEngine {
    suspend fun search(query: String): List<ResearchSource> = withContext(Dispatchers.IO) {
        require(query.trim().length >= 3) { "Araştırma için en az 3 karakter gir." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val sources = mutableListOf<ResearchSource>()

        runCatching {
            val json = getJson("https://tr.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1&srlimit=4")
            val items = json.optJSONObject("query")?.optJSONArray("search")
            for (i in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(i) ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                val summary = cleanMarkup(item.optString("snippet")).take(700)
                val page = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
                sources.add(ResearchSource(title, summary, "https://tr.wikipedia.org/wiki/$page", "Wikipedia TR"))
            }
        }

        runCatching {
            val json = getJson("https://api.crossref.org/works?query=$encoded&rows=4&select=title,author,published,DOI,URL,abstract")
            val items = json.optJSONObject("message")?.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(i) ?: continue
                val title = item.optJSONArray("title")?.optString(0)?.takeIf { it.isNotBlank() } ?: continue
                val doi = item.optString("DOI")
                val url = item.optString("URL").ifBlank { if (doi.isNotBlank()) "https://doi.org/$doi" else "" }
                if (url.isBlank()) continue
                val authors = item.optJSONArray("author")
                val author = authors?.optJSONObject(0)?.let {
                    listOf(it.optString("given"), it.optString("family")).filter(String::isNotBlank).joinToString(" ")
                }.orEmpty()
                val abstract = cleanMarkup(item.optString("abstract")).take(700)
                val summary = listOf(author, abstract).filter(String::isNotBlank).joinToString(" • ")
                    .ifBlank { "Crossref kayıtlı akademik yayın metaverisi." }
                sources.add(ResearchSource(title, summary, url, "Crossref"))
            }
        }

        sources.distinctBy { it.url }.take(8)
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "JARVIS-Android/1.0 (research module)")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Kaynak servisi HTTP $code yanıtı verdi.")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanMarkup(value: String): String = value
        .replace(Regex("<[^>]*>"), " ")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}
