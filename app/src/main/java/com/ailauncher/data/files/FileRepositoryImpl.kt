package com.ailauncher.data.files

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.ailauncher.data.local.dao.FileAccessDao
import com.ailauncher.data.local.entity.RecentFileAccessEntity
import com.ailauncher.domain.model.FileAccessLevel
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.FileQueryResult
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage insights over MediaStore. With media permissions it sees photos, videos and audio; with
 * "All files access" (optional) it also sees documents and archives. MediaStore has no
 * "last opened" column, so files opened or shared through the launcher are logged in Room.
 */
@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessDao: FileAccessDao,
) : FileRepository {

    private val resolver: ContentResolver get() = context.contentResolver
    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    override fun accessLevel(): FileAccessLevel = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() -> FileAccessLevel.ALL_FILES
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            if (listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO).any(::granted)) {
                FileAccessLevel.MEDIA_ONLY
            } else {
                FileAccessLevel.NONE
            }
        // Android 10 with requestLegacyExternalStorage sees every file through MediaStore.
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> FileAccessLevel.ALL_FILES
        granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> FileAccessLevel.MEDIA_ONLY
        else -> FileAccessLevel.NONE
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun largest(limit: Int, type: FileTypeFilter): FileQueryResult =
        query(typeSelection(type), emptyList(), "${MediaStore.MediaColumns.SIZE} DESC", limit)

    override suspend fun modifiedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult =
        query(
            and(typeSelection(type), "${MediaStore.MediaColumns.DATE_MODIFIED} >= ? AND ${MediaStore.MediaColumns.DATE_MODIFIED} < ?"),
            listOf((startMillis / 1000).toString(), (endMillis / 1000).toString()),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            limit,
        )

    override suspend fun downloadedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult {
        val download = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.MediaColumns.IS_DOWNLOAD} = 1"
        } else {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE 'Download/%'"
        }
        return query(
            and(typeSelection(type), download, "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATE_ADDED} < ?"),
            listOf((startMillis / 1000).toString(), (endMillis / 1000).toString()),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            limit,
        )
    }

    override suspend fun notModifiedSince(cutoffMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult {
        // Over-fetch (largest first: those are worth cleaning up), then drop recently opened ones.
        val result = query(
            and(typeSelection(type), "${MediaStore.MediaColumns.DATE_MODIFIED} < ?"),
            listOf((cutoffMillis / 1000).toString()),
            "${MediaStore.MediaColumns.SIZE} DESC",
            limit * 3,
        )
        val recentlyOpened = withContext(Dispatchers.IO) {
            accessDao.lastAccessByUri().filter { it.lastAccess >= cutoffMillis }.mapTo(HashSet()) { it.uri }
        }
        return result.copy(files = result.files.filterNot { it.uri in recentlyOpened }.take(limit))
    }

    override suspend fun openedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): List<FileEntry> =
        withContext(Dispatchers.IO) {
            accessDao.between(startMillis, endMillis)
                .distinctBy { it.uri }
                .filter { matchesType(it.mimeType, it.displayName, type) }
                .take(limit)
                .map {
                    FileEntry(
                        uri = it.uri,
                        name = it.displayName,
                        mimeType = it.mimeType,
                        sizeBytes = it.sizeBytes ?: 0L,
                        modifiedAtMillis = null,
                        addedAtMillis = null,
                        relativePath = null,
                        isDownload = false,
                        lastOpenedAtMillis = it.accessedAt,
                    )
                }
        }

    override suspend fun recordAccess(file: FileEntry, accessType: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        accessDao.insert(
            RecentFileAccessEntity(
                uri = file.uri,
                displayName = file.name,
                mimeType = file.mimeType,
                sizeBytes = file.sizeBytes.takeIf { it > 0 },
                accessType = accessType,
                accessedAt = now,
            ),
        )
        accessDao.purgeOlderThan(now - ACCESS_LOG_RETENTION_MILLIS)
    }

    // ------------------------------------------------------------------------------------------ //

    private suspend fun query(selection: String, args: List<String>, sortOrder: String, limit: Int): FileQueryResult =
        withContext(Dispatchers.IO) {
            val level = accessLevel()
            if (level == FileAccessLevel.NONE) return@withContext FileQueryResult(emptyList(), level)
            val projection = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.MIME_TYPE)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.DATE_MODIFIED)
                add(MediaStore.MediaColumns.DATE_ADDED)
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.Files.FileColumns.MEDIA_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.IS_DOWNLOAD)
            }.toTypedArray()
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, and("${MediaStore.MediaColumns.SIZE} > 0", selection))
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            val files = runCatching {
                resolver.query(filesUri, projection, queryArgs, null)?.use { cursor -> readAll(cursor, limit) }
            }.getOrNull().orEmpty()
            FileQueryResult(files, level)
        }

    private fun readAll(cursor: Cursor, limit: Int): List<FileEntry> {
        val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val modified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val added = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val path = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val isDownload = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.MediaColumns.IS_DOWNLOAD) else -1
        val out = ArrayList<FileEntry>()
        while (cursor.moveToNext() && out.size < limit) {
            val rowId = cursor.getLong(id)
            val relativePath = cursor.getStringOrNull(path)
            out += FileEntry(
                uri = contentUriFor(cursor.getInt(mediaType), rowId).toString(),
                name = cursor.getStringOrNull(name) ?: "file-$rowId",
                mimeType = cursor.getStringOrNull(mime),
                sizeBytes = cursor.getLong(size),
                modifiedAtMillis = cursor.getLongOrNull(modified)?.times(1000),
                addedAtMillis = cursor.getLongOrNull(added)?.times(1000),
                relativePath = relativePath,
                isDownload = (isDownload >= 0 && cursor.getInt(isDownload) == 1) || relativePath?.startsWith("Download") == true,
            )
        }
        return out
    }

    /** Media-type collection URIs open in more viewers than the generic files URI. */
    private fun contentUriFor(mediaType: Int, id: Long): Uri = when (mediaType) {
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        else -> ContentUris.withAppendedId(filesUri, id)
    }

    private fun Cursor.getStringOrNull(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
    private fun Cursor.getLongOrNull(index: Int): Long? = if (index < 0 || isNull(index)) null else getLong(index)

    companion object {
        private const val ACCESS_LOG_RETENTION_MILLIS = 180L * 24 * 3_600_000

        private val DOCUMENT_MIMES = listOf(
            "application/pdf", "application/msword", "application/rtf", "application/epub+zip",
            "application/vnd.ms-excel", "application/vnd.ms-powerpoint", "text/plain", "text/csv",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
        )
        private val ARCHIVE_MIMES = listOf(
            "application/zip", "application/x-zip-compressed", "application/x-rar-compressed", "application/vnd.rar",
            "application/x-7z-compressed", "application/gzip", "application/x-gzip", "application/x-tar",
            "application/x-bzip2", "application/vnd.android.package-archive",
        )
        private val ARCHIVE_EXTENSIONS = listOf("zip", "rar", "7z", "gz", "tgz", "tar", "bz2", "xz", "apk", "apks", "xapk")

        private fun quoted(values: List<String>) = values.joinToString(",") { "'$it'" }

        /** SQL selection for [type]; constants only (no user input), so it is injection-safe. */
        fun typeSelection(type: FileTypeFilter): String {
            val mime = MediaStore.MediaColumns.MIME_TYPE
            val mediaType = MediaStore.Files.FileColumns.MEDIA_TYPE
            val name = MediaStore.MediaColumns.DISPLAY_NAME
            return when (type) {
                FileTypeFilter.ALL -> "$mime IS NOT NULL"
                FileTypeFilter.IMAGE -> "$mediaType = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
                FileTypeFilter.VIDEO -> "$mediaType = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
                FileTypeFilter.AUDIO -> "$mediaType = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"
                FileTypeFilter.DOCUMENT ->
                    "($mime IN (${quoted(DOCUMENT_MIMES)}) OR $mime LIKE 'application/vnd.openxmlformats-officedocument.%')"
                FileTypeFilter.ARCHIVE ->
                    "($mime IN (${quoted(ARCHIVE_MIMES)}) OR " + ARCHIVE_EXTENSIONS.joinToString(" OR ") { "$name LIKE '%.$it'" } + ")"
            }
        }

        fun matchesType(mimeType: String?, name: String, type: FileTypeFilter): Boolean {
            val mime = mimeType.orEmpty()
            val extension = name.substringAfterLast('.', "").lowercase()
            return when (type) {
                FileTypeFilter.ALL -> true
                FileTypeFilter.IMAGE -> mime.startsWith("image/")
                FileTypeFilter.VIDEO -> mime.startsWith("video/")
                FileTypeFilter.AUDIO -> mime.startsWith("audio/")
                FileTypeFilter.DOCUMENT -> mime in DOCUMENT_MIMES || mime.startsWith("application/vnd.openxmlformats-officedocument.")
                FileTypeFilter.ARCHIVE -> mime in ARCHIVE_MIMES || extension in ARCHIVE_EXTENSIONS
            }
        }

        private fun and(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" AND ") { "($it)" }
    }
}
