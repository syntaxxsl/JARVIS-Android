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
import com.jarvis.assistant.core.phone.PhoneActions
import com.jarvis.assistant.core.common.util.TimeUtils
import com.jarvis.assistant.core.voice.recognition.SpeechRecognizerManager
import com.jarvis.assistant.core.voice.tts.TtsManager
import com.jarvis.assistant.core.ui.theme.JarvisTheme
import com.jarvis.assistant.feature.dashboard.DashboardScreen
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
                    onAddMemory = { value -> lifecycleScope.launch { database.memoryDao().insert(MemoryEntity(content = value)) } },
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
                        val result = PhoneActions.run(this, action)
                        Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                    },
                    onSpeak = { text -> ttsMgr.speak(text) },
                    onStopSpeaking = { ttsMgr.stop() }
                )
            }
        }
        requestMicrophonePermissionIfNeeded()
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
        val currentHistory = history.toList().filterNot { it.first == "user" && it.second == clean }.takeLast(19) + ("user" to clean)
        lifecycleScope.launch {
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
            } catch (e: Exception) {
                offlineReply(clean) + if (prefs.getString("api_key", "").isNullOrBlank())
                    "\n\nOnline AI için Kontrol bölümünden birincil veya yedek sağlayıcı anahtarı ekleyebilirsin."
                else "\n\nAI bağlantı notu: ${e.message ?: "bağlantı hatası"}"
            }
            database.chatMessageDao().insert(ChatMessageEntity(conversationId = DEFAULT_CONVERSATION, role = "assistant", content = reply))
            onReply(reply)
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
            "görev" in q || "gorev" in q -> "Görevlerini Kontrol bölümündeki görev listesinde görebilirsin."
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
        private const val DEFAULT_CONVERSATION = "default"
    }
}