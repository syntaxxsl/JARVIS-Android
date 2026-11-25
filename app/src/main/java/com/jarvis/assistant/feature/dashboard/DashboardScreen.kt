package com.jarvis.assistant.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.common.util.TimeUtils
import com.jarvis.assistant.core.common.model.UiState
import com.jarvis.assistant.core.voice.recognition.SpeechRecognizerManager
import com.jarvis.assistant.core.voice.tts.TtsManager
import com.jarvis.assistant.core.common.util.DispatcherProvider
import com.jarvis.assistant.core.common.model.Result
import kotlinx.coroutines.launch
import kotlin.coroutines.resumeWith
import kotlinx.coroutines.flow collectLatest

@Composable
fun DashboardScreen(
    onMicPress: () -> Unit,
    onSendPress: (String) -> Unit,
    messageInput: String,
    setMessageInput: (String) -> Unit,
    val ttsMgr: com.jarvis.assistant.core.voice.tts.TtsManager
) {
    var scrollState by rememberScrollState()
    var textInput by remember { mutableStateOf(messageInput) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        scrollState = scrollState
    ) {
        // Greeting
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = com.jarvis.assistant.core.ui.theme.Color.JarvisPanel
        ) {
            Text(
                text = "JARVIS - ${TimeUtils.nowFormatted()}",
                style = MaterialTheme.typography.titleLarge,
                textColor = com.jarvis.assistant.core.ui.theme.Color.JarvisText
            )
        }

        // Chat area
        Box(
            modifier = Modifier
                .fillMaxSize(0.8f)
                .padding(8.dp)
                .background(com.jarvis.assistant.core.ui.theme.Color.JarvisPanel)
                .border(width = 1.dp, color = com.jarvis.assistant.core.ui.theme.Color.JarvisPanelGlass)
        ) {
            // Messages would go here
            Text(
                text = "JARVIS: Merhaba! Nasıl yardımcı olabilirim?",
                style = MaterialTheme.typography.body1,
                color = com.jarvis.assistant.core.ui.theme.Color.JarvisTextDim
            )
        }

        // Input area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(com.jarvis.assistant.core.ui.theme.Color.JarvisPanel),
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text input
                var currentText by remember { mutableStateOf(messageInput) }
                var currentInput by remember { mutableStateOf("") }

                androidx.compose.material3.TextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    label = { Text("Mesaj") },
                    modifier = Modifier.weight(1f)
                )

                // Send button
                androidx.compose.material3.IconButton(
                    modifier = Modifier.size(48.dp)
                ) {
                    onSendPress(currentInput)
                    currentInput = ""
                }
            }
        }

        // Mic button at bottom
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(com.jarvis.assistant.core.ui.theme.Color.JarvisPanel),
            elevation = 4.dp
        ) {
            androidx.compose.material3.IconButton(
                onClick = onMicPress
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material3Icons.Filled.Microphone,
                    contentDescription = "Mikrofon"
                )
            }
        }
    }
}