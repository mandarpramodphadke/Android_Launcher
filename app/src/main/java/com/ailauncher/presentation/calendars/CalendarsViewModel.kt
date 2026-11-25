package com.ailauncher.presentation.calendars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.domain.model.CalendarFeed
import com.ailauncher.domain.model.DeviceCalendar
import com.ailauncher.domain.repository.CalendarSourcesRepository
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarsUiState(
    val canRead: Boolean = false,
    val deviceCalendars: List<DeviceCalendar> = emptyList(),
    val hiddenIds: Set<Long> = emptySet(),
    /** Null: automatic (the primary synced calendar). */
    val reminderCalendarId: Long? = null,
    val feeds: List<CalendarFeed> = emptyList(),
    /** Feeds being downloaded or refreshed right now (-1 while a new one is added). */
    val working: Set<Long> = emptySet(),
)

@HiltViewModel
class CalendarsViewModel @Inject constructor(
    private val sources: CalendarSourcesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val device = MutableStateFlow<Pair<Boolean, List<DeviceCalendar>>>(false to emptyList())
    private val working = MutableStateFlow<Set<Long>>(emptySet())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<CalendarsUiState> = combine(device, settings.launcherSettings, sources.feeds, working) { (canRead, list), s, feeds, busy ->
        CalendarsUiState(canRead, list, s.hiddenCalendarIds, s.reminderCalendarId, feeds, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarsUiState())

    init {
        reload()
    }

    /** Re-reads the phone's calendars (after the permission is granted, or on return). */
    fun reload() {
        viewModelScope.launch { device.value = sources.canReadDeviceCalendars() to sources.deviceCalendars() }
    }

    fun setShown(calendar: DeviceCalendar, shown: Boolean) {
        viewModelScope.launch {
            settings.updateLauncherSettings { s ->
                s.copy(hiddenCalendarIds = if (shown) s.hiddenCalendarIds - calendar.id else s.hiddenCalendarIds + calendar.id)
            }
        }
    }

    fun setReminderCalendar(id: Long?) {
        viewModelScope.launch { settings.updateLauncherSettings { it.copy(reminderCalendarId = id) } }
    }

    fun subscribe(name: String, url: String) = run(NEW) {
        sources.subscribe(name, url).fold(
            onSuccess = { "Added ${it.name}: ${events(it.eventCount)}" },
            onFailure = { it.message ?: "That calendar couldn't be added" },
        )
    }

    fun importFile(uri: String) = run(NEW) {
        sources.importFile(null, uri).fold(
            onSuccess = { "Imported ${it.name}: ${events(it.eventCount)}" },
            onFailure = { it.message ?: "That file couldn't be imported" },
        )
    }

    fun refresh(feed: CalendarFeed) = run(feed.id) {
        sources.refresh(feed.id).fold(
            onSuccess = { "Updated ${it.name}: ${events(it.eventCount)}" },
            onFailure = { it.message ?: "Couldn't update ${feed.name}" },
        )
    }

    fun remove(feed: CalendarFeed) {
        viewModelScope.launch {
            sources.remove(feed.id)
            _messages.send("Removed ${feed.name}")
        }
    }

    private fun run(key: Long, block: suspend () -> String) {
        viewModelScope.launch {
            working.update { it + key }
            val message = try {
                block()
            } finally {
                working.update { it - key }
            }
            _messages.send(message)
        }
    }

    private fun events(count: Int) = if (count == 1) "1 event" else "$count events"

    companion object {
        const val NEW = -1L
    }
}
