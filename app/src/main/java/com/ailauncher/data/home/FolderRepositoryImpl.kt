package com.ailauncher.data.home

import android.content.Context
import com.ailauncher.data.local.dao.FolderDao
import com.ailauncher.data.local.dao.HomeItemDao
import com.ailauncher.data.local.entity.CustomFolderEntity
import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.home.SmartFolderEngine
import com.ailauncher.domain.home.smartFolderSnapshot
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.UsageSnapshot
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.AppUsageRepository
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.search.FuzzyMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom folders from Room. Smart folders (a [FolderRule]) are filled here from the installed
 * apps and, for the usage rules, app usage read on the device; they refresh when apps change,
 * every 15 minutes while someone is watching, and on [refreshSmartFolders].
 */
@Singleton
class FolderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FolderDao,
    private val homeItemDao: HomeItemDao,
    private val dock: DockCoordinator,
    appRepository: AppRepository,
    private val usage: AppUsageRepository,
    @ApplicationScope scope: CoroutineScope,
) : FolderRepository {

    private data class SmartInputs(val nowMillis: Long, val usage: UsageSnapshot)

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val rows: Flow<List<CustomFolderEntity>> = dao.observe()

    /** 0: no smart folders; 1: smart folders that only need the clock; 2: some read app usage. */
    private val smartInputs: Flow<SmartInputs> = rows
        .map { list ->
            val rules = list.mapNotNull { FolderRule.decode(it.smartRule) }
            when {
                rules.isEmpty() -> 0
                rules.any { it.usesAppUsage } -> 2
                else -> 1
            }
        }
        .distinctUntilChanged()
        .flatMapLatest { level ->
            if (level == 0) {
                flowOf(SmartInputs(0L, UsageSnapshot.EMPTY))
            } else {
                channelFlow {
                    suspend fun read() = send(SmartInputs(System.currentTimeMillis(), if (level == 2) readUsage() else UsageSnapshot.EMPTY))
                    launch { refreshes.collect { read() } }
                    while (true) {
                        read()
                        delay(REFRESH_MILLIS)
                    }
                }
            }
        }

    override val folders: Flow<List<CustomFolder>> =
        combine(rows, appRepository.apps, smartInputs) { list, apps, inputs -> list.map { it.toDomain(apps, inputs) } }
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private suspend fun readUsage(): UsageSnapshot = usage.smartFolderSnapshot(System.currentTimeMillis())

    override fun refreshSmartFolders() {
        refreshes.tryEmit(Unit)
    }

    override suspend fun get(id: Long): CustomFolder? = folders.first().firstOrNull { it.id == id }

    override suspend fun create(name: String, description: String?, accent: FolderAccent, packageNames: List<String>, rule: FolderRule?): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            CustomFolderEntity(
                name = name.trim().take(MAX_NAME),
                description = description?.trim()?.take(MAX_DESCRIPTION)?.takeIf { it.isNotEmpty() },
                accentColorKey = accent.key,
                sortOrder = dao.maxSortOrder() + 1,
                packageNames = if (rule == null) packageNames.distinct() else emptyList(),
                createdAt = now,
                updatedAt = now,
                smartRule = rule?.encode(),
            ),
        )
    }

    override suspend fun update(id: Long, name: String, description: String?, accent: FolderAccent) {
        val row = dao.get(id) ?: return
        dao.update(
            row.copy(
                name = name.trim().take(MAX_NAME),
                description = description?.trim()?.take(MAX_DESCRIPTION)?.takeIf { it.isNotEmpty() },
                accentColorKey = accent.key,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setApps(id: Long, packageNames: List<String>) {
        val row = dao.get(id) ?: return
        dao.update(row.copy(packageNames = packageNames.distinct(), updatedAt = System.currentTimeMillis()))
    }

    override suspend fun setRule(id: Long, rule: FolderRule?, packageNames: List<String>) {
        val row = dao.get(id) ?: return
        dao.update(
            row.copy(
                smartRule = rule?.encode(),
                packageNames = if (rule == null) packageNames.distinct() else emptyList(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun delete(id: Long) {
        homeItemDao.deleteFolder(id)
        dao.delete(id)
        dock.compact()
    }

    override suspend fun findByName(query: String): CustomFolder? {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return null
        val all = folders.first()
        all.firstOrNull { normalize(it.name) == wanted }?.let { return it }
        val ranked = FuzzyMatcher.rank(wanted, all) { listOf(normalize(it.name)) }.firstOrNull() ?: return null
        return ranked.first.takeIf { ranked.second >= FuzzyMatcher.CONFIDENT_MATCH }
    }

    private fun CustomFolderEntity.toDomain(apps: List<AppInfo>, inputs: SmartInputs): CustomFolder {
        val rule = FolderRule.decode(smartRule)
        return CustomFolder(
            id = id,
            name = name,
            description = description,
            accent = FolderAccent.fromKey(accentColorKey),
            sortOrder = sortOrder,
            packageNames = rule?.let { SmartFolderEngine.packages(it, apps, inputs.usage, inputs.nowMillis, context.packageName) } ?: packageNames,
            dockPosition = dockPosition,
            rule = rule,
        )
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_DESCRIPTION = 160
        private const val REFRESH_MILLIS = 15 * 60_000L

        /** "my Work folder" → "work". */
        fun normalize(text: String): String = text.trim().lowercase()
            .removePrefix("open ").removePrefix("my ").removePrefix("the ")
            .removeSuffix(" folder").removePrefix("folder ").trim()
    }
}

/** The dock as a flow of apps and folders sorted by slot; edits go through [DockCoordinator]. */
@Singleton
class DockRepositoryImpl @Inject constructor(
    appRepository: AppRepository,
    folderRepository: FolderRepository,
    private val coordinator: DockCoordinator,
) : DockRepository {

    override val entries: Flow<List<DockEntry>> = combine(appRepository.apps, folderRepository.folders) { apps, folders ->
        val pinnedApps = apps.filter { it.dockPosition != null && !it.isHidden }.map { DockEntry.App(it) }
        val pinnedFolders = folders.filter { it.dockPosition != null }.map { DockEntry.Folder(it) }
        (pinnedApps + pinnedFolders).sortedBy { it.position }
    }.distinctUntilChanged()

    override suspend fun pinApp(componentKey: String) = coordinator.pinApp(componentKey)
    override suspend fun unpinApp(componentKey: String) = coordinator.unpinApp(componentKey)
    override suspend fun pinFolder(folderId: Long) = coordinator.pinFolder(folderId)
    override suspend fun unpinFolder(folderId: Long) = coordinator.unpinFolder(folderId)

    override suspend fun move(entry: DockEntry, offset: Int) = when (entry) {
        is DockEntry.App -> coordinator.moveApp(entry.app.componentKey, offset)
        is DockEntry.Folder -> coordinator.moveFolder(entry.folder.id, offset)
    }
}
