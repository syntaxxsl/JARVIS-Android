package com.jarvis.assistant.core.phone

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

object PhoneActions {
    fun run(context: Context, action: String): String {
        val intent = when (action) {
            "alarm" -> Intent(AlarmClock.ACTION_SET_ALARM)
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "permissions" -> Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:" + context.packageName) }
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            else -> null
        } ?: return "Bu telefon komutu henüz desteklenmiyor."
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "İlgili Android ekranını açtım. Değişikliği sen onaylayıp yapabilirsin."
        } catch (_: Exception) {
            "Bu ekran bu cihazda açılamadı."
        }
    }
}