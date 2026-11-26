package com.jarvis.assistant.feature.dashboard

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class GhFile(val path: String, val sha: String, val size: Int)
private data class GhBranch(val name: String, val sha: String)
private data class GhRun(val number: Int, val status: String, val conclusion: String, val url: String)
private data class GhPull(val number: Int, val title: String, val state: String, val url: String)

private object JarvisGitHub {
    private const val REPO = "syntaxxsl/JARVIS-Android"
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        "jarvis_github_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun token(context: Context): String = runCatching { prefs(context).getString("token", "").orEmpty() }.getOrDefault("")
    fun saveToken(context: Context, value: String) { prefs(context).edit().putString("token", value.trim()).apply() }
    fun clearToken(context: Context) { prefs(context).edit().remove("token").apply() }

    private fun request(context: Context, method: String, path: String, body: JSONObject? = null): JSONObject {
        val token = token(context)
        require(token.isNotBlank()) { "Önce GitHub erişim anahtarını kaydet." }
        val connection = (URL("https://api.github.com$path").openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrDefault(text)
                throw IllegalStateException("GitHub HTTP $code: $message")
            }
            return if (text.isBlank()) JSONObject()
                else if (text.trimStart().startsWith("[")) JSONObject().put("__array", JSONArray(text))
                else JSONObject(text)
        } finally { connection.disconnect() }
    }

    fun verify(context: Context): String {
        val user = request(context, "GET", "/user").optString("login")
        val repo = request(context, "GET", "/repos/$REPO")
        return "@$user • ${repo.optString("full_name")} • ${repo.optString("default_branch")} dalı"
    }
    fun branches(context: Context): List<GhBranch> {
        val array = request(context, "GET", "/repos/$REPO/branches?per_page=100").optJSONArray("__array") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            GhBranch(item.optString("name"), item.optJSONObject("commit")?.optString("sha").orEmpty())
        }
    }
    fun tree(context: Context, branch: String): List<GhFile> {
        val encoded = URLEncoder.encode(branch, "UTF-8")
        val ref = request(context, "GET", "/repos/$REPO/git/ref/heads/$encoded")
            .optJSONObject("object")?.optString("sha").orEmpty()
        require(ref.isNotBlank()) { "Dal bulunamadı: $branch" }
        val array = request(context, "GET", "/repos/$REPO/git/trees/$ref?recursive=1").optJSONArray("tree") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            if (item.optString("type") != "blob" || item.optLong("size") > 500_000) return@mapNotNull null
            GhFile(item.optString("path"), item.optString("sha"), item.optInt("size"))
        }
    }
    fun readFile(context: Context, path: String, branch: String): Pair<String, String> {
        val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val encodedBranch = URLEncoder.encode(branch, "UTF-8")
        val json = request(context, "GET", "/repos/$REPO/contents/$encodedPath?ref=$encodedBranch")
        val raw = Base64.decode(json.optString("content").replace("\n", ""), Base64.DEFAULT)
        return String(raw, Charsets.UTF_8) to json.optString("sha")
    }
    fun saveFile(context: Context, path: String, content: String, branch: String, sha: String, message: String): String {
        val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = JSONObject().put("message", message).put("content", encoded).put("branch", branch)
        if (sha.isNotBlank()) body.put("sha", sha)
        return request(context, "PUT", "/repos/$REPO/contents/$encodedPath", body)
            .optJSONObject("commit")?.optString("html_url").orEmpty()
    }
    fun createBranch(context: Context, name: String, source: String): String {
        val encoded = URLEncoder.encode(source, "UTF-8")
        val sha = request(context, "GET", "/repos/$REPO/git/ref/heads/$encoded").optJSONObject("object")?.optString("sha").orEmpty()
        require(sha.isNotBlank()) { "Kaynak dal okunamadı." }
        request(context, "POST", "/repos/$REPO/git/refs", JSONObject().put("ref", "refs/heads/$name").put("sha", sha))
        return name
    }
    fun compare(context: Context, base: String, head: String): String {
        val b = URLEncoder.encode(base, "UTF-8")
        val h = URLEncoder.encode(head, "UTF-8")
        val json = request(context, "GET", "/repos/$REPO/compare/$b...$h")
        val files = json.optJSONArray("files") ?: JSONArray()
        val details = buildString {
            append("Durum: ${json.optString("status")} • ${json.optInt("ahead_by")} commit önde • ${files.length()} dosya değişmiş\n")
            for (i in 0 until minOf(files.length(), 40)) {
                val f = files.optJSONObject(i) ?: continue
                append("${f.optString("status")}  ${f.optString("filename")}  +${f.optInt("additions")} / -${f.optInt("deletions")}\n")
            }
        }
        return details
    }
    fun createPull(context: Context, title: String, body: String, head: String, base: String): String {
        val json = request(context, "POST", "/repos/$REPO/pulls",
            JSONObject().put("title", title).put("body", body).put("head", head).put("base", base).put("draft", true))
        return json.optString("html_url")
    }
    fun runs(context: Context): List<GhRun> {
        val array = request(context, "GET", "/repos/$REPO/actions/runs?per_page=8").optJSONArray("workflow_runs") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            GhRun(item.optInt("run_number"), item.optString("status"), item.optString("conclusion"), item.optString("html_url"))
        }
    }
    fun pulls(context: Context): List<GhPull> {
        val array = request(context, "GET", "/repos/$REPO/pulls?state=all&per_page=10").optJSONArray("__array") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            GhPull(item.optInt("number"), item.optString("title"), item.optString("state"), item.optString("html_url"))
        }
    }
}

