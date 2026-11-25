package com.jarvis.assistant.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = runCatching { sbn.notification.extras }.getOrNull() ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val replyAction = sbn.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        NotificationReplyBridge.register(sbn.key, replyAction)
        val prefs = getSharedPreferences(STORE, MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().apply {
            put("key", sbn.key)
            put("canReply", replyAction != null)
            put("package", sbn.packageName)
            put("title", title.take(180))
            put("text", text.take(1200))
            put("time", sbn.postTime)
        })
        for (i in 0 until minOf(current.length(), 29)) {
            val item = current.optJSONObject(i) ?: continue
            if (item.optString("key") != sbn.key) next.put(item)
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationReplyBridge.remove(sbn.key)
        runCatching {
            val prefs = getSharedPreferences(STORE, MODE_PRIVATE)
            val current = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            val next = JSONArray()
            for (index in 0 until current.length()) {
                val item = current.optJSONObject(index) ?: continue
                if (item.optString("key") != sbn.key) next.put(item)
            }
            prefs.edit().putString(KEY, next.toString()).apply()
        }
    }

    companion object {
        const val STORE = "jarvis_notification_store"
        const val KEY = "recent_notifications"
    }
}
