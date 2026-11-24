package com.ailauncher.data.home

import com.ailauncher.data.local.dao.FolderDao
import com.ailauncher.data.local.dao.HomeItemDao
import com.ailauncher.data.local.entity.CustomFolderEntity
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.search.FuzzyMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val dao: FolderDao,
    private val homeItemDao: HomeItemDao,
    private val dock: DockCoordinator,
) : FolderRepository {

    override val folders: Flow<List<CustomFolder>> = dao.observe().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun get(id: Long): CustomFolder? = dao.get(id)?.toDomain()

    override suspend fun create(name: String, description: String?, accent: FolderAccent, packageNames: List<String>): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            CustomFolderEntity(
                name = name.trim().take(MAX_NAME),
                description = description?.trim()?.take(MAX_DESCRIPTION)?.takeIf { it.isNotEmpty() },
                accentColorKey = accent.key,
                sortOrder = dao.maxSortOrder() + 1,
                packageNames = packageNames.distinct(),
                createdAt = now,
                updatedAt = now,
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

    private fun CustomFolderEntity.toDomain() = CustomFolder(
        id = id,
        name = name,
        description = description,
        accent = FolderAccent.fromKey(accentColorKey),
        sortOrder = sortOrder,
        packageNames = packageNames,
        dockPosition = dockPosition,
    )

    companion object {
        const val MAX_NAME = 40
        const val MAX_DESCRIPTION = 160

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
