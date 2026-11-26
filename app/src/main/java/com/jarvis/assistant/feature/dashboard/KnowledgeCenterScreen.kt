package com.jarvis.assistant.feature.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.documents.DocumentAnalyzer
import com.jarvis.assistant.core.knowledge.KnowledgeDocument
import com.jarvis.assistant.core.knowledge.KnowledgeStore
import com.jarvis.assistant.core.ui.theme.JarvisCyan
import com.jarvis.assistant.core.ui.theme.JarvisPanel
import com.jarvis.assistant.core.ui.theme.JarvisText
import com.jarvis.assistant.core.ui.theme.JarvisTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KnowledgeCenterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf(KnowledgeStore.list(context)) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("PDF, görsel ve metin dosyalarını kişisel bilgi kütüphanene ekle. İlgili kısa bölümler, soru sorulduğunda yapılandırdığın AI sağlayıcısına bağlam olarak gönderilebilir.") }
    var importing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<Pair<KnowledgeDocument, String>>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            status = "${uris.size} dosya inceleniyor..."
            scope.launch {
                var saved = 0
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val name = runCatching {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: "Belge"
                    val extracted = runCatching {
                        withContext(Dispatchers.IO) {
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            if (mime == "application/pdf" || name.endsWith(".pdf", true) ||
                                mime.startsWith("image/")) {
                                DocumentAnalyzer.analyze(context, uri)
                            } else {
                                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(60_000) }.orEmpty()
                            }
                        }
                    }.getOrDefault("")
                    if (extracted.isNotBlank() && KnowledgeStore.save(context, name, extracted)) saved++
                }
                documents = KnowledgeStore.list(context)
                importing = false
                status = if (saved > 0) "$saved belge şifreli yerel kütüphaneye eklendi." else "Dosyalar kaydedilemedi veya okunabilir metin bulunamadı."
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 28.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("STARK KNOWLEDGE ENGINE", color = JarvisCyan, fontWeight = FontWeight.Bold)
                Text("Bilgi kütüphanesi", color = JarvisText, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Eklediğin belgeler cihazda şifreli saklanır. JARVIS, soruyla ilgili bölümleri bulup AI yanıtına bağlam olarak ekler; belgeyi kendiliğinden internete yüklemez.", color = JarvisTextDim)
            }
        }
        item {
            HudCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MenuBook, tint = JarvisCyan, contentDescription = null)
                    Text("YEREL BİLGİ DEPOSU  •  ${documents.size}/50", color = JarvisText, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*", "text/*", "application/json")) },
                    enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text("  PDF / Görsel / Metin ekle")
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = JarvisCyan)
                Text(status, color = JarvisTextDim)
            }
        }
        item {
            HudCard {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kütüphanede ara") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Button(onClick = { results = KnowledgeStore.search(context, query, 5) },
                    enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Yerel bilgide ara") }
            }
        }
        if (results.isNotEmpty()) {
            item { Text("ARAMA SONUÇLARI", color = JarvisCyan, fontWeight = FontWeight.Bold) }
            items(results, key = { "result-" + it.first.id }) { (doc, excerpt) ->
                HudCard {
                    Text(doc.title, color = JarvisText, fontWeight = FontWeight.Bold)
                    Text(excerpt, color = JarvisTextDim)
                }
            }
        }
        item { Text("KAYITLI BELGELER", color = JarvisCyan, fontWeight = FontWeight.Bold) }
        if (documents.isEmpty()) item { EmptyState("Kütüphane henüz boş", "Ders notları, teknik belgeler, kullanım kılavuzları veya kendi metin dosyalarını ekleyebilirsin.") }
        items(documents, key = { it.id }) { doc ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisPanel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(doc.title, color = JarvisText, fontWeight = FontWeight.Bold)
                    Text("${doc.text.length} karakter • şifreli yerel kayıt", color = JarvisTextDim, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    TextButton(onClick = {
                        KnowledgeStore.delete(context, doc.id)
                        documents = KnowledgeStore.list(context)
                        results = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, null); Text("Kütüphaneden sil")
                    }
                }
            }
        }
    }
}
