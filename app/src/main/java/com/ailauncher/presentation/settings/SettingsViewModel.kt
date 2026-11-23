package com.ailauncher.presentation.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.auth.GoogleAuthRepository
import com.ailauncher.data.backup.BackupCrypto
import com.ailauncher.data.llm.DeviceResources
import com.ailauncher.data.system.DefaultLauncherHelper
import com.ailauncher.data.system.LockMethod
import com.ailauncher.data.system.SystemGestureController
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.UserProfile
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.BackupRepository
import com.ailauncher.domain.repository.BackupSummary
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.usecase.ChatAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class SystemStatus(
    val isDefaultLauncher: Boolean = false,
    val lockMethod: LockMethod = LockMethod.NONE,
)

data class SettingsUiState(
    val profile: UserProfile? = null,
    val googleSignInConfigured: Boolean = false,
    val cloudBackupAvailable: Boolean = false,
    val settings: LauncherSettings = LauncherSettings(),
    val modelConfig: ModelConfig = ModelConfig(),
    val engine: EngineState = EngineState.Unloaded,
    val modelFile: ModelFileState = ModelFileState.Missing,
    val system: SystemStatus = SystemStatus(),
    val hiddenApps: List<AppInfo> = emptyList(),
    val performanceCores: Int = 4,
    val busy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: GoogleAuthRepository,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val backupRepository: BackupRepository,
    private val categorizationScheduler: CategorizationScheduler,
    private val chatAgent: ChatAgent,
    private val llm: LlmEngine,
    private val defaultLauncher: DefaultLauncherHelper,
    private val gestures: SystemGestureController,
    modelRepository: ModelRepository,
    deviceResources: DeviceResources,
) : ViewModel() {

    private val system = MutableStateFlow(SystemStatus())
    private val busy = MutableStateFlow(false)
    private val cores = deviceResources.performanceCoreCount

    private val hiddenApps: Flow<List<AppInfo>> = appRepository.apps.map { apps -> apps.filter { it.isHidden } }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(auth.profile, settingsRepository.launcherSettings, settingsRepository.modelConfig, ::Triple),
        combine(llm.state, modelRepository.fileState, ::Pair),
        system,
        hiddenApps,
        busy,
    ) { (profile, settings, config), (engine, file), sys, hidden, isBusy ->
        SettingsUiState(
            profile = profile,
            googleSignInConfigured = auth.isGoogleSignInConfigured,
            cloudBackupAvailable = auth.isCloudBackupAvailable,
            settings = settings,
            modelConfig = config,
            engine = engine,
            modelFile = file,
            system = sys,
            hiddenApps = hidden,
            performanceCores = cores,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Re-read system state that can change while we are in another Settings app. */
    fun refreshSystemStatus() {
        system.value = SystemStatus(isDefaultLauncher = defaultLauncher.isDefaultLauncher, lockMethod = gestures.lockMethod)
    }

    fun defaultLauncherIntent(): Intent = defaultLauncher.requestIntent()
    fun homeSettingsIntent(): Intent = defaultLauncher.homeSettingsIntent()
    fun accessibilitySettingsIntent(): Intent = gestures.accessibilitySettingsIntent()
    fun deviceAdminIntent(): Intent = gestures.deviceAdminIntent()

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { settingsRepository.updateLauncherSettings(transform) }
    }

    fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) {
        viewModelScope.launch { settingsRepository.updateModelConfig(transform) }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            auth.signIn(activity)
                .onSuccess { _messages.send("Signed in as ${it.displayName}") }
                .onFailure { if (it !is GoogleAuthRepository.SignInCancelledException) _messages.send(it.message ?: "Sign-in failed") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _messages.send("Signed out. The launcher keeps working offline.")
        }
    }

    fun unhide(app: AppInfo) {
        viewModelScope.launch { appRepository.setHidden(app.componentKey, false) }
    }

    fun resortAllApps() {
        categorizationScheduler.schedule(force = true)
        viewModelScope.launch { _messages.send("Re-sorting all apps with on-device AI…") }
    }

    fun resetCategories() {
        viewModelScope.launch {
            appRepository.resetAutomaticCategories()
            categorizationScheduler.schedule(force = false)
            _messages.send("Automatic categories cleared; your manual choices were kept.")
        }
    }

    fun clearChatHistory() {
        chatAgent.clearConversation()
        viewModelScope.launch { _messages.send("Chat history cleared") }
    }

    fun unloadModel() {
        viewModelScope.launch {
            llm.unload()
            _messages.send("Model freed from memory")
        }
    }

    // --- Backup ----------------------------------------------------------------------------

    fun exportBackup(uri: Uri, passphrase: CharArray) = runBusy {
        val bytes = backupRepository.export(passphrase)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IllegalStateException("Cannot write the backup file")
        }
        "Encrypted backup saved"
    }

    fun importBackup(uri: Uri, passphrase: CharArray) = runBusy {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot read the backup file")
        }
        describeRestore(backupRepository.import(bytes, passphrase))
    }

    fun uploadToCloud(passphrase: CharArray) = runBusy {
        val createdAt = backupRepository.uploadToCloud(passphrase).getOrThrow()
        "Backed up to your account at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(createdAt))}"
    }

    fun restoreFromCloud(passphrase: CharArray) = runBusy {
        describeRestore(backupRepository.restoreFromCloud(passphrase).getOrThrow())
    }

    private fun describeRestore(summary: BackupSummary): String =
        "Restored ${summary.categories} categories, ${summary.pinnedApps} dock apps and ${summary.hiddenApps} hidden apps"

    private fun runBusy(block: suspend () -> String) {
        viewModelScope.launch {
            busy.value = true
            try {
                _messages.send(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupCrypto.WrongPassphraseException) {
                _messages.send("Wrong passphrase")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Backup failed")
            } finally {
                busy.value = false
            }
        }
    }
}
