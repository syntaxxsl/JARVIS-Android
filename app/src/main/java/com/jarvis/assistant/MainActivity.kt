package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.core.ai.JarvisAiClient
import com.jarvis.assistant.core.data.local.AppDatabase
import com.jarvis.assistant.core.data.local.entity.ChatMessageEntity
import com.jarvis.assistant.core.data.local.entity.MemoryEntity
import com.jarvis.assistant.core.data.local.entity.TaskEntity
import com.jarvis.assistant.core.notifications.NotificationReplyBridge
import com.jarvis.assistant.core.phone.PhoneActions
import com.jarvis.assistant.core.phone.AppLauncher
import com.jarvis.assistant.core.common.util.TimeUtils
import com.jarvis.assistant.core.voice.recognition.SpeechRecognizerManager
import com.jarvis.assistant.core.voice.tts.TtsManager
import com.jarvis.assistant.core.ui.theme.JarvisTheme
import com.jarvis.assistant.feature.dashboard.DashboardScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var speechMgr: SpeechRecognizerManager
    private lateinit var database: AppDatabase
    private val savedMemories = mutableStateListOf<String>()
    private val savedTasks = mutableStateListOf<String>()
    private val savedChat = mutableStateListOf<Pair<String, String>>()
    private lateinit var ttsMgr: TtsManager
    private val history = mutableListOf<Pair<String, String>>()
    private var activeResponseJob: Job? = null
    private var pendingTorchPermission = false
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            this, "jarvis_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            database.memoryDao().observeAll().collect { rows ->
                savedMemories.clear()
                savedMemories.addAll(rows.map { it.content })
            }
        }
        lifecycleScope.launch {
            database.taskDao().observeAll().collect { rows ->
                savedTasks.clear()
                savedTasks.addAll(rows.map { it.title })
            }
        }
        lifecycleScope.launch {
            database.chatMessageDao().observe(DEFAULT_CONVERSATION).collect { rows ->
                savedChat.clear()
                savedChat.addAll(rows.map { it.role to it.content })
                history.clear()
                history.addAll(rows.map { it.role to it.content })
            }
        }
        speechMgr = SpeechRecognizerManager(this)
        ttsMgr = TtsManager(this).also { it.init() }
        lifecycleScope.launch {
            speechMgr.result.collect { recognized ->
                if (!recognized.isNullOrBlank()) sendMessage(recognized) { reply -> ttsMgr.speak(reply) }
            }
        }
        lifecycleScope.launch {
            speechMgr.error.collect { error ->
                if (!error.isNullOrBlank()) Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            JarvisTheme {
                DashboardScreen(
                    initialApiKey = runCatching { prefs.getString("api_key", "").orEmpty() }.getOrDefault(""),
                    initialModel = runCatching { prefs.getString("model", "openrouter/free").orEmpty() }.getOrDefault("openrouter/free"),
                    initialBaseUrl = runCatching { prefs.getString("base_url", "https://openrouter.ai/api/v1").orEmpty() }.getOrDefault("https://openrouter.ai/api/v1"),
                    initialBackupApiKey = runCatching { prefs.getString("backup_api_key", "").orEmpty() }.getOrDefault(""),
                    initialBackupModel = runCatching { prefs.getString("backup_model", "llama-3.3-70b-versatile").orEmpty() }.getOrDefault("llama-3.3-70b-versatile"),
                    initialBackupBaseUrl = runCatching { prefs.getString("backup_base_url", "https://api.groq.com/openai/v1").orEmpty() }.getOrDefault("https://api.groq.com/openai/v1"),
                    memoryItems = savedMemories,
                    taskItems = savedTasks,
                    chatItems = savedChat,
                    onNewChat = { lifecycleScope.launch { database.chatMessageDao().deleteConversation(DEFAULT_CONVERSATION) } },
                    onApproveNotificationReply = { key, reply ->
                        val sent = NotificationReplyBridge.send(this, key, reply)
                        Toast.makeText(this, if (sent) "Yanıt bildirimin üzerinden gönderildi." else "Yanıt gönderilemedi; bildirim artık etkin olmayabilir.", Toast.LENGTH_LONG).show()
                        sent
                    },
                    onAddMemory = { value -> lifecycleScope.launch { database.memoryDao().insert(MemoryEntity(content = value)) } },
                    onDeleteMemory = { value -> lifecycleScope.launch { database.memoryDao().deleteByContent(value) } },
                    onUpdateMemory = { old, updated -> lifecycleScope.launch { database.memoryDao().updateContent(old, updated, System.currentTimeMillis()) } },
                    onAddTask = { value -> lifecycleScope.launch { database.taskDao().insert(TaskEntity(title = value)) } },
                    onMicPress = { startSpeech() },
                    onSendPress = { message, onReply -> sendMessage(message, onReply) },
                    onSaveSettings = { key, model, url, backupKey, backupModel, backupUrl ->
                        runCatching {
                            prefs.edit()
                                .putString("api_key", key.trim())
                                .putString("model", model.trim())
                                .putString("base_url", url.trim())
                                .putString("backup_api_key", backupKey.trim())
                                .putString("backup_model", backupModel.trim())
                                .putString("backup_base_url", backupUrl.trim())
                                .apply()
                        }.onSuccess {
                            Toast.makeText(this, "Birincil ve yedek AI ayarları güvenli alana kaydedildi.", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(this, "Ayarlar kaydedilemedi: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onPhoneAction = { action ->
                        if (action == "torch" && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            pendingTorchPermission = true
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
                        } else {
                            val result = PhoneActions.run(this, action)
                            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSpeak = { text -> ttsMgr.speak(text) },
                    onStopSpeaking = { stopAllActivity() }
                )
            }
        }
        requestMicrophonePermissionIfNeeded()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && pendingTorchPermission) {
            pendingTorchPermission = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, PhoneActions.run(this, "torch"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "El feneri için kamera izni verilmedi.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
        }
    }

    private fun startSpeech() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechMgr.startListening()
        } else {
            requestMicrophonePermissionIfNeeded()
            Toast.makeText(this, "Sesli komut için mikrofon izni gerekli.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(message: String, onReply: (String) -> Unit) {
        val clean = message.trim()
        if (clean.isBlank()) return
        val clockReply = handleClockCommand(clean)
        if (clockReply != null) {
            lifecycleScope.launch {
                database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "user", content = clean))
                database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "assistant", content = clockReply))
            }
            history.add("user" to clean)
            history.add("assistant" to clockReply)
            onReply(clockReply)
            return
        }
        val normalizedCommand = clean.lowercase(java.util.Locale("tr", "TR"))
        if ("ekranı oku" in normalizedCommand || "ekrani oku" in normalizedCommand) {
            val screenText = com.jarvis.assistant.core.phone.JarvisAccessibilityService.readScreen()
            onReply(screenText)
            return
        }
        val phoneAction = detectPhoneAction(clean)
        if (phoneAction != null) {
            val reply = PhoneActions.run(this, phoneAction)
            lifecycleScope.launch {
                database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "user", content = clean))
                database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "assistant", content = reply))
            }
            history.add("user" to clean)
            history.add("assistant" to reply)
            onReply(reply)
            return
        }
        val asksToOpenApp = listOf(" aç", " ac", " başlat", " çalıştır").any { clean.lowercase().contains(it) }
        if (asksToOpenApp) {
            val match = AppLauncher.installedLaunchableApps(this)
                .filter { clean.lowercase().contains(it.label.lowercase()) }
                .maxByOrNull { it.label.length }
            if (match != null) {
                val opened = AppLauncher.launch(this, match.packageName)
                val reply = if (opened) "${match.label} açılıyor." else "${match.label} açılamadı."
                lifecycleScope.launch {
                    database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "user", content = clean))
                    database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "assistant", content = reply))
                }
                history.add("user" to clean)
                history.add("assistant" to reply)
                onReply(reply)
                return
            }
        }
        val currentHistory = history.toList().let { existing -> if (existing.lastOrNull() == ("user" to clean)) existing else existing + ("user" to clean) }.takeLast(20)
        activeResponseJob?.cancel()
        activeResponseJob = lifecycleScope.launch {
            database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "user", content = clean))
            val reply = try {
                JarvisAiClient.chatWithFallback(
                    primaryKey = prefs.getString("api_key", "").orEmpty(),
                    primaryModel = prefs.getString("model", "openrouter/free").orEmpty(),
                    primaryUrl = prefs.getString("base_url", "https://openrouter.ai/api/v1").orEmpty(),
                    backupKey = prefs.getString("backup_api_key", "").orEmpty(),
                    backupModel = prefs.getString("backup_model", "llama-3.3-70b-versatile").orEmpty(),
                    backupUrl = prefs.getString("backup_base_url", "https://api.groq.com/openai/v1").orEmpty(),
                    messages = currentHistory
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                offlineReply(clean) + if (prefs.getString("api_key", "").isNullOrBlank())
                    "\n\nOnline AI için Kontrol bölümünden birincil veya yedek sağlayıcı anahtarı ekleyebilirsin."
                else "\n\nAI bağlantı notu: ${e.message ?: "bağlantı hatası"}"
            }
            database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "assistant", content = reply))
            onReply(reply)
        }
    }

    private fun stopAllActivity() {
        if (::speechMgr.isInitialized) speechMgr.stopListening()
        if (::ttsMgr.isInitialized) ttsMgr.stop()
        activeResponseJob?.cancel()
        activeResponseJob = null
        Toast.makeText(this, "JARVIS işlemi durduruldu.", Toast.LENGTH_SHORT).show()
    }

    private fun detectPhoneAction(command: String): String? {
        val q = command.lowercase(java.util.Locale("tr", "TR"))
        return when {
            listOf("sesi aç", "ses aç", "ses yükselt", "sesi yükselt", "volume up").any(q::contains) -> "volume_up"
            listOf("sesi kıs", "ses kıs", "ses azalt", "volume down").any(q::contains) -> "volume_down"
            listOf("sessize al", "sesi kapat", "mute").any(q::contains) -> "volume_mute"
            listOf("parlaklığı artır", "parlaklığı yükselt", "ekranı aydınlat").any(q::contains) -> "brightness_up"
            listOf("parlaklığı azalt", "ekranı karart").any(q::contains) -> "brightness_down"
            "rahatsız etme" in q && listOf("aç", "etkinleştir").any(q::contains) -> "dnd_on"
            "rahatsız etme" in q && listOf("kapat", "devre dışı").any(q::contains) -> "dnd_off"
            ("wi-fi" in q || "wifi" in q) && listOf("aç", "kapat", "ayar", "bağlan").any(q::contains) -> "wifi"
            "bluetooth" in q && listOf("aç", "kapat", "ayar", "bağlan").any(q::contains) -> "bluetooth"
            "konum ayar" in q -> "location"
            "pil tasarruf" in q || "pil ayar" in q -> "battery"
            "ekran ayar" in q -> "display"
            "ses ayar" in q -> "sound"
            "uygulama ayar" in q -> "apps"
            "bildirim ayar" in q -> "notifications"
            listOf("feneri aç", "el fenerini aç", "ışığı aç", "flashlight on",
                "feneri kapat", "el fenerini kapat", "ışığı kapat", "flashlight off").any(q::contains) -> "torch"
            "telefon ayar" in q || q == "ayarları aç" || q == "ayarlar" -> "settings"
            else -> null
        }
    }

    private fun handleClockCommand(command: String): String? {
        val q = command.lowercase(java.util.Locale("tr", "TR"))
        return try {
            if (listOf("alarm", "uyandır", "uyandırma").any(q::contains)) {
                val time = Regex("([0-9]{1,2}):([0-9]{2})").find(q)
                val hour = time?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 }
                val minute = time?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 0..59 }
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    if (hour != null && minute != null) {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    }
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                })
                if (hour != null && minute != null) "Alarmı " + "%02d:%02d".format(hour, minute) + " için ayarlama ekranını açtım. Kaydetmeden önce kontrol et."
                else "Alarm kurma ekranını açtım. Saati seçip kaydedebilirsin."
            } else if (listOf("zamanlayıcı", "geri sayım", "timer").any(q::contains)) {
                val match = Regex("([0-9]+)\\s*(saniye|sn|dakika|dk|saat)").find(q)
                    ?: return "Zamanlayıcı süresini belirt. Örnek: 5 dakika zamanlayıcı kur."
                val amount = match.groupValues[1].toIntOrNull()?.coerceIn(1, 86400)
                    ?: return "Geçerli bir süre belirt."
                val seconds = when (match.groupValues[2]) {
                    "saat" -> amount * 3600
                    "dakika", "dk" -> amount * 60
                    else -> amount
                }.coerceAtMost(86400)
                startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS zamanlayıcısı")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                })
                "Zamanlayıcıyı " + amount + " " + match.groupValues[2] + " için ayarlama ekranını açtım. Onaylayıp başlat."
            } else null
        } catch (error: Exception) {
            "Saat uygulaması açılamadı: " + (error.message ?: "Android kısıtlaması")
        }
    }

    private fun offlineReply(command: String): String {
        val q = command.lowercase()
        return when {
            "saat" in q || "tarih" in q -> "Şu an: ${TimeUtils.nowFormatted()}"
            "merhaba" in q -> "Merhaba. Ben JARVIS. Nasıl yardımcı olabilirim?"
            "nasılsın" in q -> "Yerel sistemlerim çalışır durumda."
            "alarm" in q -> {
                runOnUiThread { runCatching { startActivity(Intent(AlarmClock.ACTION_SET_ALARM)) } }
                "Alarm kurma ekranını açıyorum."
            }
            "hafıza" in q || "hafiza" in q -> "Kişisel hafızanı Merkez ekranındaki Hafıza bölümünden yönetebilirsin."
            "görev" in q || "gorev" in q -> "Görev modülünü sonraki aşamaya bıraktık; şu an etkin değil."
            else -> "Komutunu aldım. Bu işlem için henüz çevrimdışı bir yetenek tanımlı değil."
        }
    }

    override fun onDestroy() {
        if (::speechMgr.isInitialized) speechMgr.destroy()
        if (::ttsMgr.isInitialized) ttsMgr.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 101
        private const val REQUEST_CAMERA_PERMISSION = 102
        private const val DEFAULT_CONVERSATION = "default"
    }
}