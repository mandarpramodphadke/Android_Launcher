package com.ailauncher.presentation.home

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.system.SystemGestureController
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.UpcomingEventsProvider
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.presentation.components.AppActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val now: ZonedDateTime = ZonedDateTime.now(),
    val use24Hour: Boolean = false,
    val upcoming: UpcomingEvent? = null,
    val dock: List<AppInfo> = emptyList(),
    val query: String = "",
    val searchResults: List<AppInfo> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
)

sealed interface HomeEvent {
    data object LockSetupNeeded : HomeEvent
    data class Message(val text: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    upcomingEventsProvider: UpcomingEventsProvider,
    private val gestures: SystemGestureController,
) : ViewModel(), AppActionHandler {

    /** Emits on every minute boundary so the clock flips exactly on time. */
    private val clock: Flow<ZonedDateTime> = flow {
        while (true) {
            val now = ZonedDateTime.now()
            emit(now)
            delay(60_000L - (now.second * 1000L + now.nano / 1_000_000L))
        }
    }

    private val query = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        clock,
        settingsRepository.launcherSettings,
        appRepository.apps,
        upcomingEventsProvider.nextEvent,
        query,
    ) { now, settings, apps, upcoming, q ->
        val visible = apps.filter { !it.isHidden }
        HomeUiState(
            now = now,
            use24Hour = settings.use24HourClock ?: DateFormat.is24HourFormat(context),
            upcoming = upcoming,
            dock = apps.filter { it.dockPosition != null }.sortedBy { it.dockPosition },
            query = q,
            searchResults = if (q.isBlank()) emptyList() else FuzzyMatcher.rank(q, visible, ::searchNames)
                .take(MAX_RESULTS).map { it.first },
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun launch(app: AppInfo) {
        query.value = ""
        viewModelScope.launch {
            if (!appRepository.launch(app)) _events.send(HomeEvent.Message("${app.label} could not be opened"))
        }
    }

    fun onDoubleTap() {
        if (!uiState.value.settings.doubleTapToLock) return
        if (!gestures.lockScreen()) _events.trySend(HomeEvent.LockSetupNeeded)
    }

    fun onSwipeDown() {
        if (uiState.value.settings.swipeDownForNotifications) gestures.expandNotifications()
    }

    fun openClockApp() {
        runCatching {
            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun accessibilitySettingsIntent(): Intent = gestures.accessibilitySettingsIntent()
    fun deviceAdminIntent(): Intent = gestures.deviceAdminIntent()

    fun disableDoubleTapLock() {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(doubleTapToLock = false) } }
    }

    // --- AppActionHandler ------------------------------------------------------------------
    override fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app.packageName)
    override fun uninstall(app: AppInfo) = appRepository.requestUninstall(app.packageName)

    override fun togglePin(app: AppInfo) {
        viewModelScope.launch {
            if (app.isInDock) {
                appRepository.unpinFromDock(app.componentKey)
            } else if (!appRepository.pinToDock(app.componentKey)) {
                _events.send(HomeEvent.Message("The dock is full (${AppRepository.MAX_DOCK_SIZE} apps)"))
            }
        }
    }

    override fun hide(app: AppInfo) {
        viewModelScope.launch { appRepository.setHidden(app.componentKey, true) }
    }

    override fun rename(app: AppInfo, label: String?) {
        viewModelScope.launch { appRepository.setCustomLabel(app.componentKey, label) }
    }

    override fun setCategory(app: AppInfo, category: AppCategory) {
        viewModelScope.launch { appRepository.setCategory(app.packageName, category, CategorySource.USER) }
    }

    override fun moveInDock(app: AppInfo, offset: Int) {
        viewModelScope.launch { appRepository.moveInDock(app.componentKey, offset) }
    }

    private companion object {
        const val MAX_RESULTS = 5

        fun searchNames(app: AppInfo): List<String> =
            listOf(app.label, app.systemLabel, app.packageName.substringAfterLast('.')) + app.aliases
    }
}
