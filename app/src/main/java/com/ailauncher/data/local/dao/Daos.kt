package com.ailauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ailauncher.data.local.entity.AppAliasEntity
import com.ailauncher.data.local.entity.AppCategoryEntity
import com.ailauncher.data.local.entity.AppEntity
import com.ailauncher.data.local.entity.AppPrefsEntity
import com.ailauncher.data.local.entity.AppRow
import com.ailauncher.data.local.entity.ChatMessageEntity
import com.ailauncher.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query(
        """
        SELECT a.componentKey, a.packageName, a.activityName, a.label, a.isSystemApp, a.installedAt, a.updatedAt,
               p.customLabel AS customLabel, p.isHidden AS isHidden, p.dockPosition AS dockPosition,
               p.launchCount AS launchCount, p.lastLaunchedAt AS lastLaunchedAt,
               c.category AS category, c.source AS categorySource
        FROM apps a
        LEFT JOIN app_prefs p ON p.componentKey = a.componentKey
        LEFT JOIN app_categories c ON c.packageName = a.packageName
        ORDER BY COALESCE(p.customLabel, a.label) COLLATE NOCASE
        """,
    )
    fun observeApps(): Flow<List<AppRow>>

    @Query("SELECT * FROM apps")
    suspend fun allApps(): List<AppEntity>

    @Upsert
    suspend fun upsertApps(apps: List<AppEntity>)

    @Query("DELETE FROM apps WHERE componentKey IN (:keys)")
    suspend fun deleteApps(keys: List<String>)

    @Transaction
    suspend fun applySync(upserts: List<AppEntity>, removals: List<String>) {
        if (upserts.isNotEmpty()) upsertApps(upserts)
        if (removals.isNotEmpty()) {
            deleteApps(removals)
            clearDockFor(removals)
        }
    }

    // --- prefs ---------------------------------------------------------------------------------
    @Query("SELECT * FROM app_prefs WHERE componentKey = :componentKey")
    suspend fun prefs(componentKey: String): AppPrefsEntity?

    @Query("SELECT * FROM app_prefs")
    suspend fun allPrefs(): List<AppPrefsEntity>

    @Upsert
    suspend fun upsertPrefs(prefs: AppPrefsEntity)

    @Upsert
    suspend fun upsertPrefs(prefs: List<AppPrefsEntity>)

    @Query("SELECT * FROM app_prefs WHERE dockPosition IS NOT NULL ORDER BY dockPosition")
    suspend fun dock(): List<AppPrefsEntity>

    @Query("UPDATE app_prefs SET dockPosition = NULL WHERE componentKey IN (:keys)")
    suspend fun clearDockFor(keys: List<String>)

    @Query("UPDATE app_prefs SET launchCount = launchCount + 1, lastLaunchedAt = :at WHERE componentKey = :componentKey")
    suspend fun incrementLaunchCount(componentKey: String, at: Long): Int

    // --- categories ----------------------------------------------------------------------------
    @Upsert
    suspend fun upsertCategories(categories: List<AppCategoryEntity>)

    @Query("SELECT * FROM app_categories")
    suspend fun allCategories(): List<AppCategoryEntity>

    @Query("SELECT * FROM app_categories WHERE packageName IN (:packages)")
    suspend fun categoriesFor(packages: List<String>): List<AppCategoryEntity>

    @Query("DELETE FROM app_categories WHERE source != 'USER'")
    suspend fun deleteAutomaticCategories()

    // --- aliases -------------------------------------------------------------------------------
    @Query("SELECT * FROM app_aliases")
    fun observeAliases(): Flow<List<AppAliasEntity>>

    @Query("SELECT * FROM app_aliases")
    suspend fun allAliases(): List<AppAliasEntity>

    @Upsert
    suspend fun upsertAlias(alias: AppAliasEntity)

    @Upsert
    suspend fun upsertAliases(aliases: List<AppAliasEntity>)

    @Query("DELETE FROM app_aliases WHERE alias = :alias")
    suspend fun deleteAlias(alias: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observe(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM chat_messages WHERE conversationId = :conversationId
            ORDER BY createdAt DESC, id DESC LIMIT :limit
        ) ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun recent(conversationId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun get(id: Long): ChatMessageEntity?

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clear(conversationId: String)

    @Query("UPDATE chat_messages SET status = 'CANCELLED' WHERE status = 'STREAMING'")
    suspend fun markStreamingAsCancelled()
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status = 'SCHEDULED' ORDER BY triggerAtMillis")
    fun observeScheduled(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'SCHEDULED' ORDER BY triggerAtMillis")
    suspend fun scheduled(): List<ReminderEntity>

    @Query(
        "SELECT * FROM reminders WHERE triggerAtMillis BETWEEN :start AND :end AND status != 'CANCELLED' ORDER BY triggerAtMillis",
    )
    suspend fun between(start: Long, end: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)
}
