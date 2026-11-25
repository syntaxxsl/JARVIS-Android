package com.jarvis.assistant.feature.dashboard

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
    initialBackupApiKey: String, initialBackupModel: String, initialBackupBaseUrl: String,
    memoryItems: List<String>, taskItems: List<String>,
    chatItems: List<Pair<String, String>>,
    onNewChat: () -> Unit,
    onAddMemory: (String) -> Unit, onAddTask: (String) -> Unit,
    onMicPress: () -> Unit,
    onSendPress: (String, (String) -> Unit) -> Unit,
    onSaveSettings: (String, String, String, String, String, String) -> Unit,
    onPhoneAction: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf("home") }
    var message by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf(initialApiKey) }
    var model by rememberSaveable { mutableStateOf(initialModel) }
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    var backupKey by rememberSaveable { mutableStateOf(initialBackupApiKey) }
    var backupModel by rememberSaveable { mutableStateOf(initialBackupModel) }
    var backupUrl by rememberSaveable { mutableStateOf(initialBackupBaseUrl) }
    var memoryText by rememberSaveable { mutableStateOf("") }
    var taskText by rememberSaveable { mutableStateOf("") }
    val chat = remember { mutableStateListOf<ChatLine>() }
    LaunchedEffect(chatItems) {
        chat.clear()
        chat.addAll(chatItems.map { ChatLine(it.first, it.second) })
        if (chat.isEmpty()) chat.add(ChatLine("assistant", "Merhaba. Ben JARVIS. Yerel sistemler hazır. Nasıl yardımcı olabilirim?"))
    }
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
            NavigationBar(containerColor = JarvisPanel, tonalElevation = 0.dp) {
                val tabs = listOf(
                    Triple("home", "Merkez", Icons.Default.Dashboard),
                    Triple("chat", "Sohbet", Icons.Default.Chat),
                    Triple("agents", "Ajanlar", Icons.Default.Hub),
                    Triple("phone", "Telefon", Icons.Default.Smartphone),
                    Triple("control", "Kontrol", Icons.Default.Tune)
                )
                tabs.forEach { (id, label, icon) ->
                    NavigationBarItem(
                        selected = page == id || (id == "memory" && page == "control"),
                        onClick = { page = id },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisCyan, selectedTextColor = JarvisCyan,
                            indicatorColor = JarvisCyan.copy(alpha = .14f),
                            unselectedIconColor = JarvisTextDim, unselectedTextColor = JarvisTextDim
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(JarvisBgDeep)) {
            HudBackdrop()
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(JarvisCyan.copy(alpha = .12f))
                        .border(1.dp, JarvisCyan.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Memory, null, tint = JarvisCyan)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = JarvisText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text("PERSONAL INTELLIGENCE SYSTEM", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("● LOCAL CORE", color = JarvisSuccess, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("v1.1 • READY", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { (fadeIn(tween(260)) + slideInVertically { it / 18 }) togetherWith fadeOut(tween(160)) },
                    label = "page-transition"
                ) { target ->
                    when (target) {
                        "home" -> {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 10.dp, bottom = 26.dp),
                                verticalArrangement = Arrangement.spacedBy(15.dp)
                            ) {
                                item {
                                    Text("SİSTEME HOŞ GELDİN", color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Merkez kontrol", color = JarvisText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(TimeUtils.nowFormatted() + "  •  Çevrimdışı temel modül aktif", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                }
                                item { ReactorPanel(onMicPress) }
                                item { LiveDeviceStatusCard() }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        StatCard("HAFIZA", memoryItems.size.toString(), Icons.Default.Psychology, Modifier.weight(1f)) { page = "memory" }
                                        StatCard("GÖREV", taskItems.size.toString(), Icons.Default.TaskAlt, Modifier.weight(1f)) { page = "control" }
                                        StatCard("AJAN", "06", Icons.Default.Hub, Modifier.weight(1f)) { page = "agents" }
                                    }
                                }
                                item { SectionHeading("SİSTEM MODÜLLERİ", "Hızlı erişim") }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        MiniModule("AI SOHBET", Icons.Default.Forum, Modifier.weight(1f)) { page = "chat" }
                                        MiniModule("AI ROUTER", Icons.Default.AccountTree, Modifier.weight(1f)) { page = "control" }
                                    }
                                }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        MiniModule("TELEFON", Icons.Default.PhonelinkSetup, Modifier.weight(1f)) { page = "phone" }
                                        MiniModule("BİLDİRİMLER", Icons.Default.Notifications, Modifier.weight(1f)) { page = "notifications" }
                                    }
                                }
                                item {
                                    HudCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Timeline, null, tint = JarvisCyan)
                                            Spacer(Modifier.width(9.dp))
                                            Text("SİSTEM AKIŞI", color = JarvisText, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        StatusLine("Yerel komut motoru", "HAZIR", true)
                                        StatusLine("Yerel hafıza veritabanı", "BAĞLI", true)
                                        StatusLine("Birincil AI sağlayıcısı", if (apiKey.isBlank()) "ANAHTAR BEKLİYOR" else "AYARLANDI", apiKey.isNotBlank())
                                        StatusLine("Yedek AI sağlayıcısı", if (backupKey.isBlank()) "AYARLANMADI" else "AYARLANDI", backupKey.isNotBlank())
                                    }
                                }
                            }
                        }
                        "chat" -> Column(Modifier.fillMaxSize().padding(horizontal = 15.dp)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("JARVIS İLETİŞİM KANALI", color = JarvisText, fontWeight = FontWeight.Bold)
                                    Text("API yoksa temel yerel komutlar", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = onStopSpeaking) { Icon(Icons.Default.StopCircle, "Sesi durdur", tint = JarvisCyan) }
                                TextButton(onClick = { chat.clear(); onNewChat(); chat.add(ChatLine("assistant", "Yeni sohbet başlatıldı.")) }) { Text("Yeni") }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(chat) { line ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.role == "user") Arrangement.End else Arrangement.Start) {
                                        Card(Modifier.fillMaxWidth(.91f), shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = if (line.role == "user") JarvisAccent.copy(alpha = .22f) else JarvisPanel)) {
                                            Column(Modifier.padding(14.dp)) {
                                                Text(if (line.role == "user") "SEN" else "J.A.R.V.I.S.", color = JarvisCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(5.dp))
                                                Text(line.text, color = JarvisText)
                                                if (line.role == "assistant") TextButton(onClick = { onSpeak(line.text) }) {
                                                    Icon(Icons.Default.VolumeUp, null); Text("Seslendir")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(message, { message = it }, Modifier.weight(1f),
                                    placeholder = { Text("Komutunu yaz...") }, shape = RoundedCornerShape(18.dp), maxLines = 4)
                                IconButton(onClick = onMicPress) { Icon(Icons.Default.Mic, "Ses", tint = JarvisCyan) }
                                IconButton(onClick = {
                                    val q = message.trim()
                                    if (q.isNotEmpty()) {
                                        chat.add(ChatLine("user", q)); message = ""
                                        onSendPress(q) { chat.add(ChatLine("assistant", it)) }
                                    }
                                }) { Icon(Icons.Default.Send, "Gönder", tint = JarvisCyan) }
                            }
                        }
                        "agents" -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            item { SectionHeading("UZMAN AJANLAR", "Her görev için doğru uzman") }
                            items(agents) { (name, detail) ->
                                QuickCard(name, detail, Icons.Default.AutoAwesome) {
                                    message = when (name) {
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
                        "notifications" -> {
                            val context = LocalContext.current
                            var notificationRows by remember { mutableStateOf(readJarvisNotifications(context)) }
                            val accessEnabled = android.provider.Settings.Secure.getString(
                                context.contentResolver, "enabled_notification_listeners"
                            )?.contains(context.packageName) == true
                            LaunchedEffect(Unit) {
                                while (true) {
                                    notificationRows = readJarvisNotifications(context)
                                    kotlinx.coroutines.delay(2500)
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item { SectionHeading("BİLDİRİM MERKEZİ", "İzin verdiğin uygulamalardan gelenler") }
                                item {
                                    HudCard {
                                        StatusLine("Bildirim erişimi", if (accessEnabled) "ETKİN" else "İZİN BEKLİYOR", accessEnabled)
                                        Text("JARVIS yalnızca Android ayarlarından özel erişim verdiğinde bildirim başlığı ve metnini okuyabilir. Kayıtlar cihazında tutulur.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                        Button(onClick = {
                                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text(if (accessEnabled) "Bildirim erişim ayarları" else "Bildirim erişimini aç")
                                        }
                                    }
                                }
                                if (notificationRows.isEmpty()) item { EmptyState("Henüz bildirim yok", "İzin verdikten sonra desteklenen bildirimler burada görünür.") }
                                items(notificationRows) { row ->
                                    HudCard {
                                        Text(row.first, color = JarvisCyan, fontWeight = FontWeight.Bold)
                                        Text(row.second, color = JarvisText)
                                    }
                                }
                            }
                        }
                        "phone" -> {
                            val actions = listOf(
                                "alarm" to "Alarm kur", "wifi" to "Wi-Fi ayarları", "bluetooth" to "Bluetooth",
                                "sound" to "Ses ayarları", "display" to "Ekran ayarları", "battery" to "Pil tasarrufu",
                                "apps" to "Uygulama ayarları", "notifications" to "Bildirim erişimi",
                                "permissions" to "JARVIS izinleri", "location" to "Konum ayarları", "settings" to "Tüm ayarlar"
                            )
                            LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item { SectionHeading("TELEFON KONTROLÜ", "Android ile güvenli bağlantı") }
                                item {
                                    HudCard {
                                        Text("CİHAZ ARAÇLARI", color = JarvisCyan, fontWeight = FontWeight.Bold)
                                        Text("Android izinleri nedeniyle bazı işlemler sistem ekranında onayınla tamamlanır.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                items(actions) { (id, label) -> QuickCard(label, "Android sistem aracını aç", Icons.Default.Tune) { onPhoneAction(id) } }
                            }
                        }
                        "memory" -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { SectionHeading("KİŞİSEL HAFIZA", "JARVIS'in yerel not defteri") }
                            item { OutlinedTextField(memoryText, { memoryText = it }, Modifier.fillMaxWidth(), label = { Text("Hatırlanacak not") }) }
                            item { Button(onClick = { if (memoryText.isNotBlank()) { onAddMemory(memoryText.trim()); memoryText = "" } }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null); Text("Hafızaya ekle")
                            } }
                            if (memoryItems.isEmpty()) item { EmptyState("Henüz kayıt yok", "Önemli notlarını buraya ekleyebilirsin.") }
                            items(memoryItems) { Text("•  $it", color = JarvisText, modifier = Modifier.fillMaxWidth().background(JarvisPanel, RoundedCornerShape(12.dp)).padding(13.dp)) }
                        }
                        else -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { SectionHeading("JARVIS CONTROL", "AI yönlendirici ve sistem yönetimi") }
                            item {
                                HudCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountTree, null, tint = JarvisCyan)
                                        Spacer(Modifier.width(9.dp))
                                        Text("ÇOKLU SAĞLAYICI YÖNLENDİRİCİ", color = JarvisText, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Birincil servis hata verirse yedek servise geçiş denenir. Her sağlayıcının kendi kotası ve erişim koşulları vardır.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    StatusLine("Birincil", if (apiKey.isBlank()) "ANAHTAR YOK" else "YAPILANDIRILDI", apiKey.isNotBlank())
                                    StatusLine("Yedek", if (backupKey.isBlank()) "ANAHTAR YOK" else "YAPILANDIRILDI", backupKey.isNotBlank())
                                    StatusLine("Çevrimdışı komutlar", "HER ZAMAN", true)
                                }
                            }
                            item { Text("BİRİNCİL SAĞLAYICI", color = JarvisCyan, fontWeight = FontWeight.Bold) }
                            item { OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("Birincil API anahtarı") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true) }
                            item { OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Birincil API adresi") }, singleLine = true) }
                            item { OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Birincil model") }, singleLine = true) }
                            item { Divider(color = JarvisCyan.copy(alpha = .22f)) }
                            item { Text("YEDEK SAĞLAYICI", color = JarvisCyan, fontWeight = FontWeight.Bold) }
                            item { OutlinedTextField(backupKey, { backupKey = it }, Modifier.fillMaxWidth(), label = { Text("Yedek API anahtarı") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true) }
                            item { OutlinedTextField(backupUrl, { backupUrl = it }, Modifier.fillMaxWidth(), label = { Text("Yedek API adresi") }, singleLine = true) }
                            item { OutlinedTextField(backupModel, { backupModel = it }, Modifier.fillMaxWidth(), label = { Text("Yedek model") }, singleLine = true) }
                            item {
                                Button(onClick = { onSaveSettings(apiKey, model, baseUrl, backupKey, backupModel, backupUrl) }, Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text("Tüm AI ayarlarını güvenli kaydet")
                                }
                            }
                            item { Text("Anahtarlar cihazdaki şifreli tercihler alanında saklanır. Kota bilgisi servis sağlayıcıdan gelmediği sürece tahmin edilmez.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                            item { Divider(color = JarvisCyan.copy(alpha = .22f)) }
                            item { Text("GÖREV YÖNETİMİ", color = JarvisCyan, fontWeight = FontWeight.Bold) }
                            item { OutlinedTextField(taskText, { taskText = it }, Modifier.fillMaxWidth(), label = { Text("Yeni görev") }) }
                            item { Button(onClick = { if (taskText.isNotBlank()) { onAddTask(taskText.trim()); taskText = "" } }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AddTask, null); Text("Görev ekle")
                            } }
                            if (taskItems.isEmpty()) item { EmptyState("Görev listesi boş", "Yapılacaklarını buradan takip edebilirsin.") }
                            items(taskItems) { Text("•  $it", color = JarvisText, modifier = Modifier.fillMaxWidth().background(JarvisPanel, RoundedCornerShape(12.dp)).padding(13.dp)) }
                            item { OutlinedButton(onClick = onStopSpeaking, Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Text("Sesli yanıtı durdur") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudBackdrop() {
    val transition = rememberInfiniteTransition(label = "hud-background")
    val scan by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "scan")
    Canvas(Modifier.fillMaxSize()) {
        val step = 34.dp.toPx()
        for (x in 0..(size.width / step).toInt()) {
            drawLine(JarvisCyan.copy(alpha = .035f), androidx.compose.ui.geometry.Offset(x * step, 0f),
                androidx.compose.ui.geometry.Offset(x * step, size.height), 1f)
        }
        for (y in 0..(size.height / step).toInt()) {
            drawLine(JarvisCyan.copy(alpha = .035f), androidx.compose.ui.geometry.Offset(0f, y * step),
                androidx.compose.ui.geometry.Offset(size.width, y * step), 1f)
        }
        val scanY = size.height * scan
        drawLine(JarvisCyan.copy(alpha = .12f), androidx.compose.ui.geometry.Offset(0f, scanY),
            androidx.compose.ui.geometry.Offset(size.width, scanY), 2.dp.toPx())
    }
}

@Composable
private fun ReactorPanel(onMicPress: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "reactor")
    val pulse by transition.animateFloat(.82f, 1.06f, infiniteRepeatable(tween(1450), RepeatMode.Reverse), label = "pulse")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rotation")
    HudCard {
        Box(Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(JarvisAccent.copy(alpha = .20f), JarvisPanel, JarvisBgDeep))).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(226.dp)) {
                    val r = size.minDimension * .38f
                    drawCircle(JarvisCyan.copy(alpha = .055f), r * 1.55f * pulse)
                    drawCircle(JarvisCyan.copy(alpha = .20f), r * 1.28f, style = Stroke(1.dp.toPx()))
                    drawCircle(JarvisAccent.copy(alpha = .85f), r, style = Stroke(4.dp.toPx()))
                    drawArc(JarvisCyan, rotation, 105f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x-r, center.y-r),
                        size = androidx.compose.ui.geometry.Size(r*2, r*2))
                    drawArc(JarvisCyan.copy(alpha = .55f), -rotation * .7f, 70f, false,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x-r*1.22f, center.y-r*1.22f),
                        size = androidx.compose.ui.geometry.Size(r*2.44f, r*2.44f))
                    drawCircle(Brush.radialGradient(listOf(JarvisCyan.copy(alpha = .95f), JarvisAccent, JarvisBgDeep), radius = r*.8f), r*.62f)
                    drawCircle(JarvisCyan.copy(alpha = .9f), r*.16f)
                }
                Text("J.A.R.V.I.S. CORE", color = JarvisCyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("YEREL SİSTEM AKTİF  •  AI BAĞLANTISI AYARLANABİLİR", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onMicPress, colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBgDeep), shape = CircleShape) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("JARVIS İLE KONUŞ")
                }
            }
        }
    }
}

