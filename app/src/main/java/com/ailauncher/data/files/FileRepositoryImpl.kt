package com.ailauncher.data.files

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ailauncher.BuildConfig
import com.ailauncher.data.local.dao.FileAccessDao
import com.ailauncher.data.local.entity.RecentFileAccessEntity
import com.ailauncher.domain.files.FileSelection
import com.ailauncher.domain.files.FileTypes
import com.ailauncher.domain.model.FileAccessLevel
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.FileQueryResult
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.SearchFolder
import com.ailauncher.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage insights. The full build reads MediaStore: with media permissions it sees photos, videos
 * and audio; with "All files access" (optional) also documents and archives. The Google Play build
 * declares no storage permission and walks only the folders the user picked ([DocumentTreeScanner]).
 * Neither has a "last opened" column, so files opened or shared through the launcher are logged
 * in Room.
 */
@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessDao: FileAccessDao,
) : FileRepository {

    private val resolver: ContentResolver get() = context.contentResolver
    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    override val usesPickedFolders: Boolean = !BuildConfig.BROAD_FILE_ACCESS

    private val folderVersion = MutableStateFlow(0)
    override val searchFolders: Flow<List<SearchFolder>> = folderVersion
        .map { pickedFolders() }
        .flowOn(Dispatchers.IO)

    private data class ScanCache(val key: String, val atMillis: Long, val files: List<FileEntry>)

    @Volatile private var scanCache: ScanCache? = null

    override fun accessLevel(): FileAccessLevel = if (usesPickedFolders) {
        if (grantedTrees().isNotEmpty()) FileAccessLevel.FOLDERS else FileAccessLevel.NONE
    } else {
        mediaStoreAccessLevel()
    }

    private fun mediaStoreAccessLevel(): FileAccessLevel = when {
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

    // ------------------------------------------------------------- picked folders (Play build)

    private fun grantedTrees(): List<Uri> = runCatching {
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }
    }.getOrDefault(emptyList())

    private fun pickedFolders(): List<SearchFolder> = grantedTrees().map { SearchFolder(it.toString(), folderName(it)) }

    private fun folderName(tree: Uri): String {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        val queried = runCatching {
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        // e.g. "primary:DCIM/Camera" -> "Camera"
        return queried?.takeIf { it.isNotBlank() } ?: treeId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
    }

    override suspend fun addSearchFolder(uri: String): Boolean = withContext(Dispatchers.IO) {
        val granted = runCatching {
            resolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        scanCache = null
        folderVersion.value++
        granted
    }

    override suspend fun removeSearchFolder(uri: String) = withContext(Dispatchers.IO) {
        runCatching { resolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scanCache = null
        folderVersion.value++
        Unit
    }

    /** Every file in the picked folders; reused for a minute so follow-up questions are instant. */
    private suspend fun folderFiles(): List<FileEntry> = withContext(Dispatchers.IO) {
        val trees = grantedTrees()
        val key = trees.joinToString("|")
        val now = SystemClock.elapsedRealtime()
        scanCache?.takeIf { it.key == key && now - it.atMillis < SCAN_CACHE_MILLIS }?.let { return@withContext it.files }
        val files = DocumentTreeScanner(resolver).scan(trees.map { it to folderName(it) }).files
        scanCache = ScanCache(key, now, files)
        files
    }

    private suspend fun fromFolders(select: suspend (List<FileEntry>) -> List<FileEntry>): FileQueryResult {
        val level = accessLevel()
        if (level == FileAccessLevel.NONE) return FileQueryResult(emptyList(), level)
        return FileQueryResult(select(folderFiles()), level)
    }

    private suspend fun recentlyOpenedSince(cutoffMillis: Long): Set<String> = withContext(Dispatchers.IO) {
        accessDao.lastAccessByUri().filter { it.lastAccess >= cutoffMillis }.mapTo(HashSet()) { it.uri }
    }

    // --------------------------------------------------------------------------------- queries

    override suspend fun largest(limit: Int, type: FileTypeFilter): FileQueryResult =
        if (usesPickedFolders) {
            fromFolders { FileSelection.largest(it, type, limit) }
        } else {
            query(typeSelection(type), emptyList(), "${MediaStore.MediaColumns.SIZE} DESC", limit)
        }

    override suspend fun modifiedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult =
        if (usesPickedFolders) fromFolders { FileSelection.modifiedBetween(it, startMillis, endMillis, type, limit) } else query(
            and(typeSelection(type), "${MediaStore.MediaColumns.DATE_MODIFIED} >= ? AND ${MediaStore.MediaColumns.DATE_MODIFIED} < ?"),
            listOf((startMillis / 1000).toString(), (endMillis / 1000).toString()),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            limit,
        )

    override suspend fun downloadedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult {
        if (usesPickedFolders) return fromFolders { FileSelection.downloadedBetween(it, startMillis, endMillis, type, limit) }
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
        if (usesPickedFolders) {
            val opened = recentlyOpenedSince(cutoffMillis)
            return fromFolders { FileSelection.notModifiedSince(it, cutoffMillis, type, limit, opened) }
        }
        // Over-fetch (largest first: those are worth cleaning up), then drop recently opened ones.
        val result = query(
            and(typeSelection(type), "${MediaStore.MediaColumns.DATE_MODIFIED} < ?"),
            listOf((cutoffMillis / 1000).toString()),
            "${MediaStore.MediaColumns.SIZE} DESC",
            limit * 3,
        )
        val recentlyOpened = recentlyOpenedSince(cutoffMillis)
        return result.copy(files = result.files.filterNot { it.uri in recentlyOpened }.take(limit))
    }

    override suspend fun openedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): List<FileEntry> =
        withContext(Dispatchers.IO) {
            accessDao.between(startMillis, endMillis)
                .distinctBy { it.uri }
                .filter { FileTypes.matches(it.mimeType, it.displayName, type) }
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
        private const val SCAN_CACHE_MILLIS = 60_000L

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
                    "($mime IN (${quoted(FileTypes.DOCUMENT_MIMES)}) OR $mime LIKE 'application/vnd.openxmlformats-officedocument.%')"
                FileTypeFilter.ARCHIVE ->
                    "($mime IN (${quoted(FileTypes.ARCHIVE_MIMES)}) OR " + FileTypes.ARCHIVE_EXTENSIONS.joinToString(" OR ") { "$name LIKE '%.$it'" } + ")"
            }
        }

        private fun and(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" AND ") { "($it)" }
    }
}
