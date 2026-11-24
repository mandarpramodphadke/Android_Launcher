package com.ailauncher.data.productivity

import com.ailauncher.data.local.dao.PlannerDao
import com.ailauncher.data.local.dao.TodoDao
import com.ailauncher.data.local.entity.PlannerBlockEntity
import com.ailauncher.data.local.entity.TodoItemEntity
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.repository.PlannerRepository
import com.ailauncher.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepositoryImpl @Inject constructor(private val dao: TodoDao) : TodoRepository {

    override val todos: Flow<List<TodoItem>> = dao.observe().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun all(): List<TodoItem> = dao.all().map { it.toDomain() }

    override suspend fun get(id: Long): TodoItem? = dao.get(id)?.toDomain()

    override suspend fun add(title: String, notes: String?, listName: String, priority: TodoPriority, dueAtMillis: Long?): TodoItem {
        val entity = TodoItemEntity(
            title = title.trim().take(MAX_TITLE),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            listName = listName.trim().ifEmpty { TodoItem.DEFAULT_LIST }.take(MAX_LIST),
            priority = priority.ordinal,
            dueTimestampMillis = dueAtMillis,
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
        )
        return entity.copy(id = dao.insert(entity)).toDomain()
    }

    override suspend fun update(item: TodoItem) = dao.update(item.toEntity())

    override suspend fun setCompleted(id: Long, completed: Boolean) =
        dao.setCompleted(id, completed, if (completed) System.currentTimeMillis() else null)

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun TodoItemEntity.toDomain() = TodoItem(
        id = id,
        title = title,
        notes = notes,
        listName = listName,
        priority = TodoPriority.entries.getOrElse(priority) { TodoPriority.MEDIUM },
        dueAtMillis = dueTimestampMillis,
        completed = isCompleted,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun TodoItem.toEntity() = TodoItemEntity(
        id = id,
        title = title,
        notes = notes,
        listName = listName,
        priority = priority.ordinal,
        dueTimestampMillis = dueAtMillis,
        isCompleted = completed,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private companion object {
        const val MAX_TITLE = 200
        const val MAX_LIST = 40
    }
}

@Singleton
class PlannerRepositoryImpl @Inject constructor(private val dao: PlannerDao) : PlannerRepository {

    override fun observeDay(date: LocalDate): Flow<List<PlannerBlock>> =
        dao.observeDay(date.toEpochDay()).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun blocksOn(date: LocalDate): List<PlannerBlock> = dao.onDay(date.toEpochDay()).map { it.toDomain() }

    override suspend fun blocksBetween(from: LocalDate, to: LocalDate): List<PlannerBlock> =
        dao.between(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() }

    override suspend fun get(id: Long): PlannerBlock? = dao.get(id)?.toDomain()

    override suspend fun schedule(date: LocalDate, startMinutes: Int, durationMinutes: Int, title: String, linkedTodoId: Long?): PlannerBlock {
        val entity = PlannerBlockEntity(
            dateEpochDay = date.toEpochDay(),
            startMinutesFromMidnight = startMinutes.coerceIn(0, MINUTES_PER_DAY - 1),
            durationMinutes = durationMinutes.coerceIn(5, MINUTES_PER_DAY - startMinutes.coerceIn(0, MINUTES_PER_DAY - 5)),
            title = title.trim().take(120),
            linkedTodoId = linkedTodoId,
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
        )
        return entity.copy(id = dao.insert(entity)).toDomain()
    }

    override suspend fun update(block: PlannerBlock) {
        val existing = dao.get(block.id) ?: return
        dao.update(
            existing.copy(
                dateEpochDay = block.date.toEpochDay(),
                startMinutesFromMidnight = block.startMinutes,
                durationMinutes = block.durationMinutes,
                title = block.title,
                linkedTodoId = block.linkedTodoId,
                isCompleted = block.completed,
            ),
        )
    }

    override suspend fun remove(id: Long) = dao.delete(id)

    private fun PlannerBlockEntity.toDomain() = PlannerBlock(
        id = id,
        date = LocalDate.ofEpochDay(dateEpochDay),
        startMinutes = startMinutesFromMidnight,
        durationMinutes = durationMinutes,
        title = title,
        linkedTodoId = linkedTodoId,
        completed = isCompleted,
    )

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
