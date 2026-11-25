package com.ailauncher.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * The photo behind the assistant's messages. A picked photo is copied (scaled to screen size) into
 * the app's private files, so it keeps working after the photo picker's access ends and never
 * needs a storage permission.
 */
@Singleton
class ChatBackgroundStore @Inject constructor(@ApplicationContext private val context: Context) {

    val file: File get() = File(context.filesDir, FILE_NAME)

    /** Changes whenever the photo does (for image caches); null when there is no photo. */
    private val _version = MutableStateFlow(file.takeIf { it.exists() }?.lastModified())
    val version: StateFlow<Long?> = _version.asStateFlow()

    /** Copies the picked image; false if it can't be read. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val maxSide = max(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels).coerceIn(720, 2400)
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            check(tmp.renameTo(file)) { "Could not save the chat background" }
            _version.value = file.lastModified()
        }.isSuccess
    }

    fun clear() {
        file.delete()
        _version.value = null
    }

    private companion object {
        const val FILE_NAME = "chat_background.jpg"
    }
}