@Composable
fun GitHubWorkspacePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(JarvisGitHub.token(context)) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("GitHub hesabını bağlayarak düzenleme ve yayın akışlarını aç.") }
    var busy by remember { mutableStateOf(false) }
    var branches by remember { mutableStateOf(emptyList<GhBranch>()) }
    var selectedBranch by remember { mutableStateOf("main") }
    var branchName by remember { mutableStateOf("jarvis/ai-changes") }
    var files by remember { mutableStateOf(emptyList<GhFile>()) }
    var filter by remember { mutableStateOf("") }
    var selectedPath by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf("") }
    var fileSha by remember { mutableStateOf("") }
    var newFilePath by remember { mutableStateOf("") }
    var newFileContent by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("JARVIS: update source file") }
    var prTitle by remember { mutableStateOf("JARVIS code changes") }
    var prBody by remember { mutableStateOf("JARVIS tarafından hazırlanan değişiklikler. Birleştirmeden önce CI ve kod incelemesini kontrol edin.") }
    var compareReport by remember { mutableStateOf("") }
    var runs by remember { mutableStateOf(emptyList<GhRun>()) }
    var pulls by remember { mutableStateOf(emptyList<GhPull>()) }

    fun perform(label: String, action: suspend () -> String) {
        busy = true
        status = "$label..."
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { action() } }
            status = result.fold({ "$label tamamlandı: $it" }, { "$label başarısız: ${it.message ?: "bilinmeyen hata"}" })
            busy = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GITHUB DEVELOPMENT WORKSPACE", color = JarvisCyan, fontWeight = FontWeight.Bold)
        Text("Gerçek GitHub API bağlantısı • syntaxxsl/JARVIS-Android • token cihazda şifreli saklanır.", color = JarvisTextDim)
        OutlinedTextField(
            value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Fine-grained GitHub token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                JarvisGitHub.saveToken(context, token)
                perform("GitHub hesabı doğrulanıyor") {
                    val identity = JarvisGitHub.verify(context)
                    branches = JarvisGitHub.branches(context)
                    if (branches.none { it.name == selectedBranch }) selectedBranch = branches.firstOrNull()?.name ?: "main"
                    files = JarvisGitHub.tree(context, selectedBranch)
                    runs = JarvisGitHub.runs(context)
                    pulls = JarvisGitHub.pulls(context)
                    connected = true
                    identity
                }
            }, enabled = token.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "Bağlanıyor…" else "Bağlan / doğrula") }
            OutlinedButton(onClick = {
                JarvisGitHub.clearToken(context); token = ""; connected = false
                branches = emptyList(); files = emptyList(); status = "GitHub bağlantısı kesildi; kayıtlı token silindi."
            }) { Text("Bağlantıyı kes") }
        }
        Text("Token yetkileri: Contents (Read and write), Pull requests (Read and write), Actions (Read-only), Metadata (Read-only). Yalnızca bu depoya erişim ver.", color = JarvisTextDim)
        OutlinedButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/settings/personal-access-tokens/new"))) } }, modifier = Modifier.fillMaxWidth()) { Text("GitHub token oluşturma sayfasını aç") }
        Text(status, color = JarvisText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        if (connected) {
            Text("DAL YÖNETİMİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
            OutlinedTextField(selectedBranch, { selectedBranch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Aktif dal adı") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { perform("Dallar yenileniyor") { branches = JarvisGitHub.branches(context); " ${branches.size} dal bulundu." } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Dalları yenile") }
                OutlinedButton(onClick = { perform("Dal oluşturuluyor") { JarvisGitHub.createBranch(context, branchName.trim(), selectedBranch) ; branches = JarvisGitHub.branches(context); selectedBranch = branchName.trim(); files = JarvisGitHub.tree(context, selectedBranch); "Yeni dal: $selectedBranch" } }, enabled = !busy && branchName.matches(Regex("[A-Za-z0-9._/-]{1,100}")) && branches.none { it.name == branchName }, modifier = Modifier.weight(1f)) { Text("Yeni dal oluştur") }
            }
            OutlinedTextField(branchName, { branchName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Yeni dal adı") }, singleLine = true)
            Text("DAL LİSTESİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
            branches.forEach { branch ->
                OutlinedButton(onClick = { selectedBranch = branch.name; perform("Dal dosyaları yükleniyor") { files = JarvisGitHub.tree(context, branch.name); " ${files.size} dosya • ${branch.name}" } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedBranch == branch.name) "✓ " else "") ; Text(branch.name)
                }
            }
            Text("DEPO DOSYALARI / DÜZENLEYİCİ", color = JarvisCyan, fontWeight = FontWeight.Bold)
            OutlinedTextField(filter, { filter = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Dosya ara") }, singleLine = true)
            files.filter { it.path.contains(filter, true) }.take(100).forEach { file ->
                OutlinedButton(onClick = {
                    selectedPath = file.path
                    perform("Dosya açılıyor") {
                        val loaded = JarvisGitHub.readFile(context, file.path, selectedBranch)
                        editor = loaded.first; fileSha = loaded.second
                        "${file.path} • ${editor.length} karakter yüklendi."
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(file.path) }
            }
            if (selectedPath.isNotBlank()) {
                Text("DÜZENLENEN: $selectedPath", color = JarvisCyan, fontWeight = FontWeight.Bold)
                OutlinedTextField(editor, { editor = it }, modifier = Modifier.fillMaxWidth(), minLines = 8, maxLines = 20, label = { Text("Dosya içeriği") })
                OutlinedTextField(commitMessage, { commitMessage = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Commit mesajı") }, singleLine = true)
                Button(onClick = {
                    val path = selectedPath; val data = editor; val sha = fileSha; val branch = selectedBranch; val message = commitMessage
                    perform("Commit gönderiliyor") {
                        val url = JarvisGitHub.saveFile(context, path, data, branch, sha, message)
                        val refreshed = JarvisGitHub.readFile(context, path, branch)
                        fileSha = refreshed.second
                        "Commit kaydedildi. $url"
                    }
                }, enabled = !busy && selectedPath.isNotBlank() && commitMessage.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Değişikliği GitHub'a commit et") }
            }
            Text("YENİ DOSYA OLUŞTUR", color = JarvisCyan, fontWeight = FontWeight.Bold)
            OutlinedTextField(newFilePath, { newFilePath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Depo içi dosya yolu (ör. app/.../NewFeature.kt)") }, singleLine = true)
            OutlinedTextField(newFileContent, { newFileContent = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Yeni dosyanın içeriği") }, minLines = 4, maxLines = 10)
            Button(onClick = {
                val path = newFilePath.trim(); val data = newFileContent; val branch = selectedBranch
                perform("Yeni dosya commit ediliyor") {
                    val url = JarvisGitHub.saveFile(context, path, data, branch, "", "JARVIS: add $path")
                    files = JarvisGitHub.tree(context, branch)
                    newFilePath = ""; newFileContent = ""
                    "Yeni dosya oluşturuldu. $url"
                }
            }, enabled = !busy && newFilePath.isNotBlank() && newFileContent.isNotBlank() && !newFilePath.startsWith("/") && !newFilePath.contains(".."), modifier = Modifier.fillMaxWidth()) { Text("Yeni dosyayı GitHub’a ekle") }

            Text("PULL REQUEST / DEĞİŞİKLİK KARŞILAŞTIRMA", color = JarvisCyan, fontWeight = FontWeight.Bold)
            OutlinedTextField(prTitle, { prTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("PR başlığı") })
            OutlinedTextField(prBody, { prBody = it }, modifier = Modifier.fillMaxWidth(), label = { Text("PR açıklaması") }, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { perform("main ile dal karşılaştırılıyor") { compareReport = JarvisGitHub.compare(context, "main", selectedBranch); compareReport } }, enabled = !busy && selectedBranch != "main", modifier = Modifier.weight(1f)) { Text("main ile karşılaştır") }
                Button(onClick = { perform("Taslak PR açılıyor") { val url = JarvisGitHub.createPull(context, prTitle, prBody, selectedBranch, "main"); pulls = JarvisGitHub.pulls(context); url } }, enabled = !busy && selectedBranch != "main" && prTitle.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Taslak PR aç") }
            }
            if (compareReport.isNotBlank()) Text(compareReport, color = JarvisText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Text("SON PULL REQUEST'LER", color = JarvisCyan, fontWeight = FontWeight.Bold)
            pulls.forEach { pr ->
                Text("#${pr.number} • ${pr.state} • ${pr.title}", color = JarvisText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(pr.url))) } }, modifier = Modifier.fillMaxWidth()) { Text("PR ayrıntılarını aç") }
            }
            Text("GITHUB ACTIONS / TESTLER", color = JarvisCyan, fontWeight = FontWeight.Bold)
            runs.forEach { run ->
                Text("Build #${run.number} • ${run.status} • ${run.conclusion}", color = JarvisText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(run.url))) } }, modifier = Modifier.fillMaxWidth()) { Text("CI çalışmasını aç") }
            }
            OutlinedButton(onClick = { perform("GitHub verileri yenileniyor") { branches = JarvisGitHub.branches(context); files = JarvisGitHub.tree(context, selectedBranch); runs = JarvisGitHub.runs(context); pulls = JarvisGitHub.pulls(context); "Dallar, dosyalar, Actions ve PR'ler güncellendi." } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Tüm GitHub verilerini yenile") }
        } else {
            Text("Bağlantı kurulduğunda dosya gezgini, çevrimiçi düzenleyici, branch, commit, compare, taslak PR ve CI geçmişi açılır. Bu uygulama için GitHub token'ı ayrıca gerekir; ChatGPT bağlantısı otomatik olarak Android'e aktarılmaz.", color = JarvisTextDim)
        }
    }
}
