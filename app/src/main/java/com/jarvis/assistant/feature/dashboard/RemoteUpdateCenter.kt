package com.jarvis.assistant.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Safe OTA channel: downloads declarative configuration only.
 * It never downloads, loads, evaluates, or executes Kotlin/DEX/JAR code.
 */
object RemoteConfigStore {
    private const val PREFS = "jarvis_remote_config"
    private const val ACTIVE = "active_config"
    private const val PREVIOUS = "previous_config"
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/syntaxxsl/JARVIS-Android/main/remote-config/config.json"

    data class Config(
        val version: Int,
        val assistantInstruction: String,
        val enabledModules: Set<String>
    )

    fun activeInstruction(context: android.content.Context): String =
        runCatching {
            val raw = context.getSharedPreferences(PREFS, 0).getString(ACTIVE, null) ?: return ""
            parse(raw).assistantInstruction
        }.getOrDefault("")

    fun activeVersion(context: android.content.Context): Int =
        runCatching {
            val raw = context.getSharedPreferences(PREFS, 0).getString(ACTIVE, null) ?: return 0
            parse(raw).version
        }.getOrDefault(0)

    suspend fun fetch(): Config = withContext(Dispatchers.IO) {
        val connection = (URL(CONFIG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Güncelleme sunucusu HTTP ${connection.responseCode} döndürdü."
            }
            val body = connection.inputStream.bufferedReader().use { it.readText().take(16000) }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    fun apply(context: android.content.Context, config: Config) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val current = prefs.getString(ACTIVE, null)
        val json = JSONObject()
            .put("configVersion", config.version)
            .put("assistantInstruction", config.assistantInstruction)
            .put("enabledModules", org.json.JSONArray(config.enabledModules.sorted()))
            .toString()
        prefs.edit().apply {
            if (!current.isNullOrBlank()) putString(PREVIOUS, current)
            putString(ACTIVE, json)
        }.apply()
    }

    fun rollback(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val previous = prefs.getString(PREVIOUS, null) ?: return false
        return runCatching {
            parse(previous)
            val current = prefs.getString(ACTIVE, null)
            prefs.edit().putString(ACTIVE, previous).putString(PREVIOUS, current).apply()
            true
        }.getOrDefault(false)
    }

    private fun parse(raw: String): Config {
        require(raw.length <= 16000) { "Yapılandırma dosyası çok büyük." }
        val json = JSONObject(raw)
        require(json.optInt("schemaVersion") == 1) { "Desteklenmeyen yapılandırma şeması." }
        val version = json.optInt("configVersion", -1)
        require(version >= 1) { "Geçersiz yapılandırma sürümü." }
        val instruction = json.optString("assistantInstruction", "")
        require(instruction.length <= 2000) { "AI talimatı 2000 karakter sınırını aşıyor." }
        val modulesJson = json.optJSONArray("enabledModules") ?: org.json.JSONArray()
        val allowed = setOf("chat", "research", "knowledge", "mission", "evolution", "documents")
        val modules = buildSet {
            for (i in 0 until modulesJson.length()) {
                val name = modulesJson.optString(i)
                require(name in allowed) { "Bilinmeyen modül anahtarı: $name" }
                add(name)
            }
        }
        return Config(version, instruction, modules)
    }
}

@Composable
fun RemoteUpdatePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Uzak yapılandırma kontrol edilmeye hazır.") }
    var loading by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RemoteConfigStore.Config?>(null) }
    var currentVersion by remember { mutableIntStateOf(RemoteConfigStore.activeVersion(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LIVE UPDATE // OTA", color = JarvisCyan, fontWeight = FontWeight.Bold)
        Text("Arayüz/AI davranış yapılandırması APK yüklemeden güncellenebilir. Kotlin/Android yerel kodu değişiklikleri yine yeni APK gerektirir.", color = JarvisTextDim)
        Text("Etkin yapılandırma: v$currentVersion", color = JarvisText)
        Button(
            onClick = {
                loading = true
                status = "İmzalı olmayan kod değil, yalnızca izin verilen JSON yapılandırması kontrol ediliyor..."
                scope.launch {
                    runCatching { RemoteConfigStore.fetch() }
                        .onSuccess { config ->
                            pending = config
                            status = if (config.version > currentVersion)
                                "Yeni yapılandırma v${config.version} hazır. Uygulamadan önce inceleyip onayla."
                            else "Zaten güncel: v${config.version}"
                        }
                        .onFailure { status = "Güncelleme alınamadı: ${it.message ?: "ağ hatası"}" }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Kontrol ediliyor..." else "Güncellemeleri kontrol et") }
        pending?.takeIf { it.version > currentVersion }?.let { config ->
            Text("Yeni sürüm: v${config.version}", color = JarvisCyan, fontWeight = FontWeight.Bold)
            Text("AI talimatı: ${config.assistantInstruction.take(500)}", color = JarvisTextDim)
            Text("Etkin modüller: ${config.enabledModules.sorted().joinToString().ifBlank { "yok" }}", color = JarvisTextDim)
            Button(onClick = {
                RemoteConfigStore.apply(context, config)
                currentVersion = config.version
                pending = null
                status = "v$currentVersion yapılandırması uygulandı. Yeni AI isteklerinde geçerli."
            }, modifier = Modifier.fillMaxWidth()) { Text("İncele ve uygula") }
        }
        OutlinedButton(
            onClick = {
                val restored = RemoteConfigStore.rollback(context)
                currentVersion = RemoteConfigStore.activeVersion(context)
                status = if (restored) "Önceki yapılandırma v$currentVersion geri yüklendi."
                    else "Geri yüklenecek önceki yapılandırma bulunamadı."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Önceki yapılandırmaya dön") }
        Text(status, color = JarvisTextDim)
        Text("Güvenlik: HTTPS + JSON şema/alan sınırları. Bu kanal uzaktan kod çalıştırmaz; Android APK imzası ve kurulum onayını atlatmaz.", color = JarvisTextDim)
    }
}
