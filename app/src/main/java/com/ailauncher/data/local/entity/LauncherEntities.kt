package com.ailauncher.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An app, custom folder, AppWidget or launcher widget on a home screen page (database v2, v3). */
@Entity(
    tableName = "home_items",
    indices = [Index("pageIndex"), Index("componentKey"), Index("folderId"), Index("appWidgetId")],
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** APP, FOLDER, WIDGET or LAUNCHER_WIDGET. */
    val type: String,
    val pageIndex: Int,
    val gridX: Int,
    val gridY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val componentKey: String? = null,
    val folderId: Long? = null,
    val appWidgetId: Int? = null,
    /** Flattened provider ComponentName of a widget. */
    val widgetProvider: String? = null,
    val createdAt: Long,
    /** v3: an encoded [com.ailauncher.domain.model.LauncherWidgetConfig] ("todo_list:Work"). */
    val launcherWidget: String? = null,
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_FOLDER = "FOLDER"
        const val TYPE_WIDGET = "WIDGET"
        const val TYPE_LAUNCHER_WIDGET = "LAUNCHER_WIDGET"
    }
}

/** A user folder: name, optional description (its purpose) and an ordered package list. */
@Entity(tableName = "custom_folders", indices = [Index("sortOrder"), Index("dockPosition")])
data class CustomFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val accentColorKey: String,
    val sortOrder: Int,
    /** Ordered package names (JSON array via [com.ailauncher.data.local.Converters]). */
    val packageNames: List<String>,
    /** Slot in the dock shared with pinned apps, or null. */
    val dockPosition: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** v3: an encoded [com.ailauncher.domain.model.FolderRule]; set for smart folders. */
    val smartRule: String? = null,
)

@Entity(tableName = "todo_items", indices = [Index("listName"), Index("isCompleted"), Index("dueTimestampMillis")])
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String?,
    val listName: String,
    /** 0 = low, 1 = medium, 2 = high. */
    val priority: Int,
    val dueTimestampMillis: Long?,
    val isCompleted: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
)

@Entity(
    tableName = "planner_blocks",
    indices = [Index(value = ["dateEpochDay", "startMinutesFromMidnight"]), Index("linkedTodoId")],
)
data class PlannerBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val startMinutesFromMidnight: Int,
    val durationMinutes: Int,
    val title: String,
    val linkedTodoId: Long?,
    val isCompleted: Boolean,
    val createdAt: Long,
)

/** Files opened or shared through the launcher (MediaStore has no "last opened" column). */
@Entity(tableName = "recent_file_access", indices = [Index("accessedAt"), Index("uri")])
data class RecentFileAccessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    /** OPENED, SHARED or CLICKED. */
    val accessType: String,
    val accessedAt: Long,
)

/**
 * One notification (latest content per notification key) for the opt-in digest. Rows older than
 * 24 hours are purged; nothing here ever leaves the phone.
 */
@Entity(
    tableName = "notification_digest",
    indices = [Index(value = ["notificationKey"], unique = true), Index("postedAt"), Index("packageName")],
)
data class NotificationDigestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val category: String?,
    val postedAt: Long,
    /** Still in the notification shade (counts towards the badge). */
    val isActive: Boolean,
    /** JSON list of smart extractions (OTP, meeting link, tracking number). */
    val extractionsJson: String?,
    val pillDismissed: Boolean = false,
)
