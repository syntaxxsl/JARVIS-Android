package com.jarvis.assistant.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim

private data class HubEntry(val title: String, val detail: String, val icon: ImageVector, val destination: String)
private data class HubGroup(val title: String, val detail: String, val entries: List<HubEntry>)

@Composable
fun ModuleHubScreen(onNavigate: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val groups = listOf(
        HubGroup("01  /  YAPAY ZEKÂ", "Sohbet, uzman ajanlar ve akıllı görev akışı", listOf(
            HubEntry("Neural Chat", "JARVIS ile metin ve sesli sohbet", Icons.Default.Forum, "chat"),
            HubEntry("Uzman ajanlar", "Araştırma, yazılım, öğrenme ve yaratıcı", Icons.Default.Hub, "agents"),
            HubEntry("Mission Control", "Hedefleri parçalara ayır ve adım adım yürüt", Icons.Default.AccountTree, "mission"),
            HubEntry("AI Router", "Birincil/yedek sağlayıcı, model kataloğu ve test", Icons.Default.AccountTree, "router")
        )),
        HubGroup("02  /  BİLGİ & HAFIZA", "Belgeler, kişisel hafıza ve yerel arama", listOf(
            HubEntry("Knowledge Engine", "PDF, görsel ve metinlerden kişisel bilgi tabanı", Icons.Default.Book, "knowledge"),
            HubEntry("Kişisel hafıza", "Cihazda saklanan kayıtları düzenle", Icons.Default.Psychology, "memory"),
            HubEntry("Belge zekâsı", "PDF ve görsellerden OCR ile metin çıkar", Icons.Default.DocumentScanner, "documents")
        )),
        HubGroup("03  /  TELEFON & OTOMASYON", "Android izinleriyle çalışan cihaz araçları", listOf(
            HubEntry("Telefon kontrolü", "Uygulamalar ve desteklenen ekran işlemleri", Icons.Default.PhonelinkSetup, "phone"),
            HubEntry("Sistem merkezi", "Canlı cihaz bilgileri ve sistem ayarları", Icons.Default.Tune, "control"),
            HubEntry("Bildirim merkezi", "Bildirimleri gör ve yanıtı onaylayarak gönder", Icons.Default.Notifications, "notifications")
        )),
        HubGroup("04  /  GELİŞTİRME LABORATUVARI", "Kod inceleme, test ve sürüm takibi", listOf(
            HubEntry("JARVIS Evolution", "Tanılama, kod inceleme ve Actions geçmişi", Icons.Default.Code, "evolution")
        ))
    )
    val filtered = groups.map { group ->
        group.copy(entries = group.entries.filter {
            search.isBlank() || (it.title + " " + it.detail + " " + group.title).contains(search, true)
        })
    }.filter { it.entries.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("STARK MODULE DECK  /  MARK VII", color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Yetenek merkezi", color = JarvisText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Modüller işlevlerine göre gruplandı. Arama ile doğrudan istediğin araca ulaş.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Modül ara") }, leadingIcon = { Icon(Icons.Default.Security, null) })
                Text("${groups.sumOf { it.entries.size }} MODÜL  •  ${filtered.sumOf { it.entries.size }} GÖSTERİLİYOR",
                    color = JarvisCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (filtered.isEmpty()) {
            item { EmptyState("Modül bulunamadı", "Arama kelimesini değiştir.") }
        }
        items(filtered, key = { it.title }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.title, color = JarvisCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                Text(group.detail, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                group.entries.forEach { entry ->
                    Card(onClick = { onNavigate(entry.destination) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(42.dp).background(JarvisCyan.copy(alpha = .10f), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(entry.icon, null, tint = JarvisCyan) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.title, color = JarvisText, fontWeight = FontWeight.Bold)
                                Text(entry.detail, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("AÇ  ›", color = JarvisCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Text("GÜVENLİK  •  Telefon ve bildirim işlemleri Android izinleri ve hedef uygulama desteğiyle sınırlıdır.",
                color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
