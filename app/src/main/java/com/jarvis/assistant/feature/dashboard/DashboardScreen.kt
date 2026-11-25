package com.jarvis.assistant.feature.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.common.util.TimeUtils
import com.jarvis.assistant.core.ui.theme.*

private data class ChatLine(val role: String, val text: String)

@Composable
fun DashboardScreen(
    initialApiKey: String, initialModel: String, initialBaseUrl: String,
    onMicPress: () -> Unit,
    onSendPress: (String, (String) -> Unit) -> Unit,
    onSaveSettings: (String, String, String) -> Unit,
    onPhoneAction: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf("home") }
    var message by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf(initialApiKey) }
    var model by rememberSaveable { mutableStateOf(initialModel) }
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    var memoryText by rememberSaveable { mutableStateOf("") }
    var taskText by rememberSaveable { mutableStateOf("") }
    val chat = remember { mutableStateListOf(ChatLine("assistant", "Merhaba. Ben JARVIS. Sistemlerin hazır. Nasıl yardımcı olabilirim?")) }
    val memories = remember { mutableStateListOf<String>() }
    val tasks = remember { mutableStateListOf<String>() }
    val agents = listOf(
        "Araştırma" to "Bilgi topla, karşılaştır ve özetle",
        "Yazılım Geliştirici" to "Kod yaz, hata ayıkla ve mimari tasarla",
        "Planlayıcı" to "Hedeflerini uygulanabilir görevlere böl",
        "Telefon Asistanı" to "İzinli Android işlemlerini planla",
        "Öğrenme Koçu" to "Konuları seviyene göre öğret",
        "Yaratıcı" to "Özgün fikirler ve konseptler üret"
    )
    Scaffold(
        containerColor = JarvisBgDeep,
        bottomBar = {
            NavigationBar(containerColor = JarvisPanel) {
                val tabs = listOf(
                    Triple("home", "Merkez", Icons.Default.Dashboard),
                    Triple("chat", "Sohbet", Icons.Default.Chat),
                    Triple("agents", "Ajanlar", Icons.Default.Hub),
                    Triple("phone", "Telefon", Icons.Default.Smartphone),
                    Triple("more", "Sistem", Icons.Default.Tune)
                )
                tabs.forEach { (id, label, icon) ->
                    NavigationBarItem(
                        selected = page == id || (id == "more" && page == "memory"),
                        onClick = { page = id }, icon = { Icon(icon, label) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = JarvisCyan, selectedTextColor = JarvisCyan, indicatorColor = JarvisAccent.copy(alpha = .18f), unselectedIconColor = JarvisTextDim, unselectedTextColor = JarvisTextDim)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(JarvisBgDeep, JarvisBg, JarvisPanel)))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(JarvisCyan.copy(alpha = .13f)).border(1.dp, JarvisCyan.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Memory, null, tint = JarvisCyan)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("J.A.R.V.I.S.", color = JarvisText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("PERSONAL INTELLIGENCE SYSTEM", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = JarvisSuccess.copy(alpha = .12f), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, JarvisSuccess.copy(alpha = .35f))) {
                    Text("● ONLINE", color = JarvisSuccess, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            when (page) {
                "home" -> {
                    val transition = rememberInfiniteTransition(label = "core")
                    val pulse by transition.animateFloat(.72f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        item {
                            Text("SİSTEME HOŞ GELDİN", color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("İyi günler. Nasıl yardımcı olabilirim?", color = JarvisText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(TimeUtils.nowFormatted(), color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
                                Box(Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(JarvisAccent.copy(alpha = .2f), JarvisPanel, JarvisBgDeep))).padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Canvas(Modifier.size(180.dp)) {
                                            val r = size.minDimension * .42f
                                            drawCircle(JarvisCyan.copy(alpha = .08f * pulse), r * 1.3f)
                                            drawCircle(JarvisCyan.copy(alpha = .3f), r, style = Stroke(2.dp.toPx()))
                                            drawCircle(JarvisAccent, r * .78f, style = Stroke(5.dp.toPx()))
                                            drawArc(JarvisCyan, 210f, 250f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round), topLeft = androidx.compose.ui.geometry.Offset(center.x-r*.78f, center.y-r*.78f), size = androidx.compose.ui.geometry.Size(r*1.56f,r*1.56f))
                                            drawCircle(JarvisCyan.copy(alpha = .18f * pulse), r * .55f)
                                            drawCircle(Brush.radialGradient(listOf(JarvisCyan, JarvisAccent, JarvisBgDeep), radius = r*.5f), r*.4f)
                                        }
                                        Text("JARVIS CORE", color = JarvisCyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("SES KOMUTU BEKLENİYOR", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = onMicPress, colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBgDeep), shape = CircleShape) {
                                            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("JARVIS İLE KONUŞ")
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                StatCard("HAFIZA", memories.size.toString(), Icons.Default.Psychology, Modifier.weight(1f)) { page = "memory" }
                                StatCard("GÖREV", tasks.size.toString(), Icons.Default.TaskAlt, Modifier.weight(1f)) { page = "more" }
                                StatCard("AJAN", "06", Icons.Default.Hub, Modifier.weight(1f)) { page = "agents" }
                            }
                        }
                        item { Text("HIZLI ERİŞİM", color = JarvisText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        item { QuickCard("Yapay zekâ sohbeti", "JARVIS ile yazılı iletişim", Icons.Default.Forum) { page = "chat" } }
                        item { QuickCard("Telefon kontrol merkezi", "Cihaz ayarları ve araçlar", Icons.Default.PhonelinkSetup) { page = "phone" } }
                        item { QuickCard("Kişisel hafıza", "Notlarını ve hatırlatmalarını yönet", Icons.Default.Bookmarks) { page = "memory" } }
                    }
                }
                "chat" -> Column(Modifier.fillMaxSize().padding(horizontal = 15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("ASİSTAN BAĞLANTISI", color = JarvisText, fontWeight = FontWeight.Bold); Text("Çevrimiçi AI • Türkçe ses", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall) }
                        IconButton(onClick = onStopSpeaking) { Icon(Icons.Default.StopCircle, "Sesi durdur", tint = JarvisCyan) }
                        TextButton(onClick = { chat.clear(); chat.add(ChatLine("assistant", "Yeni sohbet başlatıldı.")) }) { Text("Yeni") }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(chat) { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.role == "user") Arrangement.End else Arrangement.Start) {
                                Card(Modifier.fillMaxWidth(.91f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (line.role == "user") JarvisAccent.copy(alpha = .22f) else JarvisPanel)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(if (line.role == "user") "SEN" else "J.A.R.V.I.S.", color = JarvisCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(5.dp)); Text(line.text, color = JarvisText)
                                        if (line.role == "assistant") TextButton(onClick = { onSpeak(line.text) }) { Icon(Icons.Default.VolumeUp, null); Text("Seslendir") }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(message, { message = it }, Modifier.weight(1f), placeholder = { Text("Komutunu yaz...") }, shape = RoundedCornerShape(18.dp), maxLines = 4)
                        IconButton(onClick = onMicPress) { Icon(Icons.Default.Mic, "Ses", tint = JarvisCyan) }
                        IconButton(onClick = {
                            val q = message.trim()
                            if (q.isNotEmpty()) { chat.add(ChatLine("user", q)); message = ""; onSendPress(q) { chat.add(ChatLine("assistant", it)) } }
                        }) { Icon(Icons.Default.Send, "Gönder", tint = JarvisCyan) }
                    }
                }
                "agents" -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    item { Text("UZMAN AJANLAR", color = JarvisCyan, style = MaterialTheme.typography.labelMedium); Text("Her görev için doğru uzman.", color = JarvisText, style = MaterialTheme.typography.headlineSmall) }
                    items(agents) { (name, detail) ->
                        QuickCard(name, detail, Icons.Default.AutoAwesome) {
                            message = when(name) {
                                "Araştırma" -> "Araştırma ajanı olarak konuyu kaynaklarıyla analiz et: "
                                "Yazılım Geliştirici" -> "Yazılım geliştirici olarak kodu ve testleri hazırla: "
                                "Planlayıcı" -> "Planlayıcı olarak hedefimi adımlara böl: "
                                "Telefon Asistanı" -> "Telefon asistanı olarak izinli Android görevini planla: "
                                "Öğrenme Koçu" -> "Öğrenme koçu olarak konuyu basitçe öğret: "
                                else -> "Yaratıcı ajan olarak fikirler üret: "
                            }
                            page = "chat"
                        }
                    }
                }
                "phone" -> {
                    val actions = listOf("alarm" to "Alarm kur", "wifi" to "Wi-Fi ayarları", "bluetooth" to "Bluetooth", "sound" to "Ses ayarları", "display" to "Ekran ayarları", "battery" to "Pil tasarrufu", "apps" to "Uygulama ayarları", "settings" to "Tüm ayarlar")
                    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("TELEFON KONTROLÜ", color = JarvisCyan, style = MaterialTheme.typography.labelMedium); Text("Cihazınla bağlantı", color = JarvisText, style = MaterialTheme.typography.headlineSmall); Text("Android izinleri nedeniyle bazı işlemler sistem ekranında onayınla tamamlanır.", color = JarvisTextDim) }
                        items(actions) { (id, label) -> QuickCard(label, "Android sistem aracını aç", Icons.Default.Tune) { onPhoneAction(id) } }
                    }
                }
                "memory" -> Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("KİŞİSEL HAFIZA", color = JarvisCyan, style = MaterialTheme.typography.labelMedium)
                    Text("JARVIS'in not defteri", color = JarvisText, style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(memoryText, { memoryText = it }, Modifier.fillMaxWidth(), label = { Text("Hatırlanacak not") })
                    Button(onClick = { if (memoryText.isNotBlank()) { memories.add(memoryText.trim()); memoryText = "" } }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Hafızaya ekle") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(memories) { Text("•  $it", color = JarvisText, modifier = Modifier.fillMaxWidth().background(JarvisPanel, RoundedCornerShape(12.dp)).padding(13.dp)) } }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("SİSTEM MERKEZİ", color = JarvisCyan, style = MaterialTheme.typography.labelMedium); Text("Yapılandırma ve araçlar", color = JarvisText, style = MaterialTheme.typography.headlineSmall) }
                    item { Text("AI SAĞLAYICI", color = JarvisCyan, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API anahtarı") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true) }
                    item { OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("API adresi") }, singleLine = true) }
                    item { OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model adı") }, singleLine = true) }
                    item { Button(onClick = { onSaveSettings(apiKey, model, baseUrl) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Security, null); Text("Güvenli kaydet") } }
                    item { Text("API anahtarı cihazda şifreli tercihler alanında saklanır. Ücretsiz kullanım sağlayıcının kotasına bağlıdır.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                    item { Divider(color = JarvisTextDim.copy(alpha = .3f)) }
                    item { Text("GÖREV LİSTESİ", color = JarvisCyan, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(taskText, { taskText = it }, Modifier.fillMaxWidth(), label = { Text("Yeni görev") }) }
                    item { Button(onClick = { if (taskText.isNotBlank()) { tasks.add(taskText.trim()); taskText = "" } }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AddTask, null); Text("Görev ekle") } }
                    items(tasks) { Text("•  $it", color = JarvisText, modifier = Modifier.fillMaxWidth().background(JarvisPanel, RoundedCornerShape(12.dp)).padding(13.dp)) }
                    item { OutlinedButton(onClick = onStopSpeaking, Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Text("Sesli yanıtı durdur") } }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = JarvisCyan)
            Text(value, color = JarvisText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).background(JarvisCyan.copy(alpha = .10f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = JarvisCyan) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, color = JarvisText, fontWeight = FontWeight.SemiBold); Text(subtitle, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null, tint = JarvisCyan)
        }
    }
}
