package com.ailauncher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.data.local.dao.ChatDao
import com.ailauncher.data.local.dao.ReminderDao
import com.ailauncher.data.local.entity.AppAliasEntity
import com.ailauncher.data.local.entity.AppCategoryEntity
import com.ailauncher.data.local.entity.AppEntity
import com.ailauncher.data.local.entity.AppPrefsEntity
import com.ailauncher.data.local.entity.ChatMessageEntity
import com.ailauncher.data.local.entity.ReminderEntity

@Database(
    entities = [
        AppEntity::class,
        AppPrefsEntity::class,
        AppCategoryEntity::class,
        AppAliasEntity::class,
        ChatMessageEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun chatDao(): ChatDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        /** Also referenced by res/xml backup rules. */
        const val NAME = "ai_launcher.db"
    }
}
