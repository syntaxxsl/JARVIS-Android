package com.jarvis.assistant.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.common.util.TimeUtils
import com.jarvis.assistant.core.ui.theme.*

private data class ChatLine(val role: String, val text: String)
private data class LocalItem(val title: String, val detail: String = "")

@Composable
fun DashboardScreen(
    initialApiKey: String,
    initialModel: String,
    initialBaseUrl: String,
    onMicPress: () -> Unit,
    onSendPress: (String, (String) -> Unit) -> Unit,
    onSaveSettings: (String, String, String) -> Unit,
    onPhoneAction: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf("chat") }
    var message by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf(initialApiKey) }
    var model by rememberSaveable { mutableStateOf(initialModel) }
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    var lastReply by rememberSaveable { mutableStateOf("") }
    var memoryInput by rememberSaveable { mutableStateOf("") }
    var taskInput by rememberSaveable { mutableStateOf("") }
    val chat = remember { mutableStateListOf(ChatLine("assistant", "Merhaba. Ben JARVIS. AI bağlantısı için Ayarlar bölümünü yapılandırabilirsin.")) }
    val memories = remember { mutableStateListOf<LocalItem>() }
    val tasks = remember { mutableStateListOf<LocalItem>() }
    val agents = listOf("Araştırma", "Yazılım Geliştirici", "Planlayıcı", "Telefon Asistanı", "Öğrenme Koçu", "Yaratıcı")
    val navy = JarvisBg

    MaterialTheme {
        Scaffold(
            containerColor = navy,
            bottomBar = {
                NavigationBar(containerColor = JarvisPanel) {
                    NavigationBarItem(selected = page == "chat", onClick = { page = "chat" }, icon = { Icon(Icons.Default.Chat, null) }, label = { Text("Sohbet") })
                    NavigationBarItem(selected = page == "agents", onClick = { page = "agents" }, icon = { Icon(Icons.Default.Hub, null) }, label = { Text("Ajanlar") })
                    NavigationBarItem(selected = page == "phone", onClick = { page = "phone" }, icon = { Icon(Icons.Default.Smartphone, null) }, label = { Text("Telefon") })
                    NavigationBarItem(selected = page == "memory", onClick = { page = "memory" }, icon = { Icon(Icons.Default.Psychology, null) }, label = { Text("Hafıza") })
                    NavigationBarItem(selected = page == "more", onClick = { page = "more" }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Diğer") })
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .background(Brush.verticalGradient(listOf(JarvisBgDeep, JarvisBg, JarvisPanel)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = JarvisCyan, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("PERSONAL AI SYSTEM", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(color = JarvisPanelGlass, shape = CircleShape) {
                        Text("● READY", color = JarvisSuccess, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
                when (page) {
                    "chat" -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Asistan sohbeti", color = JarvisText, style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { chat.clear(); chat.add(ChatLine("assistant", "Yeni sohbet başlatıldı.")) }) { Text("Yeni sohbet") }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(chat) { line ->
                                Card(colors = CardDefaults.cardColors(containerColor = if (line.role == "user") JarvisAccent.copy(alpha = .22f) else JarvisPanel), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(if (line.role == "user") "SEN" else "JARVIS", color = JarvisCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(5.dp))
                                        Text(line.text, color = JarvisText)
                                        if (line.role == "assistant") Row {
                                            TextButton(onClick = { onSpeak(line.text) }) { Icon(Icons.Default.VolumeUp, null); Text("Seslendir") }
                                        }
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.weight(1f), placeholder = { Text("JARVIS'e bir şey sor...") }, shape = RoundedCornerShape(18.dp), maxLines = 4)
                            IconButton(onClick = onMicPress) { Icon(Icons.Default.Mic, "Ses", tint = JarvisCyan) }
                            IconButton(onClick = {
                                val q = message.trim()
                                if (q.isNotEmpty()) { chat.add(ChatLine("user", q)); message = ""; onSendPress(q) { reply -> chat.add(ChatLine("assistant", reply)) } }
                            }) { Icon(Icons.Default.Send, "Gönder", tint = JarvisCyan) }
                        }
                        Text("Saat: ${TimeUtils.nowFormatted()}", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    "agents" -> {
                        Text("UZMAN AJANLAR", color = JarvisText, style = MaterialTheme.typography.titleLarge)
                        Text("Bir ajan seç; görevini sohbet alanına hazırla.", color = JarvisTextDim)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(agents) { agent ->
                                Card(colors = CardDefaults.cardColors(containerColor = JarvisPanel), onClick = {
                                    page = "chat"
                                    val prompt = when (agent) {
                                        "Araştırma" -> "Araştırma ajanı olarak şu konuyu kaynak ve belirsizlikleriyle analiz et: "
                                        "Yazılım Geliştirici" -> "Yazılım geliştirici ajanı olarak şu projeye mimari, kod ve test planı hazırla: "
                                        "Planlayıcı" -> "Planlama ajanı olarak bu hedefi adımlara ve önceliklere böl: "
                                        "Telefon Asistanı" -> "Telefon asistanı olarak Android'de güvenli ve izinli şekilde şu görevi planla: "
                                        "Öğrenme Koçu" -> "Öğrenme koçu olarak bu konuyu seviyeme uygun anlat ve örnek ver: "
                                        else -> "Yaratıcı ajan olarak bu fikir için özgün öneriler geliştir: "
                                    }
                                    message = prompt
                                }) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = JarvisCyan)
                                        Spacer(Modifier.width(12.dp))
                                        Column { Text(agent, color = JarvisText, fontWeight = FontWeight.SemiBold); Text("Görev oluşturmak için dokun", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                    }
                    "phone" -> {
                        Text("TELEFON KONTROL MERKEZİ", color = JarvisText, style = MaterialTheme.typography.titleLarge)
                        Text("Android güvenlik ekranları açılır; son değişikliği sen yaparsın.", color = JarvisTextDim)
                        val actions = listOf("alarm" to "Alarm kur", "wifi" to "Wi-Fi ayarları", "bluetooth" to "Bluetooth ayarları", "sound" to "Ses ayarları", "display" to "Ekran ayarları", "battery" to "Pil tasarrufu", "apps" to "Uygulama ayarları", "settings" to "Tüm ayarlar")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(actions) { (id, title) ->
                                Card(onClick = { onPhoneAction(id) }, colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, null, tint = JarvisCyan); Spacer(Modifier.width(12.dp)); Text(title, color = JarvisText)
                                    }
                                }
                            }
                        }
                    }
                    "memory" -> {
                        Text("KİŞİSEL HAFIZA", color = JarvisText, style = MaterialTheme.typography.titleLarge)
                        Text("Bu ekrandaki notlar bu oturumda tutulur. Kalıcı veritabanı bağlantısı sonraki entegrasyon adımıdır.", color = JarvisTextDim)
                        OutlinedTextField(value = memoryInput, onValueChange = { memoryInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Hatırlanacak not") })
                        Button(onClick = { if (memoryInput.isNotBlank()) { memories.add(LocalItem(memoryInput)); memoryInput = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Hafızaya ekle") }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(memories) { item -> Card(colors = CardDefaults.cardColors(containerColor = JarvisPanel)) { Text(item.title, color = JarvisText, modifier = Modifier.padding(14.dp)) } } }
                    }
                    "more" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { Text("SİSTEM AYARLARI", color = JarvisText, style = MaterialTheme.typography.titleLarge) }
                            item { Text("OpenAI uyumlu servis / OpenRouter", color = JarvisTextDim) }
                            item { OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API anahtarı") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true) }
                            item { OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API adresi") }, singleLine = true) }
                            item { OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Model adı") }, singleLine = true) }
                            item { Button(onClick = { onSaveSettings(apiKey, model, baseUrl) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Güvenli kaydet") } }
                            item { Text("Ücretsiz model için OpenRouter'dan API anahtarı ve kullanılabilir ücretsiz model adı gerekir. Kullanım kotası sağlayıcıya bağlıdır.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                            item { Divider(color = JarvisTextDim.copy(alpha = .3f)) }
                            item { Text("GÖREV LİSTESİ", color = JarvisText, style = MaterialTheme.typography.titleMedium) }
                            item { OutlinedTextField(value = taskInput, onValueChange = { taskInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Yeni görev") }) }
                            item { Button(onClick = { if (taskInput.isNotBlank()) { tasks.add(LocalItem(taskInput)); taskInput = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Görev ekle") } }
                            items(tasks) { task -> Card(colors = CardDefaults.cardColors(containerColor = JarvisPanel)) { Text(task.title, color = JarvisText, modifier = Modifier.padding(14.dp)) } }
                            item { OutlinedButton(onClick = onStopSpeaking, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Text("Sesli yanıtı durdur") } }
                        }
                    }
                }
            }
        }
    }
}