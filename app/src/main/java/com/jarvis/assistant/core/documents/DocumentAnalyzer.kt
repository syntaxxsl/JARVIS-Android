package com.jarvis.assistant.core.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object DocumentAnalyzer {
    suspend fun analyze(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime == "application/pdf" || uri.toString().endsWith(".pdf", true)) {
            analyzePdf(context, uri)
        } else {
            val image = InputImage.fromFilePath(context, uri)
            recognize(image)
        }
    }

    private suspend fun analyzePdf(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("PDF dosyası açılamadı.")
        descriptor.use { file ->
            PdfRenderer(file).use { renderer ->
                if (renderer.pageCount == 0) return "PDF içinde sayfa bulunamadı."
                val output = StringBuilder()
                val pagesToRead = minOf(renderer.pageCount, MAX_PDF_PAGES)
                for (index in 0 until pagesToRead) {
                    renderer.openPage(index).use { page ->
                        val maxDimension = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = minOf(2.0f, 1800f / maxDimension)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, Matrix(), PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            output.append("\n\n--- SAYFA ${index + 1} ---\n")
                            output.append(recognize(InputImage.fromBitmap(bitmap, 0)))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                if (renderer.pageCount > pagesToRead) {
                    output.append("\n\nNot: Performans için ilk ${MAX_PDF_PAGES} sayfa tarandı.")
                }
                return output.toString().trim().ifBlank { "Belgede okunabilir metin bulunamadı." }
            }
        }
    }

    private suspend fun recognize(image: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    private const val MAX_PDF_PAGES = 8
}
