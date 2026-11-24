package com.ailauncher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.domain.action.LauncherDestination
import com.ailauncher.domain.action.LauncherNavigator
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A press of the Home button. [alreadyOnHome]: the launcher was already in front (not returning from an app). */
data class HomePress(val alreadyOnHome: Boolean)

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val settings: LauncherSettings) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    navigator: LauncherNavigator,
) : ViewModel() {

    /** In-launcher navigation requested by tools ("open my Work folder", "open my planner"). */
    val destinations: Flow<LauncherDestination> = navigator.destinations

    val uiState: StateFlow<MainUiState> = settingsRepository.launcherSettings
        .map<LauncherSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    private val _homePresses = MutableSharedFlow<HomePress>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val homePresses: SharedFlow<HomePress> = _homePresses.asSharedFlow()

    // Replayed once so a share that arrives before the UI is composed (cold start) still opens the assistant.
    private val _shares = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Text or a PDF was shared to the launcher (content waits in SharedContentStore). */
    val shares: SharedFlow<Unit> = _shares.asSharedFlow()

    fun onHomePressed(alreadyOnHome: Boolean) {
        _homePresses.tryEmit(HomePress(alreadyOnHome))
    }

    fun onShared() {
        _shares.tryEmit(Unit)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun onShareHandled() {
        _shares.resetReplayCache()
    }
}
