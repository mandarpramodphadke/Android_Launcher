package com.ailauncher.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.ailauncher.data.actions.AndroidActionExecutor
import com.ailauncher.data.auth.GoogleAuthRepository
import com.ailauncher.data.backup.BackupRepositoryImpl
import com.ailauncher.data.categorize.WorkManagerCategorizationScheduler
import com.ailauncher.data.chat.ChatRepositoryImpl
import com.ailauncher.data.download.ModelRepositoryImpl
import com.ailauncher.data.llm.LocalLlmEngine
import com.ailauncher.data.local.AppDatabase
import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.data.local.dao.ChatDao
import com.ailauncher.data.local.dao.ReminderDao
import com.ailauncher.data.packages.PackageRepository
import com.ailauncher.data.preferences.DataStoreSettingsRepository
import com.ailauncher.data.reminders.ReminderRepositoryImpl
import com.ailauncher.data.reminders.UpcomingEventsProviderImpl
import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.AuthRepository
import com.ailauncher.domain.repository.BackupRepository
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.ReminderRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.UpcomingEventsProvider
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
            // v1 schema; future versions ship explicit migrations (schemas/ is exported for that).
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun appDao(db: AppDatabase): AppDao = db.appDao()

    @Provides
    fun chatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
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
}
