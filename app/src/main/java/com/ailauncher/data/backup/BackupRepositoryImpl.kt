package com.ailauncher.data.backup

import android.os.Build
import com.ailauncher.BuildConfig
import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.data.local.entity.AppAliasEntity
import com.ailauncher.data.local.entity.AppCategoryEntity
import com.ailauncher.data.local.entity.AppPrefsEntity
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.repository.AuthRepository
import com.ailauncher.domain.repository.BackupRepository
import com.ailauncher.domain.repository.BackupSummary
import com.ailauncher.domain.repository.MemoryRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Plain-text payload inside the encrypted envelope. Never contains chat history. */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val appVersion: String,
    val device: String,
    val settings: LauncherSettings,
    val modelConfig: ModelConfig,
    val apps: List<AppPrefsSnapshot>,
    val categories: List<CategorySnapshot>,
    val aliases: List<AliasSnapshot>,
    /** What the assistant was asked to remember (1.8, ASSISTANT_MEMORY); empty in older backups. */
    val memories: List<String> = emptyList(),
)

@Serializable
data class AppPrefsSnapshot(
    val componentKey: String,
    val packageName: String,
    val customLabel: String? = null,
    val hidden: Boolean = false,
    val dockPosition: Int? = null,
)

@Serializable
data class CategorySnapshot(val packageName: String, val category: String, val source: String)

@Serializable
data class AliasSnapshot(val alias: String, val componentKey: String)

/**
 * Encrypted export/import of launcher settings, pinned apps, hidden apps, aliases and AI
 * categories, stored as a local file (Storage Access Framework) or in Firestore at
 * `users/{uid}/backups/latest` (see firestore.rules).
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val appDao: AppDao,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val memoryRepository: MemoryRepository,
) : BackupRepository {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun export(passphrase: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val payload = BackupPayload(
            createdAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            settings = settingsRepository.launcherSettings.first(),
            modelConfig = settingsRepository.modelConfig.first(),
            apps = appDao.allPrefs()
                .filter { it.customLabel != null || it.isHidden || it.dockPosition != null }
                .map { AppPrefsSnapshot(it.componentKey, it.packageName, it.customLabel, it.isHidden, it.dockPosition) },
            categories = appDao.allCategories().map { CategorySnapshot(it.packageName, it.category, it.source) },
            aliases = appDao.allAliases().map { AliasSnapshot(it.displayAlias, it.componentKey) },
            memories = if (BuildConfig.ASSISTANT_MEMORY) memoryRepository.all().map { it.text } else emptyList(),
        )
        val envelope = BackupCrypto.encrypt(json.encodeToString(BackupPayload.serializer(), payload).toByteArray(), passphrase)
        json.encodeToString(BackupCrypto.Envelope.serializer(), envelope).toByteArray()
    }

    override suspend fun import(data: ByteArray, passphrase: CharArray): BackupSummary = withContext(Dispatchers.Default) {
        val envelope = runCatching { json.decodeFromString(BackupCrypto.Envelope.serializer(), data.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("This file is not an AI Launcher backup.") }
        val payload = json.decodeFromString(BackupPayload.serializer(), BackupCrypto.decrypt(envelope, passphrase).decodeToString())
        apply(payload)
        BackupSummary(
            createdAt = payload.createdAt,
            pinnedApps = payload.apps.count { it.dockPosition != null },
            hiddenApps = payload.apps.count { it.hidden },
            categories = payload.categories.size,
            aliases = payload.aliases.size,
        )
    }

    private suspend fun apply(payload: BackupPayload) {
        // Onboarding stays completed: restoring must never lock the user out of their home screen.
        settingsRepository.updateLauncherSettings { payload.settings.copy(onboardingCompleted = true, modelSetupSeen = true) }
        settingsRepository.updateModelConfig { payload.modelConfig }

        val existing = appDao.allPrefs().associateBy { it.componentKey }
        appDao.upsertPrefs(
            payload.apps.map { snapshot ->
                (existing[snapshot.componentKey] ?: AppPrefsEntity(snapshot.componentKey, snapshot.packageName)).copy(
                    customLabel = snapshot.customLabel,
                    isHidden = snapshot.hidden,
                    dockPosition = snapshot.dockPosition,
                )
            },
        )
        val now = System.currentTimeMillis()
        appDao.upsertCategories(
            payload.categories.mapNotNull { c ->
                val category = AppCategory.fromName(c.category) ?: return@mapNotNull null
                val source = CategorySource.entries.firstOrNull { it.name == c.source } ?: CategorySource.AI
                AppCategoryEntity(c.packageName, category.name, source.name, now)
            },
        )
        appDao.upsertAliases(
            payload.aliases.map { AppAliasEntity(com.ailauncher.domain.search.FuzzyMatcher.normalize(it.alias), it.componentKey, it.alias) },
        )
        // Oldest first, so the restored memories keep their order; identical ones are kept once.
        if (BuildConfig.ASSISTANT_MEMORY) payload.memories.asReversed().forEach { memoryRepository.remember(it) }
    }

    override suspend fun uploadToCloud(passphrase: CharArray): Result<Long> = cloud { uid ->
        val bytes = export(passphrase)
        val createdAt = System.currentTimeMillis()
        document(uid).set(
            mapOf(
                "envelope" to bytes.decodeToString(),
                "createdAt" to createdAt,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "appVersion" to BuildConfig.VERSION_NAME,
            ),
        ).await()
        createdAt
    }

    override suspend fun restoreFromCloud(passphrase: CharArray): Result<BackupSummary> = cloud { uid ->
        val snapshot = document(uid).get().await()
        val envelope = snapshot.getString("envelope") ?: throw IllegalStateException("No cloud backup found for this account.")
        import(envelope.toByteArray(), passphrase)
    }

    private fun document(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("backups").document("latest")

    private suspend fun <T> cloud(block: suspend (uid: String) -> T): Result<T> {
        val profile = authRepository.profile.value
        if (!authRepository.isCloudBackupAvailable || profile == null || profile.isGuest) {
            return Result.failure(IllegalStateException("Sign in with Google (with Firebase configured) to use cloud backup."))
        }
        return try {
            Result.success(block(profile.uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
