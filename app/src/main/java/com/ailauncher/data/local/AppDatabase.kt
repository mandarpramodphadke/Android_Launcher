package com.ailauncher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.data.local.dao.CalendarFeedDao
import com.ailauncher.data.local.dao.ChatDao
import com.ailauncher.data.local.dao.FileAccessDao
import com.ailauncher.data.local.dao.FolderDao
import com.ailauncher.data.local.dao.HomeItemDao
import com.ailauncher.data.local.dao.MemoryDao
import com.ailauncher.data.local.dao.NotificationDao
import com.ailauncher.data.local.dao.PlannerDao
import com.ailauncher.data.local.dao.ReminderDao
import com.ailauncher.data.local.dao.TodoDao
import com.ailauncher.data.local.entity.AppAliasEntity
import com.ailauncher.data.local.entity.AppCategoryEntity
import com.ailauncher.data.local.entity.AppEntity
import com.ailauncher.data.local.entity.AppPrefsEntity
import com.ailauncher.data.local.entity.CalendarFeedEntity
import com.ailauncher.data.local.entity.ChatMessageEntity
import com.ailauncher.data.local.entity.CustomFolderEntity
import com.ailauncher.data.local.entity.FeedEventEntity
import com.ailauncher.data.local.entity.HomeItemEntity
import com.ailauncher.data.local.entity.MemoryEntity
import com.ailauncher.data.local.entity.NotificationDigestEntity
import com.ailauncher.data.local.entity.PlannerBlockEntity
import com.ailauncher.data.local.entity.RecentFileAccessEntity
import com.ailauncher.data.local.entity.ReminderEntity
import com.ailauncher.data.local.entity.TodoItemEntity

@Database(
    entities = [
        AppEntity::class,
        AppPrefsEntity::class,
        AppCategoryEntity::class,
        AppAliasEntity::class,
        ChatMessageEntity::class,
        ReminderEntity::class,
        // v2
        HomeItemEntity::class,
        CustomFolderEntity::class,
        TodoItemEntity::class,
        PlannerBlockEntity::class,
        RecentFileAccessEntity::class,
        NotificationDigestEntity::class,
        // v4
        CalendarFeedEntity::class,
        FeedEventEntity::class,
        // v5
        MemoryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun chatDao(): ChatDao
    abstract fun reminderDao(): ReminderDao
    abstract fun homeItemDao(): HomeItemDao
    abstract fun folderDao(): FolderDao
    abstract fun todoDao(): TodoDao
    abstract fun plannerDao(): PlannerDao
    abstract fun fileAccessDao(): FileAccessDao
    abstract fun notificationDao(): NotificationDao
    abstract fun calendarFeedDao(): CalendarFeedDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        /** Also referenced by res/xml backup rules. */
        const val NAME = "ai_launcher.db"

        val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2, Migration2To3, Migration3To4, Migration4To5)
    }
}

/**
 * v1 → v2: home screen pages, custom folders, to-dos, the daily planner, the file-access log and
 * the notification digest. Purely additive (new tables only), so no existing row is touched. The
 * SQL mirrors `schemas/com.ailauncher.data.local.AppDatabase/2.json` and is verified by
 * DatabaseMigrationTest.
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `home_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`pageIndex` INTEGER NOT NULL, `gridX` INTEGER NOT NULL, `gridY` INTEGER NOT NULL, `spanX` INTEGER NOT NULL, " +
                "`spanY` INTEGER NOT NULL, `componentKey` TEXT, `folderId` INTEGER, `appWidgetId` INTEGER, `widgetProvider` TEXT, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_items_pageIndex` ON `home_items` (`pageIndex`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_items_componentKey` ON `home_items` (`componentKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_items_folderId` ON `home_items` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_home_items_appWidgetId` ON `home_items` (`appWidgetId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`description` TEXT, `accentColorKey` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `packageNames` TEXT NOT NULL, " +
                "`dockPosition` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_folders_sortOrder` ON `custom_folders` (`sortOrder`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_folders_dockPosition` ON `custom_folders` (`dockPosition`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`notes` TEXT, `listName` TEXT NOT NULL, `priority` INTEGER NOT NULL, `dueTimestampMillis` INTEGER, " +
                "`isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_listName` ON `todo_items` (`listName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_isCompleted` ON `todo_items` (`isCompleted`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_dueTimestampMillis` ON `todo_items` (`dueTimestampMillis`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planner_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, `startMinutesFromMidnight` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `linkedTodoId` INTEGER, `isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_planner_blocks_dateEpochDay_startMinutesFromMidnight` " +
                "ON `planner_blocks` (`dateEpochDay`, `startMinutesFromMidnight`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planner_blocks_linkedTodoId` ON `planner_blocks` (`linkedTodoId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recent_file_access` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `mimeType` TEXT, `sizeBytes` INTEGER, `accessType` TEXT NOT NULL, " +
                "`accessedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_file_access_accessedAt` ON `recent_file_access` (`accessedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_file_access_uri` ON `recent_file_access` (`uri`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_digest` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`notificationKey` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `title` TEXT, `text` TEXT, " +
                "`category` TEXT, `postedAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `extractionsJson` TEXT, " +
                "`pillDismissed` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_digest_notificationKey` ON `notification_digest` (`notificationKey`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_digest_postedAt` ON `notification_digest` (`postedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_digest_packageName` ON `notification_digest` (`packageName`)")
    }
}

/**
 * v2 → v3 (launcher 1.4): launcher-drawn widgets on home pages (`home_items.launcherWidget`) and
 * smart folders (`custom_folders.smartRule`). Two nullable columns, so every row stays as it was.
 */
object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `home_items` ADD COLUMN `launcherWidget` TEXT")
        db.execSQL("ALTER TABLE `custom_folders` ADD COLUMN `smartRule` TEXT")
    }
}

/**
 * v3 → v4 (launcher 1.6): calendars subscribed by link or imported from .ics files, and their
 * events. New tables only.
 */
object Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `calendar_feeds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`url` TEXT, `colorArgb` INTEGER NOT NULL, `lastSyncedAt` INTEGER, `lastError` TEXT, `eventCount` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `location` TEXT, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `allDay` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_events_feedId` ON `feed_events` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_events_startMillis` ON `feed_events` (`startMillis`)")
    }
}

/** v4 → v5: the assistant's memories (ASSISTANT_MEMORY). One new table; nothing else changes. */
object Migration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
    }
}
