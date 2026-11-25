package com.jarvis.assistant.core.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

object NotificationReplyBridge {
    private val replyActions = ConcurrentHashMap<String, Notification.Action>()

    fun register(key: String, action: Notification.Action?) {
        if (action?.remoteInputs?.isNotEmpty() == true) replyActions[key] = action
    }

    fun remove(key: String) {
        replyActions.remove(key)
    }

    fun send(context: Context, key: String, message: String): Boolean {
        if (message.isBlank()) return false
        val action = replyActions[key] ?: return false
        val inputs = action.remoteInputs ?: return false
        return runCatching {
            val fillIn = Intent()
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, message) } }
            RemoteInput.addResultsToIntent(inputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            true
        }.getOrDefault(false)
    }
}
