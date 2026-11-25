package com.ailauncher.domain.files

import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.FileTypeFilter

/** File-type rules shared by the MediaStore queries and the folder scanner. */
object FileTypes {
    val DOCUMENT_MIMES = listOf(
        "application/pdf", "application/msword", "application/rtf", "application/epub+zip",
        "application/vnd.ms-excel", "application/vnd.ms-powerpoint", "text/plain", "text/csv",
        "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
    )
    val ARCHIVE_MIMES = listOf(
        "application/zip", "application/x-zip-compressed", "application/x-rar-compressed", "application/vnd.rar",
        "application/x-7z-compressed", "application/gzip", "application/x-gzip", "application/x-tar",
        "application/x-bzip2", "application/vnd.android.package-archive",
    )
    val ARCHIVE_EXTENSIONS = listOf("zip", "rar", "7z", "gz", "tgz", "tar", "bz2", "xz", "apk", "apks", "xapk")

    private val DOCUMENT_EXTENSIONS = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "csv", "epub", "md")
    private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "avif")
    private val VIDEO_EXTENSIONS = listOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v")
    private val AUDIO_EXTENSIONS = listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "mid")

    /** MIME type first; the extension decides when a provider reports no or a generic MIME type. */
    fun matches(mimeType: String?, name: String, type: FileTypeFilter): Boolean {
        val mime = mimeType.orEmpty().lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        val generic = mime.isEmpty() || mime == "application/octet-stream"
        return when (type) {
            FileTypeFilter.ALL -> true
            FileTypeFilter.IMAGE -> mime.startsWith("image/") || (generic && extension in IMAGE_EXTENSIONS)
            FileTypeFilter.VIDEO -> mime.startsWith("video/") || (generic && extension in VIDEO_EXTENSIONS)
            FileTypeFilter.AUDIO -> mime.startsWith("audio/") || (generic && extension in AUDIO_EXTENSIONS)
            FileTypeFilter.DOCUMENT -> mime in DOCUMENT_MIMES || mime.startsWith("application/vnd.openxmlformats-officedocument.") ||
                (generic && extension in DOCUMENT_EXTENSIONS)
            FileTypeFilter.ARCHIVE -> mime in ARCHIVE_MIMES || extension in ARCHIVE_EXTENSIONS
        }
    }
}

/**
 * The file tools' filters applied to files found by walking folders (Google Play build), mirroring
 * what the MediaStore queries of the full build do in SQL.
 */
object FileSelection {

    fun largest(files: List<FileEntry>, type: FileTypeFilter, limit: Int): List<FileEntry> =
        files.asSequence()
            .filter { it.sizeBytes > 0 && FileTypes.matches(it.mimeType, it.name, type) }
            .sortedByDescending { it.sizeBytes }
            .take(limit)
            .toList()

    fun modifiedBetween(files: List<FileEntry>, startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): List<FileEntry> =
        files.asSequence()
            .filter { it.sizeBytes > 0 && FileTypes.matches(it.mimeType, it.name, type) }
            .filter { file -> file.modifiedAtMillis?.let { it in startMillis until endMillis } == true }
            .sortedByDescending { it.modifiedAtMillis }
            .take(limit)
            .toList()

    /** Untouched since [cutoffMillis] and not opened through the launcher since then; largest first. */
    fun notModifiedSince(
        files: List<FileEntry>,
        cutoffMillis: Long,
        type: FileTypeFilter,
        limit: Int,
        recentlyOpened: Set<String> = emptySet(),
    ): List<FileEntry> =
        files.asSequence()
            .filter { it.sizeBytes > 0 && FileTypes.matches(it.mimeType, it.name, type) }
            .filter { file -> file.modifiedAtMillis?.let { it < cutoffMillis } == true && file.uri !in recentlyOpened }
            .sortedByDescending { it.sizeBytes }
            .take(limit)
            .toList()

    /** Files under a "Download" folder (the only place downloads can be told apart), newest first. */
    fun downloadedBetween(files: List<FileEntry>, startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): List<FileEntry> =
        modifiedBetween(files.filter { it.isDownload }, startMillis, endMillis, type, limit)
}
