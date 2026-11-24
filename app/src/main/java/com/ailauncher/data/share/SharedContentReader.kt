package com.ailauncher.data.share

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Something shared to the launcher with "Share → AI Launcher". */
data class SharedPayload(
    val kind: Kind,
    /** File name or subject, when there is one. */
    val title: String?,
    /** Plain text (for a PDF: the text extracted on-device; empty when that isn't possible). */
    val text: String,
    /** For a PDF that could not be read, why. */
    val problem: String? = null,
) {
    enum class Kind { TEXT, PDF }
}

/**
 * Reads ACTION_SEND intents (text/plain and application/pdf) entirely on-device. PDF text is
 * extracted with the platform PdfRenderer (Android 15+); nothing is uploaded anywhere.
 */
@Singleton
class SharedContentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun isShare(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND && (intent.type?.startsWith("text/") == true || intent.type == PDF_MIME)

    /** Must run while the sender's temporary URI grant is still valid (i.e. right away). */
    suspend fun read(intent: Intent): SharedPayload? = withContext(Dispatchers.IO) {
        if (!isShare(intent)) return@withContext null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
        if (intent.type == PDF_MIME) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: return@withContext SharedPayload(SharedPayload.Kind.PDF, subject, "", problem = "The PDF couldn't be opened")
            readPdf(uri, subject)
        } else {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (text.isEmpty()) null else SharedPayload(SharedPayload.Kind.TEXT, subject, text.take(MAX_CHARS))
        }
    }

    private fun readPdf(uri: Uri, subject: String?): SharedPayload {
        val name = displayName(uri) ?: subject
        if (!canExtractPdfText()) {
            return SharedPayload(SharedPayload.Kind.PDF, name, "", problem = "Reading PDF text on-device needs Android 15 or newer")
        }
        var copy: File? = null
        return try {
            val descriptor = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            // PdfRenderer needs a seekable file; some apps share a pipe, so copy those to the cache first.
            val seekable = descriptor?.takeIf { it.statSize >= 0 } ?: run {
                descriptor?.close()
                copy = copyToCache(uri) ?: return SharedPayload(SharedPayload.Kind.PDF, name, "", problem = "The PDF is too large or couldn't be read")
                ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            seekable.use { SharedPayload(SharedPayload.Kind.PDF, name, extractText(it)) }
                .let { if (it.text.isBlank()) it.copy(problem = "This PDF has no selectable text (it may be a scan)") else it }
        } catch (e: Exception) {
            Log.w(TAG, "PDF read failed", e)
            SharedPayload(SharedPayload.Kind.PDF, name, "", problem = "The PDF couldn't be read (it may be encrypted)")
        } finally {
            copy?.delete()
        }
    }

    @Suppress("NewApi") // Guarded by canExtractPdfText().
    private fun extractText(descriptor: ParcelFileDescriptor): String {
        val out = StringBuilder()
        PdfRenderer(descriptor).use { renderer ->
            for (index in 0 until minOf(renderer.pageCount, MAX_PAGES)) {
                renderer.openPage(index).use { page ->
                    page.textContents.forEach { content -> out.append(content.text).append('\n') }
                }
                if (out.length >= MAX_CHARS) break
            }
        }
        return out.toString().replace(Regex("""[ \t]+"""), " ").replace(Regex("""\n{3,}"""), "\n\n").trim().take(MAX_CHARS)
    }

    private fun copyToCache(uri: Uri): File? {
        val file = File(context.cacheDir, "shared-${System.nanoTime()}.pdf")
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
        var total = 0L
        input.use { stream ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PDF_BYTES) {
                        file.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return file
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        const val TAG = "SharedContent"
        const val PDF_MIME = "application/pdf"
        const val MAX_CHARS = 20_000
        const val MAX_PAGES = 40
        const val MAX_PDF_BYTES = 40L * 1024 * 1024

        /** PdfRenderer.Page.getTextContents(): API 35, also shipped to older devices as S extension 13. */
        fun canExtractPdfText(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13
    }
}
