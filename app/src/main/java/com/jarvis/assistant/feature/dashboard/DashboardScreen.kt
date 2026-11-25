package com.jarvis.assistant.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.ui.theme.JarvisAccent
import com.jarvis.assistant.core.ui.theme.JarvisBg
import com.jarvis.assistant.core.ui.theme.JarvisBgDeep
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim
import com.jarvis.assistant.core.common.util.TimeUtils

@Composable
fun DashboardScreen(
    onMicPress: () -> Unit,
    onSendPress: (String) -> Unit
) {
    var message by rememberSaveable { mutableStateOf("") }
    var lastCommand by rememberSaveable { mutableStateOf("Sistem hazır. Komutunuzu bekliyorum.") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = JarvisBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(JarvisBgDeep, JarvisBg, JarvisPanel))
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "KİŞİSEL YAPAY ZEKÂ ASİSTANI",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisTextDim
                    )
                }
                Text(
                    text = "● ÇEVRİMİÇİ",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(190.dp)
                    .background(
                        Brush.radialGradient(listOf(JarvisCyan.copy(alpha = 0.22f), JarvisBgDeep)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .background(
                            Brush.linearGradient(listOf(JarvisAccent, JarvisCyan)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "J",
                        color = JarvisBgDeep,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Merhaba. Ben JARVIS.",
                color = JarvisText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = TimeUtils.nowFormatted(),
                color = JarvisTextDim,
                style = MaterialTheme.typography.bodyMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = lastCommand,
                    modifier = Modifier.padding(18.dp),
                    color = JarvisText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("JARVIS'a mesaj yaz") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val outgoing = message.trim()
                        if (outgoing.isNotEmpty()) {
                            lastCommand = "Sen: $outgoing"
                            onSendPress(outgoing)
                            message = ""
                        }
                    },
                    enabled = message.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Gönder", tint = JarvisCyan)
                }
            }

            Button(
                onClick = onMicPress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("SESLE KONUŞ")
            }
        }
    }
}
