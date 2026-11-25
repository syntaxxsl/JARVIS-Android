package com.jarvis.assistant.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val prefs = getSharedPreferences(STORE, MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().apply {
            put("package", sbn.packageName)
            put("title", title.take(180))
            put("text", text.take(1200))
            put("time", sbn.postTime)
        })
        for (i in 0 until minOf(current.length(), 29)) {
            val item = current.optJSONObject(i) ?: continue
            if (item.optLong("time") != sbn.postTime || item.optString("package") != sbn.packageName) next.put(item)
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        const val STORE = "jarvis_notification_store"
        const val KEY = "recent_notifications"
    }
}
