package com.ailauncher.domain.repository

import com.ailauncher.domain.home.GridSpec
import com.ailauncher.domain.model.AppShortcut
import com.ailauncher.domain.model.ContactMatch
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.FileAccessLevel
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.FileQueryResult
import com.ailauncher.domain.model.ContentReport
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.ReportOutcome
import com.ailauncher.domain.model.SearchFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.NotificationDigestItem
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.SmartPill
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.suggestions.LaunchSignal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Apps, folders and widgets placed on the home screen pages (Room `home_items`). */
interface HomeLayoutRepository {
    val items: Flow<List<HomeItem>>

    /** Number of home pages (at least 1; empty pages are allowed). */
    val pageCount: Flow<Int>

    /** Places the item at the first free cell from [preferredPage] on; null when every page is full. */
    suspend fun addApp(componentKey: String, preferredPage: Int, spec: GridSpec): GridPosition?
    suspend fun addFolder(folderId: Long, preferredPage: Int, spec: GridSpec): GridPosition?
    suspend fun addWidget(appWidgetId: Int, provider: String, spanX: Int, spanY: Int, preferredPage: Int, spec: GridSpec): GridPosition?

    suspend fun move(itemId: Long, target: GridPosition, spec: GridSpec): Boolean
    suspend fun resize(itemId: Long, spanX: Int, spanY: Int, spec: GridSpec): GridPosition?

    /** Removes the item and returns it (a widget's id must then be released by the caller). */
    suspend fun remove(itemId: Long): HomeItem?
    suspend fun removeApp(componentKey: String)
    suspend fun removeFolder(folderId: Long)

    suspend fun addPage(): Int

    /** Deletes the page and its items (returned), shifting later pages down. */
    suspend fun removePage(page: Int): List<HomeItem>
    suspend fun movePage(from: Int, to: Int)
}

/** User folders with a name, a description and an ordered app list (Room `custom_folders`). */
interface FolderRepository {
    val folders: Flow<List<CustomFolder>>
    suspend fun get(id: Long): CustomFolder?
    suspend fun create(name: String, description: String?, accent: FolderAccent, packageNames: List<String>): Long
    suspend fun update(id: Long, name: String, description: String?, accent: FolderAccent)
    suspend fun setApps(id: Long, packageNames: List<String>)
    suspend fun delete(id: Long)

    /** Folder whose name matches [query] ("Work", "my work folder"), if any. */
    suspend fun findByName(query: String): CustomFolder?
}

/** The 5-slot dock, shared by pinned apps and pinned folders. */
interface DockRepository {
    val entries: Flow<List<DockEntry>>
    suspend fun pinApp(componentKey: String): Boolean
    suspend fun unpinApp(componentKey: String)
    suspend fun pinFolder(folderId: Long): Boolean
    suspend fun unpinFolder(folderId: Long)
    suspend fun move(entry: DockEntry, offset: Int)

    companion object {
        const val MAX_SIZE = 5
    }
}

interface TodoRepository {
    val todos: Flow<List<TodoItem>>
    suspend fun all(): List<TodoItem>
    suspend fun get(id: Long): TodoItem?
    suspend fun add(title: String, notes: String?, listName: String, priority: TodoPriority, dueAtMillis: Long?): TodoItem
    suspend fun update(item: TodoItem)
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun delete(id: Long)
}

interface PlannerRepository {
    fun observeDay(date: LocalDate): Flow<List<PlannerBlock>>
    suspend fun blocksOn(date: LocalDate): List<PlannerBlock>
    suspend fun blocksBetween(from: LocalDate, to: LocalDate): List<PlannerBlock>
    suspend fun get(id: Long): PlannerBlock?
    suspend fun schedule(date: LocalDate, startMinutes: Int, durationMinutes: Int, title: String, linkedTodoId: Long?): PlannerBlock
    suspend fun update(block: PlannerBlock)
    suspend fun remove(id: Long)
}

/** App usage from UsageStatsManager (when the user grants usage access) merged with the launcher's log. */
interface AppUsageRepository {
    fun hasUsageAccess(): Boolean

    /** packageName → last time it was in the foreground, since [sinceMillis]. */
    suspend fun lastUsedTimes(sinceMillis: Long): Map<String, Long>

    /** Individual foreground starts since [sinceMillis] (for suggestions). */
    suspend fun launchHistory(sinceMillis: Long): List<LaunchSignal>

    /** App + data + cache bytes, when storage stats are readable. */
    suspend fun appSizeBytes(packageName: String): Long?
}

/**
 * Shared-storage files plus the launcher's own "opened through me" log. The full build reads
 * MediaStore (media permissions, optional all-files access); the Google Play build only searches
 * folders the user picked ([usesPickedFolders]).
 */
interface FileRepository {
    fun accessLevel(): FileAccessLevel

    /** True in the Google Play build: no storage permission, only [searchFolders] are searched. */
    val usesPickedFolders: Boolean
    val searchFolders: Flow<List<SearchFolder>>
    /** Keeps read access to a folder the user picked (a tree URI). */
    suspend fun addSearchFolder(uri: String): Boolean
    suspend fun removeSearchFolder(uri: String)

    suspend fun largest(limit: Int, type: FileTypeFilter): FileQueryResult
    suspend fun modifiedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult
    suspend fun downloadedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult
    suspend fun notModifiedSince(cutoffMillis: Long, type: FileTypeFilter, limit: Int): FileQueryResult
    suspend fun openedBetween(startMillis: Long, endMillis: Long, type: FileTypeFilter, limit: Int): List<FileEntry>
    suspend fun recordAccess(file: FileEntry, accessType: String)
}

/** Opt-in, on-device notification digest (auto-purged after 24 h). */
interface NotificationDigestRepository {
    val digest: Flow<List<NotificationDigestItem>>

    /** packageName → active notifications, for icon badges. */
    val badgeCounts: Flow<Map<String, Int>>
    val smartPills: Flow<List<SmartPill>>
    fun hasListenerAccess(): Boolean
    suspend fun recent(sinceMillis: Long): List<NotificationDigestItem>
    suspend fun dismissPill(notificationKey: String)
    suspend fun clear()
}

interface ContactRepository {
    fun hasPermission(): Boolean
    suspend fun search(name: String, limit: Int = 5): List<ContactMatch>
}

interface ShortcutRepository {
    /** Static, dynamic and pinned shortcuts (only available while this is the default launcher). */
    suspend fun shortcuts(packageName: String): List<AppShortcut>
}

/** Reports of assistant replies, sent only when the user asks (see [ContentReport]). */
interface ContentReportRepository {
    /** Replies the user reported on this phone; they stay hidden behind a "Show" button. */
    val reportedMessageIds: Flow<Set<Long>>
    suspend fun submit(report: ContentReport): ReportOutcome
}
