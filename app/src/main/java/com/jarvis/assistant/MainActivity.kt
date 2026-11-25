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
import com.jarvis.assistant.core.common.util.TimeUtils
import com.jarvis.assistant.core.voice.recognition.SpeechRecognizerManager
import com.jarvis.assistant.core.voice.tts.TtsManager
import com.jarvis.assistant.core.ui.theme.JarvisTheme
import com.jarvis.assistant.feature.dashboard.DashboardScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var speechMgr: SpeechRecognizerManager
    private lateinit var ttsMgr: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechMgr = SpeechRecognizerManager(this)
        ttsMgr = TtsManager(this).also { it.init() }

        lifecycleScope.launch {
            speechMgr.result.collect { recognized ->
                if (!recognized.isNullOrBlank()) {
                    processCommand(recognized)
                }
            }
        }

        setContent {
            JarvisTheme {
                DashboardScreen(
                    onMicPress = { startSpeech() },
                    onSendPress = { message -> processCommand(message) }
                )
            }
        }

        requestMicrophonePermissionIfNeeded()
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
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

    private fun processCommand(command: String) {
        lifecycleScope.launch {
            val response = withContext(Dispatchers.Default) { handleCommand(command.trim()) }
            if (response.isNotBlank()) ttsMgr.speak(response)
        }
    }

    private fun handleCommand(command: String): String {
        val normalized = command.lowercase()
        return when {
            normalized.contains("saat") || normalized.contains("tarih") ->
                "Şu an: ${TimeUtils.nowFormatted()}"
            normalized.contains("merhaba") ->
                "Merhaba. Ben JARVIS. Nasıl yardımcı olabilirim?"
            normalized.contains("nasılsın") ->
                "Sistemlerim çalışır durumda. Teşekkür ederim."
            normalized.contains("alarm") -> {
                runOnUiThread {
                    try {
                        startActivity(Intent(AlarmClock.ACTION_SET_ALARM))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Alarm uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
                    }
                }
                "Alarm kurma ekranını açıyorum."
            }
            normalized.isBlank() -> ""
            else -> "Komutunu aldım: $command. Çevrimdışı modda bu komut için henüz bir işlem tanımlı değil."
        }
    }

    override fun onDestroy() {
        if (::speechMgr.isInitialized) speechMgr.destroy()
        if (::ttsMgr.isInitialized) ttsMgr.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 101
    }
}
