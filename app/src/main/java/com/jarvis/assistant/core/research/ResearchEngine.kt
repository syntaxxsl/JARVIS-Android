package com.jarvis.assistant.core.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class ResearchSource(val title: String, val summary: String, val url: String, val sourceType: String)

object ResearchEngine {
    suspend fun search(query: String): List<ResearchSource> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        require(cleanQuery.length >= 3) { "Araştırma için en az 3 karakter gir." }
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

        coroutineScope {
            val wikipedia = async { runCatching { searchWikipedia(encoded) }.getOrDefault(emptyList()) }
            val crossref = async { runCatching { searchCrossref(encoded) }.getOrDefault(emptyList()) }
            val openAlex = async { runCatching { searchOpenAlex(encoded) }.getOrDefault(emptyList()) }
            val arxiv = async { runCatching { searchArxiv(encoded) }.getOrDefault(emptyList()) }
            (wikipedia.await() + crossref.await() + openAlex.await() + arxiv.await())
                .distinctBy { it.url.lowercase() }
                .take(16)
        }
    }

    private fun searchWikipedia(query: String): List<ResearchSource> {
        val json = getJson("https://tr.wikipedia.org/w/api.php?action=query&list=search&srsearch=$query&format=json&utf8=1&srlimit=4")
        val items = json.optJSONObject("query")?.optJSONArray("search")
        return (0 until (items?.length() ?: 0)).mapNotNull { index ->
            val item = items?.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val page = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
            ResearchSource(title, cleanMarkup(item.optString("snippet")).take(700),
                "https://tr.wikipedia.org/wiki/$page", "Wikipedia TR • Genel başvuru")
        }
    }

    private fun searchCrossref(query: String): List<ResearchSource> {
        val json = getJson("https://api.crossref.org/works?query=$query&rows=4&select=title,author,published,DOI,URL,abstract")
        val items = json.optJSONObject("message")?.optJSONArray("items")
        return (0 until (items?.length() ?: 0)).mapNotNull { index ->
            val item = items?.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optJSONArray("title")?.optString(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val doi = item.optString("DOI")
            val url = item.optString("URL").ifBlank { if (doi.isNotBlank()) "https://doi.org/$doi" else "" }
            if (url.isBlank()) return@mapNotNull null
            val author = item.optJSONArray("author")?.optJSONObject(0)?.let {
                listOf(it.optString("given"), it.optString("family")).filter(String::isNotBlank).joinToString(" ")
            }.orEmpty()
            val year = item.optJSONObject("published")?.optJSONArray("date-parts")
                ?.optJSONArray(0)?.optInt(0)?.takeIf { it > 0 }?.toString().orEmpty()
            val abstract = cleanMarkup(item.optString("abstract")).take(700)
            val summary = listOf(author, year, abstract).filter(String::isNotBlank).joinToString(" • ")
                .ifBlank { "Crossref bibliyografik yayın metaverisi; tam metin olduğu varsayılmamalıdır." }
            ResearchSource(title, summary, url, "Crossref • Yayın metaverisi")
        }
    }

    private fun searchOpenAlex(query: String): List<ResearchSource> {
        val json = getJson("https://api.openalex.org/works?search=$query&per-page=4&select=id,doi,display_name,publication_year,cited_by_count")
        val items = json.optJSONArray("results")
        return (0 until (items?.length() ?: 0)).mapNotNull { index ->
            val item = items?.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("display_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = item.optString("doi").ifBlank { item.optString("id") }
            if (url.isBlank()) return@mapNotNull null
            val normalizedUrl = if (url.startsWith("http")) url else "https://doi.org/" + url.removePrefix("https://doi.org/")
            val year = item.optInt("publication_year").takeIf { it > 0 }?.toString() ?: "Yıl bilgisi yok"
            val citations = item.optInt("cited_by_count")
            ResearchSource(title, "Yayın yılı: $year • OpenAlex kayıtlı atıf sayısı: $citations. Atıf sayısı kalite garantisi değildir.",
                normalizedUrl, "OpenAlex • Akademik indeks")
        }
    }

    private fun searchArxiv(query: String): List<ResearchSource> {
        val xml = getText("https://export.arxiv.org/api/query?search_query=all:$query&start=0&max_results=4")
        val entries = Regex("<entry>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(xml).toList()
        return entries.mapNotNull { match ->
            val block = match.groupValues[1]
            val title = tag(block, "title")?.let(::cleanMarkup)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val summary = tag(block, "summary")?.let(::cleanMarkup).orEmpty().take(700)
            val id = Regex("<id>(.*?)</id>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)?.trim()
                ?: return@mapNotNull null
            if (!id.startsWith("https://arxiv.org/")) return@mapNotNull null
            ResearchSource(title, summary, id, "arXiv • Ön baskı / akademik çalışma")
        }
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<$name[^>]*>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)?.groupValues?.get(1)

    private fun getJson(address: String): JSONObject = JSONObject(getText(address))

    private fun getText(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json, application/atom+xml, application/xml, text/xml")
        connection.setRequestProperty("User-Agent", "JARVIS-Android/1.0 (research module)")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Kaynak servisi HTTP $code yanıtı verdi.")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanMarkup(value: String): String = value
        .replace(Regex("<[^>]*>"), " ")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#x27;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
