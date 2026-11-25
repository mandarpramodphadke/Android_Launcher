package com.ailauncher.domain.model

/** File kinds the storage tools can filter by (wire names are what the model sends). */
enum class FileTypeFilter(val wireName: String, val displayName: String) {
    ALL("all", "files"),
    VIDEO("video", "videos"),
    ARCHIVE("archive", "archives"),
    DOCUMENT("document", "documents"),
    IMAGE("image", "images"),
    AUDIO("audio", "audio files"),
    ;

    companion object {
        fun fromWire(value: String?): FileTypeFilter? = when (value?.trim()?.lowercase()?.removeSuffix("s")) {
            null, "" -> null
            "all", "any", "file", "everything" -> ALL
            "video", "movie", "clip", "mp4" -> VIDEO
            "archive", "zip", "apk", "compressed", "rar", "backup" -> ARCHIVE
            "document", "doc", "pdf", "docx", "text", "spreadsheet", "presentation", "ebook" -> DOCUMENT
            "image", "photo", "picture", "pic", "screenshot", "jpg", "png" -> IMAGE
            "audio", "music", "song", "sound", "recording", "voice note", "podcast", "mp3" -> AUDIO
            else -> null
        }
    }
}

/** How `query_files_by_activity` selects files. */
enum class FileActivityMode(val wireName: String) {
    /** Not modified (and not opened through the launcher) for N days. */
    UNUSED_SINCE("unused_since"),

    /** Saved to Downloads within a date range. */
    DOWNLOADED("downloaded"),

    /** Opened or shared through the launcher within a date range. */
    OPENED_OR_CLICKED("opened_or_clicked"),

    /** Modified within a date range. */
    MODIFIED_RANGE("modified_range"),
    ;

    companion object {
        fun fromWire(value: String?): FileActivityMode? {
            val v = value?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
            return entries.firstOrNull { it.wireName == v } ?: when (v) {
                "unused", "stale", "old", "not_used", "unused_for", "older_than" -> UNUSED_SINCE
                "download", "downloads", "downloaded_files" -> DOWNLOADED
                "opened", "clicked", "accessed", "recent", "recently_opened", "opened_or_accessed" -> OPENED_OR_CLICKED
                "modified", "changed", "edited", "range", "date_range", "created" -> MODIFIED_RANGE
                else -> null
            }
        }
    }
}

/** A file found through MediaStore (and, for opened files, the launcher's own access log). */
data class FileEntry(
    /** content:// URI usable with ACTION_VIEW / ACTION_SEND. */
    val uri: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAtMillis: Long?,
    val addedAtMillis: Long?,
    /** e.g. "Download/" or "DCIM/Camera/". */
    val relativePath: String?,
    val isDownload: Boolean,
    val lastOpenedAtMillis: Long? = null,
)

/** How much of shared storage the launcher may read. */
enum class FileAccessLevel {
    NONE,
    MEDIA_ONLY,
    ALL_FILES,

    /** Google Play build: only folders the user picked (Storage Access Framework). */
    FOLDERS,
}

/** A folder the user allowed the file tools to search (Google Play build). */
data class SearchFolder(val uri: String, val name: String)

data class FileQueryResult(val files: List<FileEntry>, val accessLevel: FileAccessLevel)

/** An installed app the user hasn't opened for a while. */
data class UnusedApp(
    val componentKey: String,
    val packageName: String,
    val label: String,
    /** From UsageStatsManager when usage access is granted, else the launcher's own launch log. */
    val lastUsedAtMillis: Long?,
    /** App + data + cache, when storage stats are available. */
    val sizeBytes: Long?,
    val installedAtMillis: Long,
)
