package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.core.ai.JarvisAiClient
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
    private lateinit var ttsMgr: TtsManager
    private val history = mutableListOf<Pair<String, String>>()
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            this, "jarvis_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechMgr = SpeechRecognizerManager(this)
        ttsMgr = TtsManager(this).also { it.init() }

        lifecycleScope.launch {
            speechMgr.result.collect { recognized ->
                if (!recognized.isNullOrBlank()) {
                    sendMessage(recognized) { }
                }
            }
        }

        setContent {
            JarvisTheme {
                DashboardScreen(
                    initialApiKey = runCatching { prefs.getString("api_key", "").orEmpty() }.getOrDefault(""),
                    initialModel = runCatching { prefs.getString("model", "openrouter/free").orEmpty() }.getOrDefault("openrouter/free"),
                    initialBaseUrl = runCatching { prefs.getString("base_url", "https://openrouter.ai/api/v1").orEmpty() }.getOrDefault("https://openrouter.ai/api/v1"),
                    onMicPress = { startSpeech() },
                    onSendPress = { message -> sendMessage(message) {} },
                    onSaveSettings = { key, model, url ->
                        runCatching {
                            prefs.edit().putString("api_key", key.trim()).putString("model", model.trim())
                                .putString("base_url", url.trim()).apply()
                        }.onSuccess { Toast.makeText(this, "AI ayarları güvenli alana kaydedildi.", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(this, "Ayarlar kaydedilemedi: ${it.message}", Toast.LENGTH_LONG).show() }
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

    private fun sendMessage(message: String, ignored: () -> Unit) {
        val clean = message.trim()
        if (clean.isBlank()) return
        history.add("user" to clean)
        lifecycleScope.launch {
            val reply = try {
                JarvisAiClient.chat(
                    prefs.getString("api_key", "").orEmpty(),
                    prefs.getString("model", "openrouter/free").orEmpty(),
                    history.toList(),
                    prefs.getString("base_url", "https://openrouter.ai/api/v1").orEmpty()
                )
            } catch (e: Exception) {
                offlineReply(clean) + if (prefs.getString("api_key", "").isNullOrBlank())
                    "\n\nOnline AI için Ayarlar bölümünden OpenRouter API anahtarını ekleyebilirsin."
                else "\n\nAI bağlantı notu: ${e.message ?: "bağlantı hatası"}"
            }
            history.add("assistant" to reply)
            (window.decorView.rootView.tag as? ((String, String) -> Unit))?.invoke(clean, reply)
            // The UI observes responses through the activity callback below.
            responseCallback?.invoke(clean, reply)
        }
    }

    private var responseCallback: ((String, String) -> Unit)? = null

    private fun offlineReply(command: String): String {
        val q = command.lowercase()
        return when {
            "saat" in q || "tarih" in q -> "Şu an: ${TimeUtils.nowFormatted()}"
            "merhaba" in q -> "Merhaba. Ben JARVIS. Nasıl yardımcı olabilirim?"
            "nasılsın" in q -> "Sistemlerim çalışır durumda."
            "alarm" in q -> {
                runOnUiThread { runCatching { startActivity(Intent(AlarmClock.ACTION_SET_ALARM)) } }
                "Alarm kurma ekranını açıyorum."
            }
            else -> "Komutunu aldım. Bu işlem için çevrimdışı bir yetenek tanımlı değil."
        }
    }

    override fun onDestroy() {
        if (::speechMgr.isInitialized) speechMgr.destroy()
        if (::ttsMgr.isInitialized) ttsMgr.shutdown()
        super.onDestroy()
    }

    companion object { private const val REQUEST_AUDIO_PERMISSION = 101 }
}