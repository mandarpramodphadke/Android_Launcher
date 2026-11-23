package com.ailauncher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached PackageManager metadata for one launchable activity (rebuilt by sync). */
@Entity(tableName = "apps", indices = [Index("packageName")])
data class AppEntity(
    @PrimaryKey val componentKey: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
)

/**
 * User customisations per launcher activity. Kept separately from [AppEntity] so that dock
 * position, hidden state and custom labels survive app updates and can be backed up/restored.
 */
@Entity(tableName = "app_prefs", indices = [Index("packageName"), Index("dockPosition")])
data class AppPrefsEntity(
    @PrimaryKey val componentKey: String,
    val packageName: String,
    val customLabel: String? = null,
    val isHidden: Boolean = false,
    val dockPosition: Int? = null,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long = 0,
)

/**
 * AI/heuristic/user category per *package*. Keyed by package so a re-installed app keeps its
 * category and the LLM never sorts the same app twice.
 */
@Entity(tableName = "app_categories")
data class AppCategoryEntity(
    @PrimaryKey val packageName: String,
    /** [com.ailauncher.domain.model.AppCategory] name. */
    val category: String,
    /** [com.ailauncher.domain.model.CategorySource] name. */
    val source: String,
    val updatedAt: Long,
)

/** Custom alias ("insta" -> Instagram) used by search and the launch_app tool. */
@Entity(tableName = "app_aliases", indices = [Index("componentKey")])
data class AppAliasEntity(
    /** Normalised alias (lower-case, no punctuation). */
    @PrimaryKey val alias: String,
    val componentKey: String,
    val displayAlias: String,
)

@Entity(tableName = "chat_messages", indices = [Index(value = ["conversationId", "createdAt"])])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val thinking: String?,
    /** JSON array of [com.ailauncher.domain.model.ToolInvocation]. */
    val toolCallsJson: String?,
    val status: String,
    val mode: String?,
    val tokensPerSecond: Float?,
    val createdAt: Long,
)

@Entity(tableName = "reminders", indices = [Index("triggerAtMillis"), Index("status")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    /** Set when the reminder was written to CalendarContract instead of AlarmManager. */
    val calendarEventId: Long?,
    val status: String,
    val createdAt: Long,
)

/** Row of the drawer query: app metadata LEFT JOIN prefs LEFT JOIN category. */
data class AppRow(
    val componentKey: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val customLabel: String?,
    val isHidden: Boolean?,
    val dockPosition: Int?,
    val launchCount: Int?,
    val lastLaunchedAt: Long?,
    val category: String?,
    val categorySource: String?,
)
