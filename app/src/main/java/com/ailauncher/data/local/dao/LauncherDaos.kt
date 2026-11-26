package com.ailauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ailauncher.data.local.entity.CalendarFeedEntity
import com.ailauncher.data.local.entity.CustomFolderEntity
import com.ailauncher.data.local.entity.FeedEventEntity
import com.ailauncher.data.local.entity.HomeItemEntity
import com.ailauncher.data.local.entity.MemoryEntity
import com.ailauncher.data.local.entity.NotificationDigestEntity
import com.ailauncher.data.local.entity.PlannerBlockEntity
import com.ailauncher.data.local.entity.RecentFileAccessEntity
import com.ailauncher.data.local.entity.TodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    @Query("SELECT * FROM home_items ORDER BY pageIndex, gridY, gridX")
    fun observe(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items")
    suspend fun all(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE id = :id")
    suspend fun get(id: Long): HomeItemEntity?

    @Insert
    suspend fun insert(item: HomeItemEntity): Long

    @Update
    suspend fun update(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM home_items WHERE type = 'APP' AND componentKey = :componentKey")
    suspend fun deleteApp(componentKey: String)

    @Query("DELETE FROM home_items WHERE type = 'FOLDER' AND folderId = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Query("DELETE FROM home_items WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    /** Deletes [ids] and inserts [item] atomically (apps merged into a new folder). */
    @Transaction
    suspend fun replaceWithItem(ids: List<Long>, item: HomeItemEntity): Long {
        deleteAll(ids)
        return insert(item)
    }

    @Query("SELECT * FROM home_items WHERE pageIndex = :page")
    suspend fun onPage(page: Int): List<HomeItemEntity>

    @Query("DELETE FROM home_items WHERE pageIndex = :page")
    suspend fun deletePage(page: Int)

    @Query("UPDATE home_items SET pageIndex = pageIndex - 1 WHERE pageIndex > :page")
    suspend fun shiftPagesDown(page: Int)

    @Query("UPDATE home_items SET pageIndex = :to WHERE pageIndex = :from")
    suspend fun renumberPage(from: Int, to: Int)

    /** Deletes [page] and closes the gap; returns the removed items. */
    @Transaction
    suspend fun removePage(page: Int): List<HomeItemEntity> {
        val removed = onPage(page)
        deletePage(page)
        shiftPagesDown(page)
        return removed
    }

    @Transaction
    suspend fun swapPages(a: Int, b: Int) {
        renumberPage(a, SWAP_PAGE)
        renumberPage(b, a)
        renumberPage(SWAP_PAGE, b)
    }

    private companion object {
        const val SWAP_PAGE = -1000
    }
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM custom_folders ORDER BY sortOrder, name COLLATE NOCASE")
    fun observe(): Flow<List<CustomFolderEntity>>

    @Query("SELECT * FROM custom_folders ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<CustomFolderEntity>

    @Query("SELECT * FROM custom_folders WHERE id = :id")
    suspend fun get(id: Long): CustomFolderEntity?

    @Insert
    suspend fun insert(folder: CustomFolderEntity): Long

    @Update
    suspend fun update(folder: CustomFolderEntity)

    @Query("DELETE FROM custom_folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM custom_folders")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM custom_folders WHERE dockPosition IS NOT NULL ORDER BY dockPosition")
    suspend fun dock(): List<CustomFolderEntity>

    @Query("UPDATE custom_folders SET dockPosition = :position WHERE id = :id")
    suspend fun setDockPosition(id: Long, position: Int?)
}

@Dao
interface TodoDao {
    @Query(
        """
        SELECT * FROM todo_items
        ORDER BY isCompleted, CASE WHEN dueTimestampMillis IS NULL THEN 1 ELSE 0 END, dueTimestampMillis,
                 priority DESC, createdAt
        """,
    )
    fun observe(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items ORDER BY isCompleted, createdAt")
    suspend fun all(): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun get(id: Long): TodoItemEntity?

    @Insert
    suspend fun insert(item: TodoItemEntity): Long

    @Update
    suspend fun update(item: TodoItemEntity)

    @Query("UPDATE todo_items SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlannerDao {
    @Query("SELECT * FROM planner_blocks WHERE dateEpochDay = :day ORDER BY startMinutesFromMidnight")
    fun observeDay(day: Long): Flow<List<PlannerBlockEntity>>

    @Query("SELECT * FROM planner_blocks WHERE dateEpochDay = :day ORDER BY startMinutesFromMidnight")
    suspend fun onDay(day: Long): List<PlannerBlockEntity>

    @Query("SELECT * FROM planner_blocks WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay, startMinutesFromMidnight")
    suspend fun between(from: Long, to: Long): List<PlannerBlockEntity>

    @Query("SELECT * FROM planner_blocks WHERE id = :id")
    suspend fun get(id: Long): PlannerBlockEntity?

    @Insert
    suspend fun insert(block: PlannerBlockEntity): Long

    @Update
    suspend fun update(block: PlannerBlockEntity)

    @Query("DELETE FROM planner_blocks WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Last time the launcher opened a URI. */
data class UriAccess(val uri: String, val lastAccess: Long)

@Dao
interface FileAccessDao {
    @Insert
    suspend fun insert(access: RecentFileAccessEntity): Long

    @Query("SELECT * FROM recent_file_access WHERE accessedAt BETWEEN :start AND :end ORDER BY accessedAt DESC")
    suspend fun between(start: Long, end: Long): List<RecentFileAccessEntity>

    @Query("SELECT uri, MAX(accessedAt) AS lastAccess FROM recent_file_access GROUP BY uri")
    suspend fun lastAccessByUri(): List<UriAccess>

    @Query("DELETE FROM recent_file_access WHERE accessedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_digest ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<NotificationDigestEntity>>

    @Query("SELECT * FROM notification_digest WHERE postedAt >= :since ORDER BY postedAt DESC")
    suspend fun since(since: Long): List<NotificationDigestEntity>

    @Query("SELECT * FROM notification_digest WHERE notificationKey = :key")
    suspend fun byKey(key: String): NotificationDigestEntity?

    @Insert
    suspend fun insert(entity: NotificationDigestEntity): Long

    @Update
    suspend fun update(entity: NotificationDigestEntity)

    /** One row per notification key holding its latest content (keeps a dismissed pill dismissed). */
    @Transaction
    suspend fun upsertByKey(entity: NotificationDigestEntity) {
        val existing = byKey(entity.notificationKey)
        if (existing == null) {
            insert(entity.copy(id = 0))
        } else {
            update(entity.copy(id = existing.id, pillDismissed = existing.pillDismissed && existing.extractionsJson == entity.extractionsJson))
        }
    }

    @Query("UPDATE notification_digest SET isActive = 0 WHERE notificationKey = :key")
    suspend fun markInactive(key: String)

    @Query("UPDATE notification_digest SET isActive = 0")
    suspend fun markAllInactive()

    @Query("UPDATE notification_digest SET pillDismissed = 1 WHERE notificationKey = :key")
    suspend fun dismissPill(key: String)

    @Query("DELETE FROM notification_digest WHERE postedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM notification_digest")
    suspend fun clear()
}

@Dao
interface CalendarFeedDao {
    @Query("SELECT * FROM calendar_feeds ORDER BY createdAt")
    fun observe(): Flow<List<CalendarFeedEntity>>

    @Query("SELECT * FROM calendar_feeds ORDER BY createdAt")
    suspend fun all(): List<CalendarFeedEntity>

    @Query("SELECT * FROM calendar_feeds WHERE id = :id")
    suspend fun get(id: Long): CalendarFeedEntity?

    @Insert
    suspend fun insert(feed: CalendarFeedEntity): Long

    @Update
    suspend fun update(feed: CalendarFeedEntity)

    @Query("DELETE FROM calendar_feeds WHERE id = :id")
    suspend fun deleteFeed(id: Long)

    @Query("DELETE FROM feed_events WHERE feedId = :feedId")
    suspend fun deleteEvents(feedId: Long)

    @Insert
    suspend fun insertEvents(events: List<FeedEventEntity>)

    /** Removes a feed and its events together. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteEvents(id)
        deleteFeed(id)
    }

    /** Swaps a feed's events for a fresh set and records the sync, all at once. */
    @Transaction
    suspend fun replaceEvents(feed: CalendarFeedEntity, events: List<FeedEventEntity>) {
        deleteEvents(feed.id)
        if (events.isNotEmpty()) insertEvents(events)
        update(feed)
    }

    @Query("SELECT * FROM feed_events WHERE startMillis < :end AND endMillis > :start ORDER BY startMillis LIMIT :limit")
    suspend fun eventsBetween(start: Long, end: Long, limit: Int = 200): List<FeedEventEntity>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC, id DESC")
    fun observe(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE text = :text COLLATE NOCASE LIMIT 1")
    suspend fun findByText(text: String): MemoryEntity?

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM memories")
    suspend fun clear()

    /** Keeps the newest [keep] memories. */
    @Query("DELETE FROM memories WHERE id NOT IN (SELECT id FROM memories ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

