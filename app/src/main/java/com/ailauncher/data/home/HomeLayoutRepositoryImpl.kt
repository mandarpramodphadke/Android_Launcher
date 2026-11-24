package com.ailauncher.data.home

import android.content.Context
import androidx.core.content.edit
import com.ailauncher.data.local.dao.HomeItemDao
import com.ailauncher.data.local.entity.HomeItemEntity
import com.ailauncher.domain.home.GridSpec
import com.ailauncher.domain.home.HomeGridPlanner
import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.repository.HomeLayoutRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home screen pages backed by Room (`home_items`). Placement goes through [HomeGridPlanner] under
 * one mutex, so two quick "Add to home" taps can never land on the same cell. The page count is
 * kept separately because pages may be empty.
 */
@Singleton
class HomeLayoutRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: HomeItemDao,
) : HomeLayoutRepository {

    private val prefs = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)
    private val pages = MutableStateFlow(prefs.getInt(KEY_PAGES, 1).coerceIn(1, MAX_PAGES))
    private val mutex = Mutex()

    override val items: Flow<List<HomeItem>> = dao.observe().map { rows -> rows.mapNotNull { it.toDomain() } }

    override val pageCount: Flow<Int> = pages.asStateFlow()

    override suspend fun addApp(componentKey: String, preferredPage: Int, spec: GridSpec): GridPosition? = mutex.withLock {
        place(1, 1, preferredPage, spec) { position ->
            HomeItemEntity(type = HomeItemEntity.TYPE_APP, pageIndex = position.page, gridX = position.x, gridY = position.y, componentKey = componentKey, createdAt = now())
        }
    }

    override suspend fun addFolder(folderId: Long, preferredPage: Int, spec: GridSpec): GridPosition? = mutex.withLock {
        place(1, 1, preferredPage, spec) { position ->
            HomeItemEntity(type = HomeItemEntity.TYPE_FOLDER, pageIndex = position.page, gridX = position.x, gridY = position.y, folderId = folderId, createdAt = now())
        }
    }

    override suspend fun addWidget(
        appWidgetId: Int,
        provider: String,
        spanX: Int,
        spanY: Int,
        preferredPage: Int,
        spec: GridSpec,
    ): GridPosition? = mutex.withLock {
        place(spanX, spanY, preferredPage, spec) { position ->
            HomeItemEntity(
                type = HomeItemEntity.TYPE_WIDGET,
                pageIndex = position.page,
                gridX = position.x,
                gridY = position.y,
                spanX = position.spanX,
                spanY = position.spanY,
                appWidgetId = appWidgetId,
                widgetProvider = provider,
                createdAt = now(),
            )
        }
    }

    /** Finds room (adding a page if every existing one is full) and inserts the row. */
    private suspend fun place(spanX: Int, spanY: Int, preferredPage: Int, spec: GridSpec, row: (GridPosition) -> HomeItemEntity): GridPosition? {
        val current = dao.all().mapNotNull { it.toDomain() }
        val pageCount = pages.value
        val position = HomeGridPlanner.findFreeCellAnywhere(current, preferredPage.coerceIn(0, pageCount - 1), pageCount, spanX, spanY, spec)
            ?: if (pageCount < MAX_PAGES) {
                setPageCount(pageCount + 1)
                HomeGridPlanner.findFreeCell(current, pageCount, spanX, spanY, spec)
            } else {
                null
            }
            ?: return null
        dao.insert(row(position))
        return position
    }

    override suspend fun move(itemId: Long, target: GridPosition, spec: GridSpec): Boolean = mutex.withLock {
        val item = dao.get(itemId)?.toDomain() ?: return@withLock false
        val others = dao.all().mapNotNull { it.toDomain() }
        val moved = target.copy(spanX = item.position.spanX, spanY = item.position.spanY)
        if (!HomeGridPlanner.isFree(others, moved, spec, ignoreId = itemId)) return@withLock false
        dao.get(itemId)?.let { dao.update(it.copy(pageIndex = moved.page, gridX = moved.x, gridY = moved.y)) }
        true
    }

    override suspend fun resize(itemId: Long, spanX: Int, spanY: Int, spec: GridSpec): GridPosition? = mutex.withLock {
        val row = dao.get(itemId) ?: return@withLock null
        val item = row.toDomain() ?: return@withLock null
        val others = dao.all().mapNotNull { it.toDomain() }
        val resized = HomeGridPlanner.clampResize(others, item, spanX, spanY, spec)
        dao.update(row.copy(spanX = resized.spanX, spanY = resized.spanY))
        resized
    }

    override suspend fun remove(itemId: Long): HomeItem? = mutex.withLock {
        val item = dao.get(itemId)?.toDomain()
        dao.delete(itemId)
        item
    }

    override suspend fun removeApp(componentKey: String) = mutex.withLock { dao.deleteApp(componentKey) }

    override suspend fun removeFolder(folderId: Long) = mutex.withLock { dao.deleteFolder(folderId) }

    override suspend fun addPage(): Int = mutex.withLock {
        val count = pages.value
        if (count >= MAX_PAGES) return@withLock count - 1
        setPageCount(count + 1)
        count
    }

    override suspend fun removePage(page: Int): List<HomeItem> = mutex.withLock {
        val count = pages.value
        if (count <= 1 || page !in 0 until count) return@withLock emptyList()
        val removed = dao.removePage(page).mapNotNull { it.toDomain() }
        setPageCount(count - 1)
        removed
    }

    override suspend fun movePage(from: Int, to: Int) = mutex.withLock {
        val count = pages.value
        if (from == to || from !in 0 until count || to !in 0 until count) return@withLock
        // Adjacent swaps keep every page's items together while it travels.
        val step = if (to > from) 1 else -1
        var page = from
        while (page != to) {
            dao.swapPages(page, page + step)
            page += step
        }
    }

    private fun setPageCount(count: Int) {
        pages.value = count
        prefs.edit { putInt(KEY_PAGES, count) }
    }

    private fun now() = System.currentTimeMillis()

    private fun HomeItemEntity.toDomain(): HomeItem? {
        val content = when (type) {
            HomeItemEntity.TYPE_APP -> componentKey?.let { HomeItemContent.App(it) }
            HomeItemEntity.TYPE_FOLDER -> folderId?.let { HomeItemContent.Folder(it) }
            HomeItemEntity.TYPE_WIDGET -> appWidgetId?.let { HomeItemContent.Widget(it, widgetProvider.orEmpty()) }
            else -> null
        } ?: return null
        return HomeItem(id, GridPosition(pageIndex, gridX, gridY, spanX, spanY), content)
    }

    private companion object {
        const val KEY_PAGES = "page_count"
        const val MAX_PAGES = 9
    }
}
