package com.jarvis.assistant.core.phone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhoneActionHistory {
    private const val PREFS = "jarvis_action_history"
    private const val KEY = "entries"
    private const val LIMIT = 30

    fun record(context: Context, action: String, result: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().apply {
            put("action", action.take(80))
            put("result", result.take(240))
            put("time", System.currentTimeMillis())
        })
        for (index in 0 until minOf(current.length(), LIMIT - 1)) {
            current.optJSONObject(index)?.let { item -> next.put(item) }
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun read(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val rows = JSONArray(raw)
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val time = row.optLong("time")
                val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
                val action = row.optString("action")
                val result = row.optString("result")
                "$stamp  •  $action\n$result"
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
