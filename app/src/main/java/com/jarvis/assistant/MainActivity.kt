package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.rememberBottomAppBarIcon
import androidx.compose.material3.BottomNavigationBar
import androidx.compose.material3.BottomNavigationItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.rememberBottomAppBarIcon
import androidx.compose.material3.BottomNavigation
import androidx.compose.material3.navigationBar
import androidx.compose.material3.navigationBarItem
import com.jarvis.assistant.core.common.model.Result
import com.jarvis.assistant.core.common.model.UiState
import com.jarvis.assistant.core.voice.recognition.SpeechRecognizerManager
import com.jarvis.assistant.core.voice.tts.TtsManager
import com.jarvis.assistant.feature.dashboard.DashboardScreen

class MainActivity : AppCompatActivity() {

    private lateinit var speechMgr: SpeechRecognizerManager
    private lateinit var ttsMgr: TtsManager
    private var currentConversationId: String = "default"
    private var messageInput: String = ""

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState)
        speechMgr = SpeechRecognizerManager(this)
        ttsMgr = TtsManager(this)

        // Setup back handler
        OnBackPressedCallback(true).enable { isEnabled ->
            // Handle back press
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    @Override
    public void onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mikrofon izni verildi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Mikrofon izni reddedildi - metin modu", Toast.LENGTH_SHORT).show()
        }
    }

    fun startSpeech() {
        speechMgr.startListening()
    }

    fun stopSpeech() {
        speechMgr.stopListening()
    }

    fun onResult(result: String?) {
        speechMgr._result.value = result
        if (result?.isNotEmpty() == true) {
            processCommand(result)
        }
    }

    private fun processCommand(command: String) {
        lifecycleScope.launch(DispatcherProvider.io) {
            val result = handleCommand(command)
            ttsMgr.speak(result)
        }
    }

    private fun handleCommand(command: String): String {
        return when {
            command.contains("saat") -> TimeUtils.nowFormatted()
            command.contains("tarih") -> TimeUtils.nowFormatted()
            command.contains("merhaba") -> "Merhaba! JARVIS'a hoş geldiniz."
            command.contains("nasılsın") -> "İyiyim, teşekkürler. Siz nasılsınız?"
            command.contains("youtube") -> "YouTube'a yönlendiriliyorsunuz..."
            command.contains("alarm") -> {
                val i = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                "Alarm kurma ekranı açıldı."
            }
            else -> "Çevrimdışı: \"$command\" komutu anlaşıldı."
        }
    }

    @Composable
    private fun content() {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomNavigation {
                    navigationBarItem(
                        id = 1,
                        label = { Text("Dashboard") },
                        icon = { androidx.compose.material3.Icon(iconVector = androidx.compose.material3Icons.Filled.Home, contentDescription = "Ana") }
                    )
                    navigationBarItem(
                        id = 2,
                        label = { Text("Sohbet") },
                        icon = { androidx.compose.material3.Icon(iconVector = androidx.compose.material3Icons.Filled.Chat, contentDescription = "Sohbet") }
                    )
                    navigationBarItem(
                        id = 3,
                        label = { Text("Ayarlar") },
                        icon = { androidx.compose.material3.Icon(iconVector = androidx.compose.material3Icons.Filled.Settings, contentDescription = "Ayarlar") }
                    )
                }
            }
        ) {
            DashboardScreen(
                onMicPress = { startSpeech() },
                onSendPress = { msg -> processCommand(msg) },
                messageInput = messageInput,
                setMessageInput = { msg -> messageInput = msg },
                ttsMgr = ttsMgr
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            materialTheme {
                content()
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }
}