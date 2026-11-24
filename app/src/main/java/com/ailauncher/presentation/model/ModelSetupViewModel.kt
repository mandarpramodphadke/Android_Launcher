package com.ailauncher.presentation.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.download.ModelDownloadWorker
import com.ailauncher.data.llm.DeviceResources
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ModelOption
import com.ailauncher.domain.model.ModelSpec
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSetupUiState(
    val spec: ModelSpec,
    val file: ModelFileState = ModelFileState.Missing,
    val options: List<ModelOption> = emptyList(),
    val totalRamBytes: Long = 0,
    val freeStorageBytes: Long = 0,
    val error: String? = null,
) {
    val lowRam: Boolean get() = totalRamBytes in 1 until maxOf(spec.recommendedRamBytes, LOW_RAM_BYTES)
    val lowStorage: Boolean get() = file !is ModelFileState.Ready && freeStorageBytes in 1 until spec.sizeBytes + 128L * 1024 * 1024

    fun tooBigFor(option: ModelSpec): Boolean = totalRamBytes in 1 until option.recommendedRamBytes

    private companion object {
        const val LOW_RAM_BYTES = 3L * 1024 * 1024 * 1024
    }
}

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val llm: LlmEngine,
    deviceResources: DeviceResources,
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)
    private val totalRam = deviceResources.memoryInfo().totalMem

    val uiState: StateFlow<ModelSetupUiState> = combine(modelRepository.fileState, modelRepository.options, error) { file, options, err ->
        ModelSetupUiState(
            spec = options.firstOrNull { it.active }?.spec ?: modelRepository.spec,
            file = file,
            options = options,
            totalRamBytes = totalRam,
            freeStorageBytes = ModelDownloadWorker.allocatableBytes(context, ModelDownloadWorker.modelsDir(context)),
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelSetupUiState(modelRepository.spec))

    fun download() {
        error.value = null
        modelRepository.startDownload()
    }

    fun pause() = modelRepository.pauseDownload()

    fun cancel() = modelRepository.cancelDownload()

    fun import(uri: Uri) {
        error.value = null
        viewModelScope.launch {
            modelRepository.importFrom(uri.toString()).onFailure { error.value = it.message ?: "Import failed" }
        }
    }

    fun delete() {
        viewModelScope.launch {
            llm.unload()
            modelRepository.deleteModel()
        }
    }

    /** Switches the assistant to another model (downloaded or not); the engine reloads on next use. */
    fun select(id: String) {
        error.value = null
        viewModelScope.launch { modelRepository.select(id) }
    }

    fun deleteOption(id: String) {
        viewModelScope.launch {
            if (modelRepository.spec.id == id) llm.unload()
            modelRepository.delete(id)
        }
    }

    fun markSeen() {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(modelSetupSeen = true) } }
    }
}
