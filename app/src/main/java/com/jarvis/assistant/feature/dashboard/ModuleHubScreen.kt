package com.jarvis.assistant.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim

private data class HubEntry(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val destination: String
)

@Composable
fun ModuleHubScreen(onNavigate: (String) -> Unit) {
    val groups = listOf(
        Triple("01  /  YAPAY ZEKÂ", "Sohbet, uzman ajanlar ve model bağlantıları", listOf(
            HubEntry("Neural Chat", "JARVIS ile metin ve sesli sohbet", Icons.Default.Forum, "chat"),
            HubEntry("Uzman ajanlar", "Araştırma, yazılım, öğrenme ve yaratıcı", Icons.Default.Hub, "agents"),
            HubEntry("AI Router", "Birincil ve yedek sağlayıcı ayarları", Icons.Default.AccountTree, "router")
        )),
        Triple("02  /  TELEFON & OTOMASYON", "Android izinleriyle çalışan cihaz araçları", listOf(
            HubEntry("Telefon kontrolü", "Uygulamalar ve desteklenen ekran işlemleri", Icons.Default.PhonelinkSetup, "phone"),
            HubEntry("Sistem merkezi", "Canlı cihaz bilgileri ve sistem ayarları", Icons.Default.Tune, "control"),
            HubEntry("Bildirim merkezi", "Bildirimleri gör ve yanıtı onaylayarak gönder", Icons.Default.Notifications, "notifications")
        )),
        Triple("03  /  BİLGİ & HAFIZA", "Kişisel notlar ve belge analizi", listOf(
            HubEntry("Kişisel hafıza", "Cihazda saklanan kayıtları düzenle", Icons.Default.Psychology, "memory"),
            HubEntry("Belge zekâsı", "PDF ve görsellerden metin çıkar", Icons.Default.DocumentScanner, "documents")
        )),
        Triple("04  /  GELİŞTİRME LABORATUVARI", "Kod inceleme, dosya karşılaştırma ve sürüm takibi", listOf(
            HubEntry("JARVIS Evolution", "Tanılama, dosya inceleme, AI kod değerlendirmesi ve Actions geçmişi", Icons.Default.AutoAwesome, "evolution")
        ))
    )

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("STARK MODULE DECK  /  MARK VII", color = JarvisCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Modül merkezi", color = JarvisText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Her yetenek kendi çalışma ekranında açılır. Modüller kategorilere ayrıldı; ana ekranda yalnızca hızlı erişimler bulunur.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(groups.size) { index ->
            val group = groups[index]
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(group.first, color = JarvisCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                Text(group.second, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                group.third.forEach { entry ->
                    Card(
                        onClick = { onNavigate(entry.destination) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = JarvisPanel)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(44.dp).background(JarvisCyan.copy(alpha = .10f), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(entry.icon, contentDescription = null, tint = JarvisCyan)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
            Text("GÜVENLİK NOTU  •  Telefon ve bildirim özellikleri yalnızca Android'in verdiğin izinler ve ilgili uygulamanın desteklediği arayüzler kapsamında çalışır.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}
