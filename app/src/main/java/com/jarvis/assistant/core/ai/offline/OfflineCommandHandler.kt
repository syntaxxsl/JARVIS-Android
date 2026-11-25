package com.jarvis.assistant.core.ai.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineCommandHandler(private val context: Context) {
    fun handle(input: String): String {
        val q = input.lowercase(Locale("tr", "TR"))
        return when {
            q.contains("saat") || q.contains("zaman") -> {
                val sdf = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
                "Şu an saat ${sdf.format(Date())}."
            }
            q.contains("tarih") || q.contains("gün") -> {
                val sdf = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr", "TR"))
                "Bugün ${sdf.format(Date())}."
            }
            q.contains("merhaba") || q.contains("selam") -> "Merhaba! Ben JARVIS, size nasıl yardımcı olabilirim?"
            q.contains("nasılsın") -> "İyiyim, teşekkürler. Siz nasılsınız?"
            q.contains("youtube") -> {
                openApp("com.google.android.youtube"); "YouTube açılıyor."
            }
            q.contains("tarayıcı") || q.contains("chrome") -> {
                openApp("com.android.chrome"); "Tarayıcı açılıyor."
            }
            q.contains("ayarlar") -> {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Ayarlar açılıyor."
            }
            q.contains("alarm") -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                "Alarm kurma ekranı açılıyor."
            }
            q.startsWith("ara ") || q.startsWith("call ") -> {
                val number = input.substringAfter(" ").trim()
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                "$number aranıyor."
            }
            else -> "Çevrimdışı moddayım. İnternet bağlantısı olduğunda daha gelişmiş yanıtlar verebilirim. Sorunuz: \"$input\""
        }
    }

    private fun openApp(pkg: String) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}