package com.jarvis.assistant.core.notifications

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/**
 * Keeps notification previews encrypted at rest. Migrates the previous app-private
 * preference value once, then clears the legacy plaintext preference.
 */
object NotificationStore {
    private const val SECURE_STORE = "jarvis_notification_store_secure"
    private const val LEGACY_STORE = "jarvis_notification_store"
    private const val KEY = "recent_notifications"

    private fun securePreferences(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            SECURE_STORE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun read(context: Context): JSONArray = runCatching {
        val secure = securePreferences(context)
        if (!secure.contains(KEY)) {
            val legacy = context.getSharedPreferences(LEGACY_STORE, Context.MODE_PRIVATE)
            val previous = legacy.getString(KEY, null)
            if (!previous.isNullOrBlank()) {
                secure.edit().putString(KEY, previous).commit()
                legacy.edit().remove(KEY).commit()
            }
        }
        JSONArray(secure.getString(KEY, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    fun write(context: Context, value: JSONArray) {
        runCatching { securePreferences(context).edit().putString(KEY, value.toString()).apply() }
    }

    fun clear(context: Context) {
        runCatching { securePreferences(context).edit().remove(KEY).apply() }
        context.getSharedPreferences(LEGACY_STORE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
