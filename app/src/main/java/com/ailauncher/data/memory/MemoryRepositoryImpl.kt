package com.ailauncher.data.memory

import com.ailauncher.data.local.dao.MemoryDao
import com.ailauncher.data.local.entity.MemoryEntity
import com.ailauncher.domain.memory.MemoryText
import com.ailauncher.domain.model.Memory
import com.ailauncher.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Memories in Room: on the phone only, never in cloud backups (see res/xml/backup_rules.xml). */
@Singleton
class MemoryRepositoryImpl @Inject constructor(private val dao: MemoryDao) : MemoryRepository {

    override val memories: Flow<List<Memory>> = dao.observe().map { rows -> rows.map { it.toMemory() } }

    override suspend fun all(): List<Memory> = dao.all().map { it.toMemory() }

    override suspend fun remember(text: String): Memory? {
        val cleaned = MemoryText.clean(text) ?: return null
        dao.findByText(cleaned)?.let { return it.toMemory() }
        val entity = MemoryEntity(text = cleaned, createdAt = System.currentTimeMillis())
        val id = dao.insert(entity)
        dao.trim(Memory.MAX_COUNT)
        return entity.copy(id = id).toMemory()
    }

    override suspend fun delete(id: Long) = dao.delete(listOf(id))

    override suspend fun forget(query: String): List<Memory> {
        val all = all()
        val gone = if (MemoryText.meansEverything(query)) all else MemoryText.matching(all, query)
        if (gone.isNotEmpty()) dao.delete(gone.map { it.id })
        return gone
    }

    override suspend fun clear() = dao.clear()

    private fun MemoryEntity.toMemory() = Memory(id, text, createdAt)
}
