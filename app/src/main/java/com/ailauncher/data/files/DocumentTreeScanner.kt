package com.ailauncher.data.files

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import com.ailauncher.domain.model.FileEntry

/**
 * Lists the files inside folders the user picked with the system folder picker (persisted
 * Storage Access Framework grants). The Google Play build uses this instead of MediaStore with
 * broad storage permissions. Bounded in file count, depth and time, so a huge photo folder cannot
 * stall the assistant.
 */
class DocumentTreeScanner(private val resolver: ContentResolver) {

    data class Result(val files: List<FileEntry>, val truncated: Boolean)

    /** [trees]: tree URI and the folder's display name (used as the start of each file's path). */
    fun scan(
        trees: List<Pair<Uri, String>>,
        maxFiles: Int = MAX_FILES,
        maxMillis: Long = MAX_MILLIS,
        maxDepth: Int = MAX_DEPTH,
    ): Result {
        val deadline = SystemClock.elapsedRealtime() + maxMillis
        val files = ArrayList<FileEntry>()
        var truncated = false
        for ((tree, rootName) in trees) {
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            val pending = ArrayDeque<Pending>().apply { addLast(Pending(rootId, "$rootName/", 0)) }
            while (pending.isNotEmpty()) {
                if (files.size >= maxFiles || SystemClock.elapsedRealtime() > deadline) {
                    truncated = true
                    break
                }
                val folder = pending.removeLast()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.documentId)
                try {
                    resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(0) ?: continue
                            val name = cursor.getString(1) ?: continue
                            if (name.startsWith(".")) continue // hidden files and folders
                            val mime = cursor.getString(2)
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (folder.depth < maxDepth) pending.addLast(Pending(id, "${folder.path}$name/", folder.depth + 1))
                            } else {
                                files += FileEntry(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                    name = name,
                                    mimeType = mime,
                                    sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                                    modifiedAtMillis = if (cursor.isNull(4)) null else cursor.getLong(4),
                                    addedAtMillis = null,
                                    relativePath = folder.path,
                                    isDownload = folder.path.contains("download", ignoreCase = true),
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // A revoked grant or a misbehaving provider: skip that folder, keep the rest.
                    Log.w(TAG, "Could not list ${folder.path}", e)
                }
            }
            if (truncated) break
        }
        return Result(files, truncated)
    }

    private data class Pending(val documentId: String, val path: String, val depth: Int)

    private companion object {
        const val TAG = "DocumentTreeScanner"
        const val MAX_FILES = 25_000
        const val MAX_MILLIS = 12_000L
        const val MAX_DEPTH = 12
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
