package com.ailauncher.domain.repository

import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppSyncResult
import com.ailauncher.domain.model.CategorizationProgress
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.Memory
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ModelOption
import com.ailauncher.domain.model.ModelSpec
import com.ailauncher.domain.model.Reminder
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Installed launchable apps, merged with user customisations and AI categories. */
interface AppRepository {
    /** Every launchable activity, including hidden ones (filter with [AppInfo.isHidden]). */
    val apps: Flow<List<AppInfo>>

    /** Reconciles the cache with PackageManager; newly installed apps are queued for AI sorting. */
    suspend fun sync(): AppSyncResult

    /** Launches the app; returns false if it is no longer launchable. */
    suspend fun launch(app: AppInfo): Boolean

    fun openAppInfo(packageName: String)
    fun requestUninstall(packageName: String)

    suspend fun setHidden(componentKey: String, hidden: Boolean)
    suspend fun setCustomLabel(componentKey: String, label: String?)
    suspend fun setCategory(packageName: String, category: AppCategory, source: CategorySource)
    suspend fun setCategories(categories: Map<String, AppCategory>, source: CategorySource)
    suspend fun resetAutomaticCategories()

    suspend fun pinToDock(componentKey: String): Boolean
    suspend fun unpinFromDock(componentKey: String)
    suspend fun moveInDock(componentKey: String, offset: Int)

    suspend fun addAlias(componentKey: String, alias: String)
    suspend fun removeAlias(alias: String)

    /** Seeds the dock with the default dialer, messages, browser and camera on first run. */
    suspend fun ensureDefaultDock()

    /** Android's own `android:appCategory`, used as a free, exact hint before the LLM. */
    fun systemCategoryOf(packageName: String): AppCategory?

    companion object {
        const val MAX_DOCK_SIZE = 5
    }
}

interface CategorizationScheduler {
    val progress: Flow<CategorizationProgress>

    /** Queues a background pass; [force] re-categorises everything except user overrides. */
    fun schedule(force: Boolean = false)
}

interface ChatRepository {
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun messages(conversationId: String, limit: Int = 60): List<ChatMessage>
    suspend fun insert(message: ChatMessage): Long
    suspend fun update(message: ChatMessage)
    suspend fun updateToolInvocation(messageId: Long, invocation: ToolInvocation)
    suspend fun message(id: Long): ChatMessage?
    suspend fun clear(conversationId: String)

    /** Messages left STREAMING by a process death are marked as interrupted on start-up. */
    suspend fun recoverInterruptedMessages()

    companion object {
        const val DEFAULT_CONVERSATION = "default"
    }
}

sealed interface ReminderScheduleResult {
    data class InCalendar(val reminder: Reminder) : ReminderScheduleResult
    data class Local(val reminder: Reminder, val exact: Boolean) : ReminderScheduleResult
}

interface ReminderRepository {
    fun observeUpcoming(): Flow<List<Reminder>>
    suspend fun schedule(title: String, triggerAtMillis: Long): ReminderScheduleResult
    suspend fun remindersBetween(startMillis: Long, endMillis: Long): List<Reminder>
    suspend fun markFired(id: Long)
    suspend fun cancel(id: Long)
    suspend fun rescheduleAll()
}

interface UpcomingEventsProvider {
    /** Soonest upcoming system alarm or reminder, for the home-screen pill. */
    val nextEvent: Flow<UpcomingEvent?>
}

interface SettingsRepository {
    val launcherSettings: Flow<LauncherSettings>
    val modelConfig: Flow<ModelConfig>
    suspend fun updateLauncherSettings(transform: (LauncherSettings) -> LauncherSettings)
    suspend fun updateModelConfig(transform: (ModelConfig) -> ModelConfig)
}

interface AuthRepository {
    /** Null until the user has chosen Google sign-in or guest mode. */
    val profile: StateFlow<UserProfile?>
    val isGoogleSignInConfigured: Boolean
    val isCloudBackupAvailable: Boolean
    suspend fun continueAsGuest()
    suspend fun signOut()
}

/**
 * The on-device models: a small catalog (Qwen3 0.6B / 1.7B / 4B) plus GGUF files the user imported.
 * Exactly one is active ([spec], persisted as ModelConfig.activeModelId); [fileState],
 * [readyModelFile] and the download controls refer to the active model.
 */
interface ModelRepository {
    /** The active model (the default until settings are read). */
    val spec: ModelSpec
    val fileState: Flow<ModelFileState>

    /** Built-in downloadable models. */
    val catalog: List<ModelSpec>
    /** Catalog plus imported models, each with its own state, for the model switcher. */
    val options: Flow<List<ModelOption>>

    /** The active model file if it is fully present on disk. */
    fun readyModelFile(): File?
    /** Like [readyModelFile], but waits for the persisted choice of model first. */
    suspend fun activeModelFile(): File?
    fun startDownload()
    fun pauseDownload()
    fun cancelDownload()

    /** Makes [id] the active model (it may still need downloading). */
    suspend fun select(id: String)

    /** Copies a user-picked `.gguf` (content URI string) into app storage and makes it active. */
    suspend fun importFrom(uri: String): Result<Unit>
    /** Deletes the active model's file. */
    suspend fun deleteModel()
    /** Deletes any model's file (imported models disappear from the list). */
    suspend fun delete(id: String)
}

data class BackupSummary(
    val createdAt: Long,
    val pinnedApps: Int,
    val hiddenApps: Int,
    val categories: Int,
    val aliases: Int,
)

interface BackupRepository {
    /** Serialises settings, dock, hidden apps, aliases and AI categories, encrypted with [passphrase]. */
    suspend fun export(passphrase: CharArray): ByteArray
    suspend fun import(data: ByteArray, passphrase: CharArray): BackupSummary
    suspend fun uploadToCloud(passphrase: CharArray): Result<Long>
    suspend fun restoreFromCloud(passphrase: CharArray): Result<BackupSummary>
}

/** Things the user asked the assistant to remember (ASSISTANT_MEMORY). Kept on the phone only. */
interface MemoryRepository {
    /** Newest first. */
    val memories: Flow<List<Memory>>
    suspend fun all(): List<Memory>

    /** Saves [text] (cleaned; an existing identical memory is kept instead); null if it is empty. */
    suspend fun remember(text: String): Memory?
    suspend fun delete(id: Long)

    /** Deletes the memories [query] is about (all of them for "everything"); returns what was deleted. */
    suspend fun forget(query: String): List<Memory>
    suspend fun clear()
}

