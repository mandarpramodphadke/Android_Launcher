package com.ailauncher.presentation.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.download.ModelDownloadWorker
import com.ailauncher.data.llm.DeviceResources
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.model.ModelFileState
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
    val totalRamBytes: Long = 0,
    val freeStorageBytes: Long = 0,
    val error: String? = null,
) {
    val lowRam: Boolean get() = totalRamBytes in 1 until LOW_RAM_BYTES
    val lowStorage: Boolean get() = file !is ModelFileState.Ready && freeStorageBytes in 1 until spec.sizeBytes + 128L * 1024 * 1024

    private companion object {
        const val LOW_RAM_BYTES = 4L * 1024 * 1024 * 1024
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

    val uiState: StateFlow<ModelSetupUiState> = combine(modelRepository.fileState, error) { file, err ->
        ModelSetupUiState(
            spec = modelRepository.spec,
            file = file,
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

    fun markSeen() {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(modelSetupSeen = true) } }
    }
}
