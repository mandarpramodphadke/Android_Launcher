package com.ailauncher.data.home

import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.data.local.dao.FolderDao
import com.ailauncher.data.local.entity.AppPrefsEntity
import com.ailauncher.domain.repository.DockRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the dock's slot numbering. Pinned apps (`app_prefs.dockPosition`) and pinned folders
 * (`custom_folders.dockPosition`) share one sequence of [DockRepository.MAX_SIZE] slots, so both
 * tables are only ever updated together under this lock.
 */
@Singleton
class DockCoordinator @Inject constructor(
    private val appDao: AppDao,
    private val folderDao: FolderDao,
) {
    private val mutex = Mutex()

    private sealed interface Slot {
        val position: Int

        data class App(val prefs: AppPrefsEntity) : Slot {
            override val position get() = prefs.dockPosition ?: Int.MAX_VALUE
        }

        data class Folder(val id: Long, override val position: Int) : Slot
    }

    private suspend fun slots(): List<Slot> =
        (appDao.dock().map { Slot.App(it) } + folderDao.dock().map { Slot.Folder(it.id, it.dockPosition ?: Int.MAX_VALUE) })
            .sortedBy { it.position }

    private fun nextPosition(slots: List<Slot>) = (slots.maxOfOrNull { it.position } ?: -1) + 1

    suspend fun pinApp(componentKey: String): Boolean = mutex.withLock {
        val slots = slots()
        if (slots.any { it is Slot.App && it.prefs.componentKey == componentKey }) return@withLock true
        if (slots.size >= DockRepository.MAX_SIZE) return@withLock false
        val prefs = appDao.prefs(componentKey) ?: AppPrefsEntity(componentKey, componentKey.substringBefore('/'))
        appDao.upsertPrefs(prefs.copy(dockPosition = nextPosition(slots), isHidden = false))
        true
    }

    suspend fun unpinApp(componentKey: String) = mutex.withLock {
        appDao.prefs(componentKey)?.let { appDao.upsertPrefs(it.copy(dockPosition = null)) }
        compactLocked()
    }

    suspend fun pinFolder(folderId: Long): Boolean = mutex.withLock {
        val slots = slots()
        if (slots.any { it is Slot.Folder && it.id == folderId }) return@withLock true
        if (slots.size >= DockRepository.MAX_SIZE) return@withLock false
        folderDao.setDockPosition(folderId, nextPosition(slots))
        true
    }

    suspend fun unpinFolder(folderId: Long) = mutex.withLock {
        folderDao.setDockPosition(folderId, null)
        compactLocked()
    }

    suspend fun moveApp(componentKey: String, offset: Int) =
        move(offset) { it is Slot.App && it.prefs.componentKey == componentKey }

    suspend fun moveFolder(folderId: Long, offset: Int) = move(offset) { it is Slot.Folder && it.id == folderId }

    private suspend fun move(offset: Int, predicate: (Slot) -> Boolean) = mutex.withLock {
        val slots = slots().toMutableList()
        val index = slots.indexOfFirst(predicate)
        if (index < 0) return@withLock
        val target = (index + offset).coerceIn(0, slots.lastIndex)
        if (target == index) return@withLock
        slots.add(target, slots.removeAt(index))
        write(slots)
    }

    /** Re-numbers the slots 0..n-1 (after an unpin, a hide or an uninstall). */
    suspend fun compact() = mutex.withLock { compactLocked() }

    private suspend fun compactLocked() = write(slots())

    private suspend fun write(slots: List<Slot>) {
        slots.forEachIndexed { index, slot ->
            when (slot) {
                is Slot.App -> if (slot.prefs.dockPosition != index) appDao.upsertPrefs(slot.prefs.copy(dockPosition = index))
                is Slot.Folder -> if (slot.position != index) folderDao.setDockPosition(slot.id, index)
            }
        }
    }
}
