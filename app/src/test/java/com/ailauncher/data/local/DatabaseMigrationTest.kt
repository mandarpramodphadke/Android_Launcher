package com.ailauncher.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ailauncher.data.local.entity.CustomFolderEntity
import com.ailauncher.data.local.entity.HomeItemEntity
import com.ailauncher.data.local.entity.NotificationDigestEntity
import com.ailauncher.data.local.entity.PlannerBlockEntity
import com.ailauncher.data.local.entity.TodoItemEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrading from shipped databases: a real v1 (or v2) file is built from the exported 1.json (2.json),
 * filled with data, then opened through the production Room builder. Room runs the migrations and
 * then validates every table against the current schema, so wrong migration SQL fails this test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class DatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val dbFile: File = context.getDatabasePath("migration-test.db")

    @After
    fun tearDown() {
        context.deleteDatabase(dbFile.name)
    }

    /** Creates every table and index of the exported schema [version] in a fresh file. */
    private fun createTables(db: SQLiteDatabase, version: Int) {
        val schema = Json.parseToJsonElement(File(SCHEMA_DIR, "$version.json").readText()).jsonObject["database"]!!.jsonObject
        schema["entities"]!!.jsonArray.forEach { entity ->
            val e = entity.jsonObject
            val table = e["tableName"]!!.jsonPrimitive.content
            db.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { index ->
                db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
    }

    /** What a 1.2/1.3 install contains: a folder on page 2, an app and a widget on page 1. */
    private fun createVersion2Database() {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createTables(db, 2)
            db.execSQL(
                "INSERT INTO custom_folders (id, name, description, accentColorKey, sortOrder, packageNames, dockPosition, createdAt, updatedAt) " +
                    "VALUES (7, 'Work', 'Apps for my job', 'teal', 0, '[\"com.slack\"]', 1, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO home_items (type, pageIndex, gridX, gridY, spanX, spanY, componentKey, folderId, appWidgetId, widgetProvider, createdAt) " +
                    "VALUES ('FOLDER', 1, 0, 0, 1, 1, NULL, 7, NULL, NULL, 1), " +
                    "('APP', 0, 1, 2, 1, 1, 'com.spotify/.Main', NULL, NULL, NULL, 1), " +
                    "('WIDGET', 0, 0, 3, 4, 2, NULL, NULL, 42, 'com.weather/.Widget', 1)",
            )
            db.version = 2
        }
    }

    @Test
    fun migratesVersion2ToVersion3KeepingFoldersAndHomePages() = runTest {
        createVersion2Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val work = db.folderDao().get(7)!!
            assertThat(work.name).isEqualTo("Work")
            assertThat(work.packageNames).containsExactly("com.slack")
            assertThat(work.dockPosition).isEqualTo(1)
            assertThat(work.smartRule).isNull()
            val items = db.homeItemDao().all()
            assertThat(items.map { it.type }).containsExactly("FOLDER", "APP", "WIDGET")
            assertThat(items.single { it.type == "WIDGET" }.appWidgetId).isEqualTo(42)
            assertThat(items.all { it.launcherWidget == null }).isTrue()

            // The new columns are usable: a smart folder and a launcher widget.
            val smartId = db.folderDao().insert(
                CustomFolderEntity(name = "New apps", description = null, accentColorKey = "sky", sortOrder = 1, packageNames = emptyList(), createdAt = 2, updatedAt = 2, smartRule = "installed:14"),
            )
            assertThat(db.folderDao().get(smartId)?.smartRule).isEqualTo("installed:14")
            db.homeItemDao().insert(
                HomeItemEntity(type = HomeItemEntity.TYPE_LAUNCHER_WIDGET, pageIndex = 1, gridX = 0, gridY = 2, spanX = 4, spanY = 2, launcherWidget = "todo_list:Work", createdAt = 2),
            )
            assertThat(db.homeItemDao().onPage(1).mapNotNull { it.launcherWidget }).containsExactly("todo_list:Work")

            // Apps dropped onto each other: both leave the page and the folder takes the cell.
            val app = db.homeItemDao().all().single { it.type == "APP" }
            db.homeItemDao().replaceWithItem(listOf(app.id), HomeItemEntity(type = HomeItemEntity.TYPE_FOLDER, pageIndex = 0, gridX = 1, gridY = 2, folderId = smartId, createdAt = 3))
            assertThat(db.homeItemDao().onPage(0).map { it.type }).containsExactly("FOLDER", "WIDGET")
        } finally {
            db.close()
        }
    }

    private fun createVersion1Database() {
        val schema = Json.parseToJsonElement(File(SCHEMA_DIR, "1.json").readText()).jsonObject["database"]!!.jsonObject
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            schema["entities"]!!.jsonArray.forEach { entity ->
                val e = entity.jsonObject
                val table = e["tableName"]!!.jsonPrimitive.content
                db.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            // What a 1.0/1.1 install contains.
            db.execSQL(
                "INSERT INTO apps (componentKey, packageName, activityName, label, isSystemApp, installedAt, updatedAt) " +
                    "VALUES ('com.spotify/.Main', 'com.spotify', '.Main', 'Spotify', 0, 1, 2)",
            )
            db.execSQL(
                "INSERT INTO app_prefs (componentKey, packageName, customLabel, isHidden, dockPosition, launchCount, lastLaunchedAt) " +
                    "VALUES ('com.spotify/.Main', 'com.spotify', 'Music', 0, 0, 7, 99)",
            )
            db.execSQL(
                "INSERT INTO chat_messages (conversationId, role, content, thinking, toolCallsJson, status, mode, tokensPerSecond, createdAt) " +
                    "VALUES ('default', 'USER', 'hello', NULL, NULL, 'COMPLETE', 'FAST_ACTION', NULL, 5)",
            )
            db.version = 1
        }
    }

    @Test
    fun migratesVersion1ToVersion2KeepingEveryRow() = runTest {
        createVersion1Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // v1 data survived.
            val rows = db.appDao().observeApps().first()
            assertThat(rows).hasSize(1)
            assertThat(rows[0].customLabel).isEqualTo("Music")
            assertThat(rows[0].dockPosition).isEqualTo(0)
            assertThat(rows[0].launchCount).isEqualTo(7)
            assertThat(db.chatDao().recent("default", 10).map { it.content }).containsExactly("hello")

            // Every v2 table is usable.
            val folderId = db.folderDao().insert(
                CustomFolderEntity(name = "Work", description = "Apps for my job", accentColorKey = "teal", sortOrder = 0, packageNames = listOf("com.slack", "com.google.android.gm"), createdAt = 1, updatedAt = 1),
            )
            assertThat(db.folderDao().get(folderId)?.packageNames).containsExactly("com.slack", "com.google.android.gm").inOrder()
            db.homeItemDao().insert(HomeItemEntity(type = HomeItemEntity.TYPE_FOLDER, pageIndex = 1, gridX = 0, gridY = 2, folderId = folderId, createdAt = 1))
            assertThat(db.homeItemDao().onPage(1)).hasSize(1)
            val todoId = db.todoDao().insert(TodoItemEntity(title = "Milk", notes = null, listName = "Shopping", priority = 2, dueTimestampMillis = null, isCompleted = false, createdAt = 1, completedAt = null))
            db.plannerDao().insert(PlannerBlockEntity(dateEpochDay = 20_000, startMinutesFromMidnight = 540, durationMinutes = 60, title = "Gym", linkedTodoId = todoId, isCompleted = false, createdAt = 1))
            assertThat(db.plannerDao().onDay(20_000).map { it.title }).containsExactly("Gym")
            db.notificationDao().upsertByKey(NotificationDigestEntity(notificationKey = "k", packageName = "p", appLabel = "App", title = "t", text = "x", category = null, postedAt = 1, isActive = true, extractionsJson = null))
            db.notificationDao().upsertByKey(NotificationDigestEntity(notificationKey = "k", packageName = "p", appLabel = "App", title = "t2", text = "y", category = null, postedAt = 2, isActive = true, extractionsJson = null))
            assertThat(db.notificationDao().since(0).map { it.title }).containsExactly("t2")
        } finally {
            db.close()
        }
    }

    @Test
    fun uninstallingAnAppRemovesItFromHomePages() = runTest {
        createVersion1Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            db.homeItemDao().insert(HomeItemEntity(type = HomeItemEntity.TYPE_APP, pageIndex = 0, gridX = 1, gridY = 3, componentKey = "com.spotify/.Main", createdAt = 1))
            db.appDao().applySync(emptyList(), listOf("com.spotify/.Main"))
            assertThat(db.homeItemDao().all()).isEmpty()
        } finally {
            db.close()
        }
    }

    private companion object {
        /** Gradle runs unit tests from the module directory. */
        val SCHEMA_DIR = File("schemas/com.ailauncher.data.local.AppDatabase")
    }
}
