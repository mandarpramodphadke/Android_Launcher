package com.ailauncher.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.ailauncher.BuildConfig
import com.ailauncher.data.actions.AndroidActionExecutor
import com.ailauncher.data.auth.GoogleAuthRepository
import com.ailauncher.data.backup.BackupRepositoryImpl
import com.ailauncher.data.calendar.CalendarSourcesRepositoryImpl
import com.ailauncher.data.categorize.WorkManagerCategorizationScheduler
import com.ailauncher.data.chat.ChatRepositoryImpl
import com.ailauncher.data.contacts.ContactRepositoryImpl
import com.ailauncher.data.download.ModelRepositoryImpl
import com.ailauncher.data.files.FileRepositoryImpl
import com.ailauncher.data.home.DockRepositoryImpl
import com.ailauncher.data.home.FolderRepositoryImpl
import com.ailauncher.data.home.HomeLayoutRepositoryImpl
import com.ailauncher.data.llm.LocalLlmEngine
import com.ailauncher.data.local.AppDatabase
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
import com.ailauncher.data.memory.MemoryRepositoryImpl
import com.ailauncher.data.notifications.NotificationDigestRepositoryImpl
import com.ailauncher.data.packages.AppLauncher
import com.ailauncher.data.packages.PackageRepository
import com.ailauncher.data.packages.ShortcutRepositoryImpl
import com.ailauncher.data.preferences.DataStoreSettingsRepository
import com.ailauncher.data.productivity.PlannerRepositoryImpl
import com.ailauncher.data.productivity.TodoRepositoryImpl
import com.ailauncher.data.reminders.ReminderRepositoryImpl
import com.ailauncher.data.reminders.UpcomingEventsProviderImpl
import com.ailauncher.data.report.ContentReportRepositoryImpl
import com.ailauncher.data.speech.AndroidSpeechOutput
import com.ailauncher.data.system.SystemInfoRepositoryImpl
import com.ailauncher.data.usage.AppUsageRepositoryImpl
import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.action.LauncherNavigator
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.model.AssistantFeatures
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.AppUsageRepository
import com.ailauncher.domain.repository.AuthRepository
import com.ailauncher.domain.repository.BackupRepository
import com.ailauncher.domain.repository.CalendarSourcesRepository
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.ContactRepository
import com.ailauncher.domain.repository.ContentReportRepository
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FileRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.HomeLayoutRepository
import com.ailauncher.domain.repository.MemoryRepository
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.NotificationDigestRepository
import com.ailauncher.domain.repository.PlannerRepository
import com.ailauncher.domain.repository.ReminderRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.ShortcutRepository
import com.ailauncher.domain.repository.SystemInfoRepository
import com.ailauncher.domain.repository.TodoRepository
import com.ailauncher.domain.repository.UpcomingEventsProvider
import com.ailauncher.domain.speech.SpeechOutput
import com.ailauncher.presentation.navigation.LauncherNavigatorImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Upgrades keep every row through explicit migrations (schemas/ is exported and the
            // migration is tested); only an app downgrade starts from a clean database.
            .addMigrations(*AppDatabase.MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun appDao(db: AppDatabase): AppDao = db.appDao()

    @Provides
    fun chatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun homeItemDao(db: AppDatabase): HomeItemDao = db.homeItemDao()

    @Provides
    fun folderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun todoDao(db: AppDatabase): TodoDao = db.todoDao()

    @Provides
    fun plannerDao(db: AppDatabase): PlannerDao = db.plannerDao()

    @Provides
    fun fileAccessDao(db: AppDatabase): FileAccessDao = db.fileAccessDao()

    @Provides
    fun notificationDao(db: AppDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun calendarFeedDao(db: AppDatabase): CalendarFeedDao = db.calendarFeedDao()

    @Provides
    fun memoryDao(db: AppDatabase): MemoryDao = db.memoryDao()

    /** Assistant features that are on in the full version first (see CLAUDE.md). */
    @Provides
    fun assistantFeatures(): AssistantFeatures = AssistantFeatures(
        betterAnswers = BuildConfig.BETTER_ANSWERS,
        memory = BuildConfig.ASSISTANT_MEMORY,
        spokenReplies = BuildConfig.SPOKEN_REPLIES,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun appRepository(impl: PackageRepository): AppRepository
    @Binds abstract fun chatRepository(impl: ChatRepositoryImpl): ChatRepository
    @Binds abstract fun reminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun authRepository(impl: GoogleAuthRepository): AuthRepository
    @Binds abstract fun backupRepository(impl: BackupRepositoryImpl): BackupRepository
    @Binds abstract fun modelRepository(impl: ModelRepositoryImpl): ModelRepository
    @Binds abstract fun llmEngine(impl: LocalLlmEngine): LlmEngine
    @Binds abstract fun actionExecutor(impl: AndroidActionExecutor): ActionExecutor
    @Binds abstract fun categorizationScheduler(impl: WorkManagerCategorizationScheduler): CategorizationScheduler
    @Binds abstract fun upcomingEvents(impl: UpcomingEventsProviderImpl): UpcomingEventsProvider

    // v2: home pages, folders, dock, productivity, storage insights, notifications, contacts
    @Binds abstract fun homeLayoutRepository(impl: HomeLayoutRepositoryImpl): HomeLayoutRepository
    @Binds abstract fun folderRepository(impl: FolderRepositoryImpl): FolderRepository
    @Binds abstract fun dockRepository(impl: DockRepositoryImpl): DockRepository
    @Binds abstract fun todoRepository(impl: TodoRepositoryImpl): TodoRepository
    @Binds abstract fun plannerRepository(impl: PlannerRepositoryImpl): PlannerRepository
    @Binds abstract fun appUsageRepository(impl: AppUsageRepositoryImpl): AppUsageRepository
    @Binds abstract fun fileRepository(impl: FileRepositoryImpl): FileRepository
    @Binds abstract fun notificationDigestRepository(impl: NotificationDigestRepositoryImpl): NotificationDigestRepository
    @Binds abstract fun contactRepository(impl: ContactRepositoryImpl): ContactRepository
    @Binds abstract fun shortcutRepository(impl: ShortcutRepositoryImpl): ShortcutRepository
    @Binds abstract fun appLauncher(impl: PackageRepository): AppLauncher
    @Binds abstract fun launcherNavigator(impl: LauncherNavigatorImpl): LauncherNavigator
    @Binds abstract fun contentReportRepository(impl: ContentReportRepositoryImpl): ContentReportRepository
    @Binds abstract fun memoryRepository(impl: MemoryRepositoryImpl): MemoryRepository
    @Binds abstract fun speechOutput(impl: AndroidSpeechOutput): SpeechOutput
    @Binds abstract fun systemInfoRepository(impl: SystemInfoRepositoryImpl): SystemInfoRepository
    @Binds abstract fun calendarSourcesRepository(impl: CalendarSourcesRepositoryImpl): CalendarSourcesRepository
}
