package com.jarvis.assistant.feature.dashboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.phone.AppLauncher
import com.jarvis.assistant.core.phone.JarvisAccessibilityService
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim

@Composable
fun AppControlScreen() {
    val context = LocalContext.current
    val apps = remember { AppLauncher.installedLaunchableApps(context) }
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var typedText by remember { mutableStateOf("") }
    var screenText by remember { mutableStateOf("Ekran içeriğini okumak için erişilebilirlik iznini etkinleştir.") }
    var status by remember { mutableStateOf("") }
    val enabled = isJarvisAccessibilityEnabled(context)
    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
    LazyColumn(
        Modifier.fillMaxSize().padding(17.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { SectionHeading("UYGULAMA ZEKÂSI", "Uygulamalarla etkileşim") }
        item {
            HudCard {
                Text("UYGULAMA ERİŞİMİ", color = JarvisCyan, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Yalnızca başlatıcıda görünen uygulamalar listelenir. JARVIS uygulama ekranını okuyup yalnızca senin bastığın kontrollerle tıklama, metin yazma ve kaydırma yapabilir.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                StatusLine("Erişilebilirlik köprüsü", if (enabled) "İZİN VERİLDİ" else "İZİN GEREKLİ", enabled)
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Security, null); Spacer(Modifier.width(8.dp)); Text("JARVIS erişilebilirlik iznini yönet")
                }
            }
        }
        item {
            HudCard {
                Text("EKRAN KONTROLÜ", color = JarvisCyan, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedTextField(target, { target = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Bulunacak metin / alan ipucu") }, singleLine = true)
                OutlinedTextField(typedText, { typedText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Yazılacak metin") }, minLines = 2)
                Button(onClick = {
                    status = if (JarvisAccessibilityService.type(target, typedText)) "Metin alana yazıldı." else "Yazılabilir alan bulunamadı veya erişim etkin değil."
                }, enabled = enabled && typedText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.TouchApp, null); Text("Seçili alana metin yaz")
                }
                OutlinedButton(onClick = {
                    status = if (target.isNotBlank() && JarvisAccessibilityService.click(target)) "Eşleşen öğeye dokunuldu." else "Öğe bulunamadı veya tıklanamadı."
                }, enabled = enabled && target.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.TouchApp, null); Text("Ekranda bu metne dokun")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { status = if (JarvisAccessibilityService.scroll(false)) "Yukarı kaydırıldı." else "Kaydırılabilir alan bulunamadı." }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ArrowUpward, null); Text("Yukarı")
                    }
                    OutlinedButton(onClick = { status = if (JarvisAccessibilityService.scroll(true)) "Aşağı kaydırıldı." else "Kaydırılabilir alan bulunamadı." }, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ArrowDownward, null); Text("Aşağı")
                    }
                }
                OutlinedButton(onClick = {
                    screenText = JarvisAccessibilityService.readScreen()
                    status = "Ekran metni yenilendi."
                }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Visibility, null); Text("Açık ekranın metnini oku")
                }
                if (status.isNotBlank()) Text(status, color = JarvisCyan, style = MaterialTheme.typography.bodySmall)
                Text(screenText, color = JarvisText, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text("TELEFONUNDAKİ UYGULAMALAR (" + apps.size + ")", color = JarvisCyan, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Uygulama ara") }, singleLine = true)
        }
        if (filtered.isEmpty()) item { EmptyState("Uygulama bulunamadı", "Arama ifadesini değiştir.") }
        items(filtered, key = { it.packageName }) { app ->
            Card(
                onClick = {
                    status = if (AppLauncher.launch(context, app.packageName)) app.label + " açıldı." else app.label + " açılamadı."
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisPanel)
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Apps, null, tint = JarvisCyan)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = JarvisText, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(app.packageName, color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("AÇ", color = JarvisCyan, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Text("GÜVENLİK: JARVIS gizlice işlem yapmaz. Ekran erişimi Android ayarlarından ayrıca açılır; mesaj gönderme ve benzeri hassas işlemler için ayrı kullanıcı onayı gerekir.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun isJarvisAccessibilityEnabled(context: Context): Boolean {
    val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return raw.split(':').any { it.contains(context.packageName, ignoreCase = true) && it.contains("JarvisAccessibilityService", ignoreCase = true) }
}
