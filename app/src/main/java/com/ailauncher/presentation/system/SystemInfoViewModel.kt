package com.ailauncher.presentation.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.domain.model.LiveSystemStatus
import com.ailauncher.domain.model.SystemDetails
import com.ailauncher.domain.repository.SystemInfoRepository
import com.ailauncher.domain.system.CpuClusters
import com.ailauncher.domain.system.InfoSection
import com.ailauncher.domain.system.SystemInfoSections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The System info page: fixed details, live values and the last minute of CPU and RAM load. */
data class SystemInfoUiState(
    val details: SystemDetails? = null,
    val live: LiveSystemStatus? = null,
    /** Clock load (0..1) per second, oldest first, up to [HISTORY] points. */
    val cpuHistory: List<Float> = emptyList(),
    /** RAM in use (0..1) per second, oldest first. */
    val ramHistory: List<Float> = emptyList(),
    val sections: List<InfoSection> = emptyList(),
) {
    val loading: Boolean get() = details == null && live == null

    companion object {
        const val HISTORY = 60
    }
}

@HiltViewModel
class SystemInfoViewModel @Inject constructor(repository: SystemInfoRepository) : ViewModel() {

    private data class LiveWithHistory(val live: LiveSystemStatus?, val cpu: List<Float>, val ram: List<Float>)

    private val details = flow<SystemDetails?> { emit(repository.details()) }.onStart { emit(null) }

    private val live = repository.live().runningFold(LiveWithHistory(null, emptyList(), emptyList())) { acc, status ->
        val cpu = CpuClusters.clockLoad(status.cpuCores)?.let { (acc.cpu + it).takeLast(SystemInfoUiState.HISTORY) } ?: acc.cpu
        val ram = (acc.ram + status.memory.usedFraction).takeLast(SystemInfoUiState.HISTORY)
        LiveWithHistory(status, cpu, ram)
    }

    /** Reads only while the page is visible (and a few seconds after, for rotation). */
    val uiState: StateFlow<SystemInfoUiState> = combine(details, live) { d, l ->
        SystemInfoUiState(d, l.live, l.cpu, l.ram, SystemInfoSections.build(d, l.live))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), SystemInfoUiState())

    fun reportText(): String = SystemInfoSections.asText(uiState.value.sections)
}