@Composable
private fun HudCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().border(1.dp, JarvisCyan.copy(alpha = .20f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = JarvisPanel.copy(alpha = .94f))) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable
private fun SectionHeading(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker, color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(title, color = JarvisText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusLine(name: String, status: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (ready) JarvisSuccess else JarvisTextDim))
        Spacer(Modifier.width(9.dp))
        Text(name, color = JarvisText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(status, color = if (ready) JarvisSuccess else JarvisTextDim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniModule(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(92.dp), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = JarvisCyan)
            Text(title, color = JarvisText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    HudCard {
        Icon(Icons.Default.Inbox, null, tint = JarvisCyan)
        Text(title, color = JarvisText, fontWeight = FontWeight.Bold)
        Text(detail, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = JarvisCyan)
            Text(value, color = JarvisText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).background(JarvisCyan.copy(alpha = .10f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = JarvisCyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = JarvisText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = JarvisCyan)
        }
    }
}

private data class DeviceSnapshot(
    val battery: Int,
    val ramUsed: Long,
    val ramTotal: Long,
    val storageUsed: Long,
    val storageTotal: Long,
    val network: String
)

@Composable
private fun LiveDeviceStatusCard() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readDeviceSnapshot(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readDeviceSnapshot(context)
            kotlinx.coroutines.delay(5000)
        }
    }
    val ramPercent = if (snapshot.ramTotal > 0) (snapshot.ramUsed * 100 / snapshot.ramTotal).toInt() else 0
    val storagePercent = if (snapshot.storageTotal > 0) (snapshot.storageUsed * 100 / snapshot.storageTotal).toInt() else 0
    HudCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonitorHeart, null, tint = JarvisCyan)
            Spacer(Modifier.width(9.dp))
            Text("CANLI CİHAZ TELEMETRİSİ", color = JarvisText, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        StatusLine("Pil", "${snapshot.battery.coerceAtLeast(0)}%", snapshot.battery >= 20)
        StatusLine("Bellek kullanımı", "$ramPercent%", ramPercent < 90)
        StatusLine("Depolama kullanımı", "$storagePercent%", storagePercent < 90)
        StatusLine("Ağ bağlantısı", snapshot.network, snapshot.network == "BAĞLI")
        Text(
            "RAM: ${formatBytes(snapshot.ramUsed)} / ${formatBytes(snapshot.ramTotal)}   •   Depolama: ${formatBytes(snapshot.storageUsed)} / ${formatBytes(snapshot.storageTotal)}",
            color = JarvisTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Text("Veriler bu cihazdan 5 saniyede bir okunur.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
    }
}

private fun readDeviceSnapshot(context: Context): DeviceSnapshot {
    val battery = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }.getOrDefault(-1)
    val memory = ActivityManager.MemoryInfo()
    runCatching { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory) }
    val totalRam = memory.totalMem.coerceAtLeast(0L)
    val usedRam = (totalRam - memory.availMem).coerceAtLeast(0L)
    val stat = StatFs(context.filesDir.absolutePath)
    val totalStorage = stat.totalBytes
    val usedStorage = (totalStorage - stat.availableBytes).coerceAtLeast(0L)
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity?.activeNetwork
    val caps = connectivity?.getNetworkCapabilities(network)
    val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return DeviceSnapshot(battery, usedRam, totalRam, usedStorage, totalStorage, if (connected) "BAĞLI" else "ÇEVRİMDIŞI")
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
    else String.format(java.util.Locale.getDefault(), "%.0f MB", bytes / (1024.0 * 1024.0))
}


private fun readJarvisNotifications(context: Context): List<Pair<String, String>> {
    val raw = context.getSharedPreferences("jarvis_notification_store", Context.MODE_PRIVATE)
        .getString("recent_notifications", "[]") ?: "[]"
    return runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").ifBlank { item.optString("package") }
            val body = item.optString("text")
            (title to body).takeIf { title.isNotBlank() || body.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}
