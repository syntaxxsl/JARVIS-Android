package com.jarvis.assistant.feature.dashboard

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.jarvis.assistant.core.documents.DocumentAnalyzer
import com.jarvis.assistant.core.phone.PhoneActionHistory
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import com.jarvis.assistant.core.ai.JarvisAiClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onApproveNotificationReply: (String, String) -> Boolean,
    onAddMemory: (String) -> Unit, onAddTask: (String) -> Unit,
    onDeleteMemory: (String) -> Unit, onUpdateMemory: (String, String) -> Unit,
    onMicPress: () -> Unit,
    onSendPress: (String, (String) -> Unit) -> Unit,
    onSaveSettings: (String, String, String, String, String, String) -> Unit,
    onTestAiConnection: (String, String, String, String, String, String, (Boolean, String) -> Unit) -> Unit,
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
    var showStartup by rememberSaveable { mutableStateOf(true) }
    var apiTestStatus by rememberSaveable { mutableStateOf("Henüz bağlantı testi yapılmadı.") }
    var modelListStatus by rememberSaveable { mutableStateOf("Model listesi henüz alınmadı.") }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    val modelScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(760); showStartup = false }
    val chat = remember { mutableStateListOf<ChatLine>() }
    LaunchedEffect(chatItems.size) {
        chat.clear()
        chat.addAll(chatItems.map { ChatLine(it.first, it.second) })
        if (chat.isEmpty()) chat.add(ChatLine("assistant", "Merhaba. Ben JARVIS. Yerel sistemler hazır. Nasıl yardımcı olabilirim?"))
    }
    val agents = listOf(
        "Araştırma" to "Bilgi topla, karşılaştır ve özetle",
        "Yazılım Geliştirici" to "Kod yaz, hata ayıkla ve mimari tasarla",
        "Belge Analisti" to "PDF ve görsellerdeki metinleri analiz et",
        "Telefon Asistanı" to "İzinli Android işlemlerini planla",
        "Öğrenme Koçu" to "Konuları seviyene göre öğret",
        "Yaratıcı" to "Özgün fikirler ve konseptler üret"
    )
    Scaffold(
        containerColor = JarvisBgDeep,
        bottomBar = {
            val tabs = listOf(
                Triple("home", "STARK", Icons.Default.Dashboard),
                Triple("chat", "NEURAL", Icons.Default.Chat),
                Triple("control", "SİSTEM", Icons.Default.Tune),
                Triple("modules", "MODÜLLER", Icons.Default.GridView)
            )
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(JarvisBgDeep.copy(alpha = .2f), JarvisBgDeep)))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(23.dp))
                        .background(Brush.horizontalGradient(listOf(JarvisPanel, JarvisAccent.copy(alpha = .20f), JarvisPanel)))
                        .border(1.dp, JarvisCyan.copy(alpha = .32f), RoundedCornerShape(23.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    tabs.forEach { (id, label, icon) ->
                        val selected = page == id
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (selected) JarvisCyan.copy(alpha = .16f) else JarvisPanel.copy(alpha = .3f))
                                .clickable { page = id }
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(icon, label, tint = if (selected) JarvisCyan else JarvisTextDim, modifier = Modifier.size(21.dp))
                            Text(label, color = if (selected) JarvisCyan else JarvisTextDim, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
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
                        Text("STARK INDUSTRIES  /  J.A.R.V.I.S.", color = JarvisText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text("MARK VII  •  PERSONAL DEFENSE & INTELLIGENCE", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("● ARC CORE ONLINE", color = JarvisSuccess, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("MARK VII  /  READY", color = JarvisCyan, style = MaterialTheme.typography.labelSmall)
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
                                    Text("STARK INDUSTRIES  //  SECURE INTERFACE", color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Good evening, Sir.", color = JarvisText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(TimeUtils.nowFormatted() + "  •  ARC REACTOR: ONLINE  •  LOCAL CORE ACTIVE", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                }
                                item { ReactorPanel(onMicPress) }
                                item { LiveDeviceStatusCard() }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        StatCard("HAFIZA", memoryItems.size.toString(), Icons.Default.Psychology, Modifier.weight(1f)) { page = "memory" }
                                        StatCard("AJAN", "06", Icons.Default.Hub, Modifier.weight(1f)) { page = "agents" }
                                    }
                                }
                                item { SectionHeading("SİSTEM MODÜLLERİ", "Hızlı erişim") }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        MiniModule("AI SOHBET", Icons.Default.Forum, Modifier.weight(1f)) { page = "chat" }
                                        MiniModule("AI ROUTER", Icons.Default.AccountTree, Modifier.weight(1f)) { page = "router" }
                                    }
                                }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        MiniModule("TELEFON", Icons.Default.PhonelinkSetup, Modifier.weight(1f)) { page = "phone" }
                                        MiniModule("BİLDİRİMLER", Icons.Default.Notifications, Modifier.weight(1f)) { page = "notifications" }
                                    }
                                }
                                item {
                                    MiniModule("PDF / OCR ANALİZİ", Icons.Default.DocumentScanner, Modifier.fillMaxWidth()) { page = "documents" }
                                }
                                item {
                                    MiniModule("JARVIS EVOLUTION", Icons.Default.AutoAwesome, Modifier.fillMaxWidth()) { page = "evolution" }
                                }
                                item {
                                    MiniModule("MISSION CONTROL", Icons.Default.AccountTree, Modifier.fillMaxWidth()) { page = "mission" }
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
                                    Text("STARK NEURAL LINK  //  MARK VII", color = Color(0xFFFFC46B), fontWeight = FontWeight.Bold)
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
                                        "Belge Analisti" -> "Belge analisti olarak bu içeriği incele: "
                                        "Telefon Asistanı" -> "Telefon asistanı olarak izinli Android görevini planla: "
                                        "Öğrenme Koçu" -> "Öğrenme koçu olarak konuyu basitçe öğret: "
                                        else -> "Yaratıcı ajan olarak fikirler üret: "
                                    }
                                    page = "chat"
                                }
                            }
                        }
                        "mission" -> MissionControlScreen(onSendPress = onSendPress)
                        "documents" -> {
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            var analysis by rememberSaveable { mutableStateOf("Bir PDF veya görsel seç. JARVIS sayfaları/metni OCR ile okuyup çıkarabilir.") }
                            var isAnalyzing by remember { mutableStateOf(false) }
                            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                                if (uri != null) {
                                    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    isAnalyzing = true
                                    analysis = "Belge inceleniyor..."
                                    scope.launch {
                                        analysis = runCatching { DocumentAnalyzer.analyze(context, uri) }
                                            .getOrElse { "Belge okunamadı: ${it.message ?: "bilinmeyen hata"}" }
                                        isAnalyzing = false
                                    }
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { SectionHeading("BELGE ZEKÂSI", "PDF ve görsel metin analizi") }
                                item {
                                    HudCard {
                                        Text("OCR MOTORU", color = JarvisCyan, fontWeight = FontWeight.Bold)
                                        Text("Görsellerdeki ve PDF sayfalarındaki yazıları cihaz üzerinde tanımaya çalışır. PDF işlemi performans için ilk 8 sayfayla sınırlıdır.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                        Button(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }, enabled = !isAnalyzing, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("PDF veya görsel seç")
                                        }
                                        if (isAnalyzing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = JarvisCyan)
                                    }
                                }
                                item {
                                    HudCard {
                                        Text("ÇIKARILAN METİN", color = JarvisCyan, fontWeight = FontWeight.Bold)
                                        Text(analysis, color = JarvisText)
                                    }
                                }
                                item {
                                    OutlinedButton(onClick = { analysis = "Bir PDF veya görsel seç. JARVIS sayfaları/metni OCR ile okuyup çıkarabilir." }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.DeleteSweep, null); Text("Sonucu temizle")
                                    }
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
                                    kotlinx.coroutines.delay(6000)
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item { SectionHeading("BİLDİRİM MERKEZİ", "İzin verdiğin uygulamalardan gelenler") }
                                item {
                                    HudCard {
                                        StatusLine("Bildirim erişimi", if (accessEnabled) "ETKİN" else "İZİN BEKLİYOR", accessEnabled)
                                        Text("JARVIS yalnızca Android ayarlarından özel erişim verdiğinde bildirim başlığı ve metnini okuyabilir. Kayıtlar cihazında tutulur; hassas mesajlar AI sağlayıcısına yalnızca yanıt taslağı istediğinde gönderilir.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { notificationRows = readJarvisNotifications(context) }, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.Refresh, null); Text("Yenile")
                                            }
                                            OutlinedButton(onClick = {
                                                com.jarvis.assistant.core.notifications.NotificationStore.clear(context)
                                                notificationRows = emptyList()
                                            }, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.DeleteSweep, null); Text("Temizle")
                                            }
                                        }
                                        Button(onClick = {
                                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text(if (accessEnabled) "Bildirim erişim ayarları" else "Bildirim erişimini aç")
                                        }
                                    }
                                }
                                if (notificationRows.isEmpty()) item { EmptyState("Henüz bildirim yok", "İzin verdikten sonra desteklenen bildirimler burada görünür.") }
                                items(notificationRows, key = { it.key }) { row ->
                                    var draft by remember(row.key) { mutableStateOf("") }
                                    var sendStatus by remember(row.key) { mutableStateOf("") }
                                    HudCard {
                                        Text(row.title, color = JarvisCyan, fontWeight = FontWeight.Bold)
                                        Text(row.text, color = JarvisText)
                                        if (row.canReply) {
                                            OutlinedButton(onClick = {
                                                sendStatus = "Yanıt taslağı hazırlanıyor..."
                                                onSendPress(
                                                    "Aşağıdaki bildirime uygun, kısa ve doğal bir Türkçe yanıt taslağı hazırla. Yalnızca yanıt metnini ver. Gönderen: ${row.title}. Gelen mesaj: ${row.text}"
                                                ) { result ->
                                                    draft = result
                                                    sendStatus = ""
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Icon(Icons.Default.AutoAwesome, null); Text("Yanıt taslağı oluştur")
                                            }
                                            if (sendStatus.isNotBlank()) Text(sendStatus, color = JarvisTextDim)
                                            if (draft.isNotBlank()) {
                                                OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gönderilecek yanıtı kontrol et") })
                                                Button(onClick = {
                                                    val sent = onApproveNotificationReply(row.key, draft)
                                                    sendStatus = if (sent) "Yanıt gönderildi." else "Bu bildirimin yanıt kanalı artık kullanılamıyor."
                                                    if (sent) draft = ""
                                                }, modifier = Modifier.fillMaxWidth()) {
                                                    Icon(Icons.Default.Send, null); Text("Onayla ve yanıtı gönder")
                                                }
                                            }
                                        } else {
                                            Text("Bu bildirim doğrudan yanıt desteklemiyor.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        "phone" -> AppControlScreen()
                        "memory" -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { SectionHeading("KİŞİSEL HAFIZA", "JARVIS'in yerel not defteri") }
                            item { OutlinedTextField(memoryText, { memoryText = it }, Modifier.fillMaxWidth(), label = { Text("Hatırlanacak not") }) }
                            item { Button(onClick = { if (memoryText.isNotBlank()) { onAddMemory(memoryText.trim()); memoryText = "" } }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null); Text("Hafızaya ekle")
                            } }
                            if (memoryItems.isEmpty()) item { EmptyState("Henüz kayıt yok", "Önemli notlarını buraya ekleyebilirsin.") }
                            items(memoryItems) { saved ->
                                var edited by remember(saved) { mutableStateOf(saved) }
                                HudCard {
                                    OutlinedTextField(edited, { edited = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Hafıza kaydı") })
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { if (edited.isNotBlank()) onUpdateMemory(saved, edited.trim()) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Save, null); Text("Kaydet")
                                        }
                                        OutlinedButton(onClick = { onDeleteMemory(saved) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Delete, null); Text("Sil")
                                        }
                                    }
                                }
                            }
                        }
                        "control" -> LazyColumn(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            item { SectionHeading("ORBIT CONTROL CENTER", "Cihaz ve izinli sistem kontrolleri") }
                            item {
                                HudCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(38.dp).clip(CircleShape).background(JarvisCyan.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Hub, null, tint = JarvisCyan)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("JARVIS SYSTEM CORE", color = JarvisText, fontWeight = FontWeight.Bold)
                                            Text("Android tarafından izin verilen canlı cihaz telemetrisi", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("LIVE", color = JarvisSuccess, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            item { LiveDeviceStatusCard() }
                            item { PhoneActionHistoryPanel() }
                            item { SectionHeading("HIZLI SİSTEM ERİŞİMİ", "Android kontrol panelleri") }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("WI-FI", Icons.Default.Wifi, Modifier.weight(1f)) { onPhoneAction("wifi") }
                                    MiniModule("BLUETOOTH", Icons.Default.Bluetooth, Modifier.weight(1f)) { onPhoneAction("bluetooth") }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("EKRAN", Icons.Default.Brightness6, Modifier.weight(1f)) { onPhoneAction("display") }
                                    MiniModule("SES", Icons.Default.VolumeUp, Modifier.weight(1f)) { onPhoneAction("sound") }
                                }
                            }
                            item { SectionHeading("DOĞRUDAN KONTROLLER", "İzin verildiğinde ayarı JARVIS değiştirir") }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("SES +", Icons.Default.VolumeUp, Modifier.weight(1f)) { onPhoneAction("volume_up") }
                                    MiniModule("SES −", Icons.Default.VolumeDown, Modifier.weight(1f)) { onPhoneAction("volume_down") }
                                    MiniModule("SESSİZ", Icons.Default.VolumeOff, Modifier.weight(1f)) { onPhoneAction("volume_mute") }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("PARLAKLIK +", Icons.Default.BrightnessHigh, Modifier.weight(1f)) { onPhoneAction("brightness_up") }
                                    MiniModule("PARLAKLIK −", Icons.Default.BrightnessLow, Modifier.weight(1f)) { onPhoneAction("brightness_down") }
                                }
                            }
                            item {
                                MiniModule("EL FENERİ", Icons.Default.FlashlightOn, Modifier.fillMaxWidth()) { onPhoneAction("torch") }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("RAHATSIZ ETME", Icons.Default.DoNotDisturbOn, Modifier.weight(1f)) { onPhoneAction("dnd") }
                                    MiniModule("30 SN EKRAN", Icons.Default.Timer, Modifier.weight(1f)) { onPhoneAction("timeout_30") }
                                    MiniModule("2 DK EKRAN", Icons.Default.Schedule, Modifier.weight(1f)) { onPhoneAction("timeout_120") }
                                }
                            }
                            item {
                                HudCard {
                                    Text("ÖZEL ANDROID İZİNLERİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
                                    Text("Parlaklık ve ekran zaman aşımı için sistem ayarlarını değiştirme; Rahatsız Etmeyin için bildirim politikası erişimi gerekir. Android izin ekranını açar, izni sen verirsin.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { onPhoneAction("write_settings") }, modifier = Modifier.fillMaxWidth()) { Text("Sistem ayarlarını değiştirme izni") }
                                    OutlinedButton(onClick = { onPhoneAction("dnd_access") }, modifier = Modifier.fillMaxWidth()) { Text("Rahatsız Etmeyin erişimi") }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("PİL", Icons.Default.BatteryChargingFull, Modifier.weight(1f)) { onPhoneAction("battery") }
                                    MiniModule("KONUM", Icons.Default.LocationOn, Modifier.weight(1f)) { onPhoneAction("location") }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("UYGULAMALAR", Icons.Default.Apps, Modifier.weight(1f)) { onPhoneAction("apps") }
                                    MiniModule("İZİNLER", Icons.Default.Security, Modifier.weight(1f)) { onPhoneAction("permissions") }
                                }
                            }
                            item { SectionHeading("JARVIS MODÜLLERİ", "Kontrol merkezinden eriş") }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("BİLDİRİMLER", Icons.Default.Notifications, Modifier.weight(1f)) { page = "notifications" }
                                    MiniModule("KİŞİSEL HAFIZA", Icons.Default.Psychology, Modifier.weight(1f)) { page = "memory" }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    MiniModule("BELGE ANALİZİ", Icons.Default.DocumentScanner, Modifier.weight(1f)) { page = "documents" }
                                    MiniModule("AI ROUTER", Icons.Default.AccountTree, Modifier.weight(1f)) { page = "router" }
                                }
                            }
                            item {
                                HudCard {
                                    Text("GÜVENLİ DURDURMA", color = JarvisDanger, fontWeight = FontWeight.Bold)
                                    Text("Bu kontrol şu anda JARVIS'in sesli yanıtını durdurur. Android'in başka uygulamalardaki işlemlerini zorla sonlandırmaz.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = onStopSpeaking, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.StopCircle, null, tint = JarvisDanger)
                                        Spacer(Modifier.width(8.dp))
                                        Text("JARVIS SESİNİ DURDUR", color = JarvisDanger)
                                    }
                                }
                            }
                        }
                        "modules" -> ModuleHubScreen(onNavigate = { destination -> page = destination })
                        "knowledge" -> KnowledgeCenterScreen()
                        "evolution" -> EvolutionCenterScreen(
                            onAskAi = { prompt, onResult -> onSendPress(prompt, onResult) }
                        )
                        "router" -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            item {
                                HudCard {
                                    Text("HIZLI SAĞLAYICI PROFİLLERİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
                                    Text("Bu profiller yalnızca API adresi ve model alanlarını doldurur. Her sağlayıcı için kendi API anahtarın ve uygun model erişimin gerekir.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { baseUrl = "https://openrouter.ai/api/v1"; model = "openrouter/free" }, modifier = Modifier.weight(1f)) { Text("OpenRouter") }
                                        OutlinedButton(onClick = { baseUrl = "https://api.groq.com/openai/v1"; model = "llama-3.3-70b-versatile" }, modifier = Modifier.weight(1f)) { Text("Groq") }
                                    }
                                    OutlinedButton(onClick = { baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"; model = "gemini-2.0-flash" }, modifier = Modifier.fillMaxWidth()) { Text("Gemini OpenAI uyumlu uç noktası") }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { baseUrl = "https://router.huggingface.co/v1"; model = "meta-llama/Llama-3.1-8B-Instruct" }, modifier = Modifier.weight(1f)) { Text("Hugging Face") }
                                        OutlinedButton(onClick = { baseUrl = "http://127.0.0.1:11434/v1"; model = "llama3.2" }, modifier = Modifier.weight(1f)) { Text("Yerel Ollama") }
                                    }
                                    Text("Hugging Face için HF token ve erişimi açık model gerekir. Yerel Ollama profili yalnızca aynı telefondaki localhost servisine bağlanır; telefonda Ollama/uyumlu sunucu ayrıca çalışıyor olmalıdır.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            item {
                                HudCard {
                                    Text("SAĞLAYICI MODEL KATALOĞU", color = JarvisCyan, fontWeight = FontWeight.Bold)
                                    Text("Seçili birincil API adresinin /models uç noktasından erişilebilir modelleri alır. Sağlayıcı bu uç noktayı desteklemiyorsa model adını elle girebilirsin.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(
                                        onClick = {
                                            modelListStatus = "Model listesi alınıyor..."
                                            availableModels = emptyList()
                                            modelScope.launch {
                                                runCatching { JarvisAiClient.listModels(apiKey, baseUrl) }
                                                    .onSuccess {
                                                        availableModels = it
                                                        modelListStatus = "${it.size} model bulundu. Seçmek için adına dokun."
                                                    }
                                                    .onFailure {
                                                        modelListStatus = "Model listesi alınamadı: ${it.message ?: "uç nokta desteklenmiyor"}"
                                                    }
                                            }
                                        },
                                        enabled = apiKey.isNotBlank() && baseUrl.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Modelleri getir")
                                    }
                                    Text(modelListStatus, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                                    availableModels.take(30).forEach { available ->
                                        TextButton(onClick = { model = available }, modifier = Modifier.fillMaxWidth()) {
                                            Text(if (model == available) "✓  " + available else available, color = if (model == available) JarvisCyan else JarvisText)
                                        }
                                    }
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
                                OutlinedButton(onClick = {
                                    apiTestStatus = "Girilen sağlayıcı ayarlarıyla doğrudan bağlantı deneniyor..."
                                    onTestAiConnection(apiKey, model, baseUrl, backupKey, backupModel, backupUrl) { success, detail ->
                                        apiTestStatus = if (success) "BAĞLANTI BAŞARILI • $detail" else "BAĞLANTI BAŞARISIZ • $detail"
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.width(8.dp)); Text("AI bağlantısını test et")
                                }
                                Text(apiTestStatus, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            item { Text("Anahtarlar cihazdaki şifreli tercihler alanında saklanır. Kota bilgisi servis sağlayıcıdan gelmediği sürece tahmin edilmez.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                            item { Divider(color = JarvisCyan.copy(alpha = .22f)) }
                            item { OutlinedButton(onClick = onStopSpeaking, Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Text("Sesli yanıtı durdur") } }
                        }
                        else -> { }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showStartup,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(420)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(JarvisAccent.copy(alpha = .30f), JarvisBgDeep, JarvisBgDeep))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(
                            Modifier.size(88.dp).clip(CircleShape)
                                .background(JarvisCyan.copy(alpha = .08f))
                                .border(1.dp, JarvisCyan.copy(alpha = .60f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, null, tint = JarvisCyan, modifier = Modifier.size(46.dp))
                        }
                        Text("J.A.R.V.I.S.", color = JarvisText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("STARK INDUSTRIES  •  MARK VII", color = JarvisCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("ARC REACTOR INITIALIZING", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
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
                Text("ARC REACTOR  //  MARK VII", color = JarvisCyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("STARK NEURAL SYSTEM  •  AI ROUTING STANDBY", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onMicPress, colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisBgDeep), shape = CircleShape) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("JARVIS İLE KONUŞ")
                }
            }
        }
    }
}

@Composable
fun HudCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().border(1.dp, JarvisCyan.copy(alpha = .20f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = JarvisPanel.copy(alpha = .94f))) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable
fun SectionHeading(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker, color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(title, color = JarvisText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusLine(name: String, status: String, ready: Boolean) {
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
fun EmptyState(title: String, detail: String) {
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
    val network: String,
    val charging: Boolean
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
        StatusLine("Şarj durumu", if (snapshot.charging) "ŞARJ OLUYOR" else "PİLDE", snapshot.charging)
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

@Composable
private fun PhoneActionHistoryPanel() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(PhoneActionHistory.read(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            entries = PhoneActionHistory.read(context)
            kotlinx.coroutines.delay(2500)
        }
    }
    HudCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = JarvisCyan)
            Spacer(Modifier.width(9.dp))
            Text("SON SİSTEM İŞLEMLERİ", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                PhoneActionHistory.clear(context)
                entries = emptyList()
            }) { Text("Temizle", color = JarvisTextDim) }
        }
        if (entries.isEmpty()) {
            Text("Henüz kayıtlı sistem işlemi yok.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
        } else {
            entries.take(5).forEachIndexed { index, entry ->
                if (index > 0) Divider(color = JarvisCyan.copy(alpha = .12f))
                Text(entry, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Kayıtlar bu cihazda tutulur; en fazla 30 işlem saklanır.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
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
    val stat = StatFs(android.os.Environment.getDataDirectory().absolutePath)
    val totalStorage = stat.totalBytes
    val usedStorage = (totalStorage - stat.availableBytes).coerceAtLeast(0L)
    val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val chargeStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargeStatus == BatteryManager.BATTERY_STATUS_FULL
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity?.activeNetwork
    val caps = connectivity?.getNetworkCapabilities(network)
    val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return DeviceSnapshot(battery, usedRam, totalRam, usedStorage, totalStorage, if (connected) "BAĞLI" else "ÇEVRİMDIŞI", charging)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
    else String.format(java.util.Locale.getDefault(), "%.0f MB", bytes / (1024.0 * 1024.0))
}


private data class NotificationItem(
    val key: String,
    val title: String,
    val text: String,
    val canReply: Boolean
)

private fun readJarvisNotifications(context: Context): List<NotificationItem> {
    val raw = com.jarvis.assistant.core.notifications.NotificationStore.read(context).toString()
    return runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").ifBlank { item.optString("package") }
            val body = item.optString("text")
            NotificationItem(
                key = item.optString("key"),
                title = title,
                text = body,
                canReply = item.optBoolean("canReply")
            ).takeIf { it.key.isNotBlank() && (title.isNotBlank() || body.isNotBlank()) }
        }.distinctBy { it.key }
    }.getOrDefault(emptyList())
}
