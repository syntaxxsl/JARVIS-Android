package com.jarvis.assistant.feature.dashboard

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

private data class MissionStep(val title: String, val result: String = "", val state: String = "BEKLEMEDE")

@Composable
fun MissionControlScreen(onSendPress: (String, (String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf("") }
    var planning by remember { mutableStateOf(false) }
    var executing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Görev hedefini yaz ve önce plan oluştur.") }
    var runToken by remember { mutableStateOf(0) }
    val steps = remember { mutableStateListOf<MissionStep>() }
    val history = remember { mutableStateListOf<String>() }

    fun saveHistory(line: String) {
        val prefs = context.getSharedPreferences("jarvis_mission_control", Context.MODE_PRIVATE)
        val stored = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        stored.put(line.take(1200))
        while (stored.length() > 20) stored.remove(0)
        prefs.edit().putString("history", stored.toString()).apply()
        history.clear()
        for (i in 0 until stored.length()) history.add(stored.optString(i))
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val stored = runCatching {
            JSONArray(context.getSharedPreferences("jarvis_mission_control", Context.MODE_PRIVATE).getString("history", "[]"))
        }.getOrDefault(JSONArray())
        for (i in 0 until stored.length()) history.add(stored.optString(i))
    }

    suspend fun askJarvis(prompt: String): String? =
        withTimeoutOrNull(120_000L) {
            suspendCancellableCoroutine { continuation ->
                onSendPress(prompt) { answer ->
                    if (continuation.isActive) continuation.resume(answer)
                }
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MissionSectionHeading("STARK MISSION CONTROL", "Hedefleri planla ve kontrollü yürüt")
            Text(
                "JARVIS hedefi en fazla 5 alt göreve böler. Her adım AI üzerinden sırayla çalışır; telefon ayarları, mesaj gönderme veya dosya değiştirme gibi harici işlemler otomatik yapılmaz.",
                color = JarvisTextDim
            )
        }
        item {
            MissionHudCard {
                Text("GÖREV HEDEFİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("Örn. JARVIS uygulamasının güvenlik ve AI mimarisini değerlendir...") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val target = goal.trim()
                            if (target.isNotBlank() && !planning && !executing) {
                                planning = true
                                status = "JARVIS görev planını hazırlıyor..."
                                steps.clear()
                                val token = ++runToken
                                scope.launch {
                                    val answer = askJarvis(
                                        "Sen JARVIS Mission Planner ajanısın. Kullanıcının hedefini analiz et ve bunu en fazla 5 küçük, sıralı, uygulanabilir alt göreve böl. Yalnızca numaralı liste döndür; her satır kısa bir görev olsun. Harici işlemleri yapıldığını iddia etme, telefon/mesaj/dosya işlemlerini yalnızca planla. Hedef: $target"
                                    )
                                    if (token == runToken) {
                                        planning = false
                                        if (answer.isNullOrBlank()) {
                                            status = "Plan alınamadı. Bağlantıyı kontrol edip tekrar dene."
                                        } else {
                                            val parsed = answer.lines()
                                                .map { it.trim().replace(Regex("^\\s*(?:[-*]|\\d+[.)])\\s*"), "") }
                                                .filter { it.length > 4 }
                                                .distinct()
                                                .take(5)
                                            if (parsed.isEmpty()) {
                                                steps.add(MissionStep("Planı incele: $answer"))
                                            } else {
                                                parsed.forEach { steps.add(MissionStep(it)) }
                                            }
                                            status = "Plan hazır. Yürütmeyi başlatmadan önce adımları kontrol et."
                                            saveHistory("HEDEF: $target\nPLAN:\n" + steps.joinToString("\n") { "• " + it.title })
                                        }
                                    }
                                }
                            }
                        },
                        enabled = goal.isNotBlank() && !planning && !executing,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.AutoAwesome, null)
                        Text(" Plan oluştur")
                    }
                    TextButton(
                        onClick = {
                            runToken++
                            planning = false
                            executing = false
                            steps.indices.forEach { index -> if (steps[index].state == "ÇALIŞIYOR") steps[index] = steps[index].copy(state = "DURDURULDU") }
                            status = "Görev akışı durduruldu. Devam eden yanıt gelse bile sonraki adımlar başlatılmayacak."
                        },
                        enabled = planning || executing
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Stop, null)
                        Text("Durdur")
                    }
                }
            }
        }
        if (planning || executing) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = JarvisCyan) }
        if (steps.isNotEmpty()) {
            item {
                HudCard {
                    Text("GÖREV PLANI", color = JarvisCyan, fontWeight = FontWeight.Bold)
                    Text(status, color = JarvisTextDim)
                    steps.forEachIndexed { index, step ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = JarvisPanel)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("\${index + 1}. \${step.title}", color = JarvisText, fontWeight = FontWeight.SemiBold)
                                Text(step.state, color = JarvisCyan, fontWeight = FontWeight.Bold)
                                if (step.result.isNotBlank()) Text(step.result, color = JarvisTextDim)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (!executing && !planning && steps.isNotEmpty()) {
                                executing = true
                                val token = ++runToken
                                val target = goal.trim()
                                scope.launch {
                                    for (index in steps.indices) {
                                        if (token != runToken) break
                                        val step = steps[index]
                                        steps[index] = step.copy(state = "ÇALIŞIYOR")
                                        status = "Adım \${index + 1}/\${steps.size} yürütülüyor..."
                                        val result = askJarvis(
                                            "Sen JARVIS görev ajanısın. Genel hedef: $target\nŞu anki alt görev: \${step.title}\nBu adımı gerçekleştirirken yalnızca sahip olduğun bilgi ve metin üretme yeteneğini kullan. Harici araç, dosya, GitHub veya telefon işlemi yaptığını iddia etme. Gereken yerde uygulanabilir sonuç, kod/öneri veya net sınırlama sun. Yanıtını kısa ama yeterince ayrıntılı ver."
                                        )
                                        if (token != runToken) break
                                        if (result.isNullOrBlank()) {
                                            steps[index] = step.copy(state = "HATA / ZAMAN AŞIMI", result = "Yanıt alınamadı veya istek zaman aşımına uğradı.")
                                        } else {
                                            steps[index] = step.copy(state = "TAMAMLANDI", result = result.take(5000))
                                        }
                                    }
                                    if (token == runToken) {
                                        executing = false
                                        val summary = steps.joinToString("\n\n") { "\${it.title}\n\${it.state}\n\${it.result}" }
                                        status = "Görev akışı sona erdi. Sonuçları ve sınırlamaları gözden geçir."
                                        saveHistory("HEDEF: $target\nSONUÇ:\n$summary")
                                    }
                                }
                            }
                        },
                        enabled = steps.isNotEmpty() && !planning && !executing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, null)
                        Text(" Planı yürüt")
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionHeading("SON GÖREV KAYITLARI", "Bu cihazda saklanan geçmiş") }
            itemsIndexed(history.reversed().take(8)) { _, entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisPanel)
                ) { Text(entry, color = JarvisTextDim, modifier = Modifier.padding(12.dp)) }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Not: Bu merkez AI adımlarını sırayla çalıştırır. Gerçek GitHub değişikliği, mesaj gönderimi veya sistem işlemi için ayrı araç entegrasyonu ve kullanıcı onayı gerekir.", color = Color.Gray)
        }
    }
}

@Composable
private fun MissionSectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = JarvisCyan, fontWeight = FontWeight.Bold)
        Text(subtitle, color = JarvisTextDim)
    }
}

@Composable
private fun MissionHudCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisPanel)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}
