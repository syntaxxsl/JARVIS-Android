package com.jarvis.assistant.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        runCatching {
            val extras = sbn.notification.extras ?: return
            val title = runCatching { extras.getCharSequence("android.title")?.toString().orEmpty() }.getOrDefault("")
            val text = runCatching {
                extras.getCharSequence("android.bigText")?.toString()?.takeIf { it.isNotBlank() }
                    ?: extras.getCharSequence("android.text")?.toString().orEmpty()
            }.getOrDefault("")
            if (title.isBlank() && text.isBlank()) return
            val replyAction = runCatching {
                sbn.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
            }.getOrNull()
            NotificationReplyBridge.register(sbn.key, replyAction)
            val current = NotificationStore.read(this)
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
            NotificationStore.write(this, next)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationReplyBridge.remove(sbn.key)
        runCatching {
            val current = NotificationStore.read(this)
            val next = JSONArray()
            for (index in 0 until current.length()) {
                val item = current.optJSONObject(index) ?: continue
                if (item.optString("key") != sbn.key) next.put(item)
            }
            NotificationStore.write(this, next)
        }
    }

}
