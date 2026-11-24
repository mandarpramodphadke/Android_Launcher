package com.ailauncher

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.ailauncher.data.notifications.NotificationDigestRepositoryImpl
import com.ailauncher.data.packages.AppIconFetcher
import com.ailauncher.data.packages.ShortcutIconFetcher
import com.ailauncher.data.packages.ShortcutRepositoryImpl
import com.ailauncher.data.packages.PackageChangeReceiver
import com.ailauncher.data.packages.PackageRepository
import com.ailauncher.data.reminders.Notifications
import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.ReminderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AiLauncherApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var packageRepository: PackageRepository
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var llmEngine: LlmEngine
    @Inject lateinit var shortcutRepository: ShortcutRepositoryImpl
    @Inject lateinit var notificationDigest: NotificationDigestRepositoryImpl

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    /**
     * Resolved through an entry point rather than a field: field injection itself constructs
     * singletons that call WorkManager.getInstance(), which re-enters this getter before an
     * injected field would be guaranteed to be set.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java).workerFactory())
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)

        // Implicit package broadcasts only reach runtime-registered receivers on Android 8+.
        ContextCompat.registerReceiver(
            this,
            PackageChangeReceiver { _, _ -> packageRepository.requestSync() },
            PackageChangeReceiver.intentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        appScope.launch {
            chatRepository.recoverInterruptedMessages()
            packageRepository.sync() // catch up on changes made while the process was dead
            packageRepository.ensureDefaultDock()
            reminderRepository.rescheduleAll()
            notificationDigest.purgeExpired()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        llmEngine.onTrimMemory(level)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AppIconFetcher.Factory(packageManager))
                add(AppIconFetcher.Key())
                // Long-press menu shortcut icons (LauncherApps.getShortcutIconDrawable).
                add(ShortcutIconFetcher.Factory(shortcutRepository))
                add(ShortcutIconFetcher.Key())
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.15).build()
            }
            .build()
}
