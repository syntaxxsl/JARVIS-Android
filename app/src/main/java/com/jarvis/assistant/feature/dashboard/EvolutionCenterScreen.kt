package com.jarvis.assistant.feature.dashboard

import android.content.Context
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisSuccess
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private data class EvolutionFile(val uri: Uri, val name: String, val text: String)
private data class Diagnostic(val name: String, val detail: String, val passed: Boolean)
private data class BuildRun(val number: Int, val status: String, val conclusion: String, val sha: String, val url: String)
private data class ApkRelease(val title: String, val tag: String, val published: String, val assetName: String, val downloadUrl: String, val releaseUrl: String)
private data class RepoSourceFile(val path: String, val size: Int, val downloadUrl: String)

@Composable
fun EvolutionCenterScreen(
    onAskAi: (String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { mutableStateListOf<EvolutionFile>() }
    val history = remember { mutableStateListOf<String>() }
    var diagnostics by remember { mutableStateOf(emptyList<Diagnostic>()) }
    var selectedFile by remember { mutableStateOf<EvolutionFile?>(null) }
    var report by remember { mutableStateOf("Henüz kod kalitesi taraması yapılmadı. Önce proje dosyalarını seç.") }
    var diffReport by remember { mutableStateOf("Karşılaştırmak için en az iki metin/kod dosyası seç.") }
    var scanning by remember { mutableStateOf(false) }
    var aiReview by remember { mutableStateOf("Henüz AI kod incelemesi yapılmadı.") }
    var aiReviewLoading by remember { mutableStateOf(false) }
    var buildRuns by remember { mutableStateOf(emptyList<BuildRun>()) }
    var buildHistoryStatus by remember { mutableStateOf("GitHub Actions geçmişi yükleniyor...") }
    var apkReleases by remember { mutableStateOf(emptyList<ApkRelease>()) }
    var releaseHistoryStatus by remember { mutableStateOf("APK sürüm arşivi yükleniyor...") }
    var repoFiles by remember { mutableStateOf(emptyList<RepoSourceFile>()) }
    var repoStatus by remember { mutableStateOf("GitHub kaynak ağacı yükleniyor...") }
    var repoFilter by remember { mutableStateOf("") }
    var repoPreviewPath by remember { mutableStateOf("") }
    var repoPreview by remember { mutableStateOf("") }
    var repoLoading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = queryDisplayName(context, uri)
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(1_000_000) }.orEmpty()
            }.getOrDefault("")
            if (text.isNotBlank() && files.none { it.uri == uri }) files.add(EvolutionFile(uri, name, text))
        }
        if (files.size >= 2) {
            val a = files[files.lastIndex - 1]
            val b = files[files.lastIndex]
            diffReport = compareText(a, b)
        }
    }

    LaunchedEffect(Unit) {
        val version = runCatching {
            val p: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${p.versionName ?: "?"} (${p.longVersionCodeCompat()})"
        }.getOrDefault("Bilinmiyor")
        val prefs = context.getSharedPreferences("jarvis_evolution", Context.MODE_PRIVATE)
        val now = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val snapshot = "$now • JARVIS $version"
        val stored = runCatching { JSONArray(prefs.getString("versions", "[]")) }.getOrNull() ?: JSONArray()
        if ((0 until stored.length()).none { stored.optString(it).contains(version) }) stored.put(snapshot)
        while (stored.length() > 20) stored.remove(0)
        prefs.edit().putString("versions", stored.toString()).apply()
        history.clear()
        for (i in 0 until stored.length()) history.add(stored.optString(i))
        diagnostics = runDiagnostics(context)
        val fetchedRuns = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val connection = java.net.URL("https://api.github.com/repos/syntaxxsl/JARVIS-Android/actions/runs?per_page=8").openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                try {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = org.json.JSONObject(body).optJSONArray("workflow_runs") ?: org.json.JSONArray()
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.optJSONObject(index) ?: return@mapNotNull null
                        BuildRun(
                            number = item.optInt("run_number"),
                            status = item.optString("status"),
                            conclusion = item.optString("conclusion", ""),
                            sha = item.optString("head_sha").take(7),
                            url = item.optString("html_url")
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
        fetchedRuns.onSuccess {
            buildRuns = it
            buildHistoryStatus = if (it.isEmpty()) "Henüz GitHub Actions derlemesi bulunamadı." else "Son ${it.size} GitHub Actions çalışması."
        }.onFailure {
            buildHistoryStatus = "Derleme geçmişi alınamadı: ${it.message ?: "ağ hatası"}"
        }
    }

    LaunchedEffect(Unit) {
        val fetched = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val connection = java.net.URL("https://api.github.com/repos/syntaxxsl/JARVIS-Android/releases?per_page=12").openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = org.json.JSONArray(body)
                    buildList {
                        for (i in 0 until array.length()) {
                            val release = array.optJSONObject(i) ?: continue
                            val assets = release.optJSONArray("assets") ?: org.json.JSONArray()
                            for (j in 0 until assets.length()) {
                                val asset = assets.optJSONObject(j) ?: continue
                                val fileName = asset.optString("name")
                                if (fileName.endsWith(".apk", true)) {
                                    add(ApkRelease(
                                        title = release.optString("name", release.optString("tag_name")),
                                        tag = release.optString("tag_name"),
                                        published = release.optString("published_at").take(10),
                                        assetName = fileName,
                                        downloadUrl = asset.optString("browser_download_url"),
                                        releaseUrl = release.optString("html_url")
                                    ))
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
        fetched.onSuccess {
            apkReleases = it
            releaseHistoryStatus = if (it.isEmpty()) "Henüz yayımlanmış APK arşivi yok. Yeni başarılı derlemeler otomatik arşivlenecek." else "${it.size} indirilebilir APK sürümü bulundu."
        }.onFailure {
            releaseHistoryStatus = "APK arşivi alınamadı: ${it.message ?: "ağ hatası"}"
        }
    }

    LaunchedEffect(Unit) {
        val result = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val connection = java.net.URL("https://api.github.com/repos/syntaxxsl/JARVIS-Android/git/trees/main?recursive=1").openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val tree = org.json.JSONObject(body).optJSONArray("tree") ?: org.json.JSONArray()
                    buildList {
                        for (i in 0 until tree.length()) {
                            val item = tree.optJSONObject(i) ?: continue
                            if (item.optString("type") == "blob") {
                                add(RepoSourceFile(item.optString("path"), item.optInt("size"), item.optString("url")))
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
        result.onSuccess {
            repoFiles = it
            repoStatus = if (it.isEmpty()) "Kaynak dosyası bulunamadı." else "main dalında ${it.size} kaynak dosyası bulundu. Gezgin salt okunurdur."
        }.onFailure {
            repoStatus = "GitHub kaynak ağacı alınamadı: ${it.message ?: "ağ hatası"}"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeading("STARK INDUSTRIES // EVOLUTION", "JARVIS gelişim merkezi")
            Text("Proje analizi, kod inceleme, değişiklik karşılaştırma ve sürüm takibi.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.CheckCircle, null, tint = JarvisCyan)
                    Text("SİSTEM ÖZ DENETİMİ", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                diagnostics.forEach { d ->
                    StatusLine(d.name, if (d.passed) "HAZIR" else "KONTROL GEREKİYOR", d.passed)
                    Text(d.detail, color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { diagnostics = runDiagnostics(context) }, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh, null); Text(" Testleri yeniden çalıştır")
                }
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.Code, null, tint = JarvisCyan)
                    Text("GITHUB KAYNAK GEZGİNİ", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Text("syntaxxsl/JARVIS-Android • main dalı. Gerçek GitHub dosya ağacını gösterir; seçtiğin kaynak kodunu uygulama içinde salt okunur önizler.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                Text(repoStatus, color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(repoFilter, { repoFilter = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Dosya veya klasör ara") }, singleLine = true)
                val visibleFiles = repoFiles.filter { it.path.contains(repoFilter, true) }.take(80)
                visibleFiles.forEach { file ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(file.path, color = JarvisText, style = MaterialTheme.typography.bodySmall)
                            Text("${file.size} bayt", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                repoLoading = true
                                repoPreviewPath = file.path
                                repoPreview = "Kaynak dosyası yükleniyor..."
                                scope.launch {
                                    val loaded = runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val rawUrl = "https://raw.githubusercontent.com/syntaxxsl/JARVIS-Android/main/" +
                                                file.path.split("/").joinToString("/") { segment ->
                                                    java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                                                }
                                            val connection = java.net.URL(rawUrl).openConnection() as java.net.HttpURLConnection
                                            connection.connectTimeout = 8000
                                            connection.readTimeout = 10000
                                            try { connection.inputStream.bufferedReader().use { it.readText().take(20000) } }
                                            finally { connection.disconnect() }
                                        }
                                    }
                                    repoPreview = loaded.getOrElse { "Dosya önizlemesi alınamadı: ${it.message ?: "ağ hatası"}" }
                                    repoLoading = false
                                }
                            },
                            enabled = !repoLoading,
                            modifier = Modifier.padding(start = 6.dp)
                        ) { Text("GÖR") }
                    }
                }
                if (repoPreviewPath.isNotBlank()) {
                    Divider(color = JarvisCyan.copy(alpha = .20f))
                    Text(repoPreviewPath, color = JarvisCyan, fontWeight = FontWeight.Bold)
                    Text(repoPreview, color = JarvisText, style = MaterialTheme.typography.labelSmall)
                    Text("Önizleme salt okunurdur; kaynak kodunu değiştirme veya GitHub'a gönderme yapmaz.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/syntaxxsl/JARVIS-Android/tree/main"))) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Depoyu GitHub'da aç") }
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.FolderOpen, null, tint = JarvisCyan)
                    Text("CİHAZDAN SEÇİLEN DOSYALAR", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Text("Android dosya seçicisiyle proje klasöründen Kotlin, Java, XML, Gradle veya metin dosyalarını seç. JARVIS seçtiğin dosyaları cihaz içinde inceler.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { picker.launch(arrayOf("text/*", "application/xml", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.FolderOpen, null); Text(" Proje dosyalarını seç")
                }
                Text("Seçilen dosya: ${files.size}", color = JarvisCyan, fontWeight = FontWeight.Bold)
                files.forEach { file ->
                    Text(file.name, color = JarvisText, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                    if (selectedFile == file) {
                        Text(file.text.take(1800), color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { selectedFile = if (selectedFile == file) null else file }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedFile == file) "Önizlemeyi kapat: ${file.name}" else "Önizle: ${file.name}")
                    }
                }
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.BugReport, null, tint = JarvisCyan)
                    Text("KOD KALİTESİ VE HATA RAPORU", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Text("Seçilen kaynaklarda TODO/FIXME işaretleri, aşırı uzun satırlar ve olası gömülü anahtar kalıpları için yerel statik tarama.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    scanning = true
                    report = runCodeAudit(files.toList())
                    scanning = false
                }, enabled = files.isNotEmpty() && !scanning, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.Code, null); Text(" Kodu tara")
                }
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(report, color = JarvisText, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.CompareArrows, null, tint = JarvisCyan)
                    Text("KOD DEĞİŞİKLİKLERİNİ KARŞILAŞTIR", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Text("Son seçilen iki dosyanın satır bazlı fark özeti.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                Text(diffReport, color = JarvisText, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (files.size >= 2) diffReport = compareText(files[files.lastIndex - 1], files.last())
                }, enabled = files.size >= 2, modifier = Modifier.fillMaxWidth()) { Text("Son iki dosyayı karşılaştır") }
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.Lightbulb, null, tint = JarvisCyan)
                    Text("AI GELİŞTİRME ÖNERİLERİ", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                val suggestions = buildList {
                    if (diagnostics.any { !it.passed }) add("Eksik Android izinlerini ve sistem erişimlerini Ayarlar ekranından gözden geçir.")
                    if (files.isEmpty()) add("GitHub proje klasörünü seçerek gerçek kaynak kodu üzerinde inceleme başlat.")
                    if (report.contains("TODO") || report.contains("FIXME")) add("Açık TODO/FIXME maddelerini önceliklendirip test senaryolarına dönüştür.")
                    add("AI sağlayıcı anahtarlarını APK içine gömmek yerine güvenli sunucu aracısı üzerinden yönet.")
                    add("Her kod değişikliğinden sonra otomatik derleme, test ve önceki sürüme dönüş paketi üret.")
                }
                suggestions.forEach { Text("• $it", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                Divider(color = JarvisCyan.copy(alpha = .20f))
                Text("AI kod incelemesi", color = JarvisCyan, fontWeight = FontWeight.Bold)
                Text("Seçtiğin dosyaların içeriği, bu düğmeye bastığında yapılandırılmış AI sağlayıcına gönderilir. Gizli anahtar veya özel veri içeren dosyaları seçme.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        aiReviewLoading = true
                        val source = files.take(4).joinToString("\\n\\n") { file ->
                            "DOSYA: ${file.name}\\n" + file.text.take(5000)
                        }
                        val prompt = "JARVIS yazılım geliştirici ajanısın. Aşağıdaki yerel statik tarama raporunu ve kullanıcının seçtiği kod örneklerini incele. Gerçek bulgu ile varsayımı ayır. Kritik hata, güvenlik riski, bakım kalitesi, test önerisi ve uygulanabilir geliştirme planı ver. Kodları kendiliğinden değiştirme.\\n\\nRAPOR:\\n${runCodeAudit(files.toList())}\\n\\nKOD:\\n$source"
                        onAskAi(prompt) { result ->
                            aiReview = result
                            aiReviewLoading = false
                        }
                    },
                    enabled = files.isNotEmpty() && !aiReviewLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Lightbulb, null)
                    Text(if (aiReviewLoading) "AI inceliyor..." else "AI ile kodu incele")
                }
                if (aiReviewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(aiReview, color = JarvisText, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.History, null, tint = JarvisCyan)
                    Text("APK / SÜRÜM GEÇMİŞİ", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                if (history.isEmpty()) Text("Henüz sürüm kaydı yok.", color = JarvisTextDim)
                history.reversed().forEach { Text(it, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall) }
                Text("Cihazda açılan sürüm kayıtları ve GitHub Actions derlemeleri aşağıda ayrı gösterilir.", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                Divider(color = JarvisCyan.copy(alpha = .20f))
                Text("GITHUB ACTIONS / DERLEME GEÇMİŞİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
                Text(buildHistoryStatus, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                buildRuns.forEach { run ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Build #${run.number} • ${run.conclusion.ifBlank { run.status }} • ${run.sha}", color = JarvisText, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(run.url)))
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Derleme ayrıntılarını aç") }
                    }
                }
                Divider(color = JarvisCyan.copy(alpha = .20f))
                Text("İNDİRİLEBİLİR APK SÜRÜMLERİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
                Text(releaseHistoryStatus, color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
                apkReleases.forEach { release ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(release.title, color = JarvisText, fontWeight = FontWeight.Bold)
                        Text("${release.tag} • ${release.published} • ${release.assetName}", color = JarvisTextDim, style = MaterialTheme.typography.labelSmall)
                        Button(onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)))
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Bu APK sürümünü indir") }
                    }
                }
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/syntaxxsl/JARVIS-Android/actions"))) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Tüm APK ve test geçmişini GitHub'da aç") }
            }
        }
        item {
            HudCard {
                Row {
                    androidx.compose.material3.Icon(Icons.Default.Security, null, tint = JarvisCyan)
                    Text("GERİ ALMA / ROLLBACK", color = JarvisText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                Text("Android, kurulu uygulamanın kendi kendine eski sürüme düşürülmesine normal uygulama izinleriyle izin vermez. Güvenli geri alma için önceki imzalı APK'yı GitHub Actions sürüm arşivinden indirip Android kurulumunu kullanıcı olarak onaylamalısın.", color = JarvisTextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun runDiagnostics(context: Context): List<Diagnostic> {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
    val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val microphone = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val notifications = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true
    val accessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(context.packageName) == true
    val storage = runCatching { context.filesDir.canRead() && context.filesDir.canWrite() }.getOrDefault(false)
    return listOf(
        Diagnostic("Uygulama iç depolaması", "Uygulama özel alanı okunabilir/yazılabilir.", storage),
        Diagnostic("İnternet bağlantısı", if (online) "Ağ erişimi mevcut; AI servisi ayrıca test edilmelidir." else "Doğrulanmış internet bağlantısı yok.", online),
        Diagnostic("Mikrofon izni", if (microphone) "Sesli komut izni verilmiş." else "Sesli komut için mikrofon izni gerekli.", microphone),
        Diagnostic("Bildirim erişimi", if (notifications) "Bildirim dinleyicisi izni açık." else "Bildirim özellikleri için özel erişim gerekli.", notifications),
        Diagnostic("Erişilebilirlik servisi", if (accessibility) "Erişilebilirlik servisi etkin." else "Ekran etkileşimi için servis kullanıcı tarafından etkinleştirilmeli.", accessibility)
    )
}

private fun runCodeAudit(files: List<EvolutionFile>): String {
    if (files.isEmpty()) return "Önce kaynak dosyası seç."
    var todos = 0
    var fixmes = 0
    var longLines = 0
    var possibleSecrets = 0
    files.forEach { file ->
        val lines = file.text.lines()
        todos += Regex("TODO", RegexOption.IGNORE_CASE).findAll(file.text).count()
        fixmes += Regex("FIXME", RegexOption.IGNORE_CASE).findAll(file.text).count()
        longLines += lines.count { it.length > 160 }
        possibleSecrets += Regex("(?i)(api[_-]?key|secret|token)\\s*[:=]\\s*[\"'][^\"']{12,}[\"']").findAll(file.text).count()
    }
    return "Taranan dosya: ${files.size}\nTODO: $todos  •  FIXME: $fixmes\n160 karakterden uzun satır: $longLines\nOlası gömülü gizli anahtar eşleşmesi: $possibleSecrets\n\nBu sezgisel tarama tam güvenlik denetimi veya derleyici testi değildir; eşleşmeleri elle doğrula."
}

private fun compareText(a: EvolutionFile, b: EvolutionFile): String {
    val left = a.text.lines()
    val right = b.text.lines()
    val max = maxOf(left.size, right.size)
    var changed = 0
    val samples = mutableListOf<String>()
    for (i in 0 until max) {
        val x = left.getOrNull(i)
        val y = right.getOrNull(i)
        if (x != y) {
            changed++
            if (samples.size < 12) {
                if (x != null) samples.add("- ${i + 1}: ${x.take(120)}")
                if (y != null) samples.add("+ ${i + 1}: ${y.take(120)}")
            }
        }
    }
    return "${a.name}  →  ${b.name}\nSatır sayısı: ${left.size} → ${right.size}\nFarklı satır konumları: $changed\n\n" + samples.joinToString("\n").ifBlank { "Metinler aynı." }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "dosya"
    val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
    return name
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersionCodeCompat(): Long =
    if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()
