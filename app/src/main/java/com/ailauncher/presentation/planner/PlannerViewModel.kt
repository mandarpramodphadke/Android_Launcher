package com.ailauncher.presentation.planner

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.actions.AgendaProvider
import com.ailauncher.data.reminders.CalendarDataSource
import com.ailauncher.domain.model.AgendaEvent
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.repository.PlannerRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.TodoRepository
import com.ailauncher.domain.usecase.FreeSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class PlannerTab(val label: String) { PLAN("Day plan"), TODOS("To-dos") }

/** One row of the day timeline. */
sealed interface TimelineEntry {
    val startMinutes: Int
    val endMinutes: Int

    data class Block(val block: PlannerBlock, val linkedTodo: TodoItem?) : TimelineEntry {
        override val startMinutes get() = block.startMinutes
        override val endMinutes get() = block.endMinutes
    }

    data class Event(val event: AgendaEvent, override val startMinutes: Int, override val endMinutes: Int) : TimelineEntry

    data class Free(override val startMinutes: Int, override val endMinutes: Int) : TimelineEntry
}

data class PlannerUiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val timeline: List<TimelineEntry> = emptyList(),
    val allDayEvents: List<AgendaEvent> = emptyList(),
    val calendarAccess: Boolean = false,
    val todos: List<TodoItem> = emptyList(),
    val lists: List<String> = listOf(TodoItem.DEFAULT_LIST),
    val listFilter: String? = null,
    val use24Hour: Boolean = false,
) {
    val openTodos: List<TodoItem> get() = todos.filter { !it.completed && (listFilter == null || it.listName.equals(listFilter, ignoreCase = true)) }
    val doneTodos: List<TodoItem> get() = todos.filter { it.completed && (listFilter == null || it.listName.equals(listFilter, ignoreCase = true)) }
        .sortedByDescending { it.completedAt ?: it.createdAt }
}

/** The To-do & Daily Planner screen: the same data the manage_todo / manage_planner tools use. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoRepository: TodoRepository,
    private val plannerRepository: PlannerRepository,
    private val agenda: AgendaProvider,
    private val calendar: CalendarDataSource,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** The quick-add field of the To-dos tab. */
    val newTodo = TextFieldState()

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val date = MutableStateFlow(LocalDate.now())
    private val listFilter = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val events: Flow<List<AgendaEvent>> = combine(date, refresh) { d, _ -> d }.mapLatest { agenda.eventsOn(it) }

    val uiState: StateFlow<PlannerUiState> = combine(
        combine(date, date.flatMapLatest { plannerRepository.observeDay(it) }, events) { d, blocks, ev -> Triple(d, blocks, ev) },
        todoRepository.todos,
        listFilter,
        settingsRepository.launcherSettings,
    ) { (day, blocks, dayEvents), todos, filter, settings ->
        val byId = todos.associateBy { it.id }
        val timed = dayEvents.filterNot { it.allDay }.map { e ->
            val start = minutesOf(e.startMillis, day)
            val end = minutesOf(e.endMillis, day).let { if (it <= start) 24 * 60 else it }
            TimelineEntry.Event(e, start, end)
        }
        val blockEntries = blocks.map { TimelineEntry.Block(it, it.linkedTodoId?.let(byId::get)) }
        val busy = blockEntries.map { it.startMinutes to it.endMinutes } + timed.map { it.startMinutes to it.endMinutes }
        val free = FreeSlots.between(busy).map { (s, e) -> TimelineEntry.Free(s, e) }
        PlannerUiState(
            date = day,
            today = LocalDate.now(zone),
            timeline = (blockEntries + timed + free).sortedWith(compareBy({ it.startMinutes }, { it !is TimelineEntry.Block })),
            allDayEvents = dayEvents.filter { it.allDay },
            calendarAccess = calendar.canRead(),
            todos = todos,
            lists = (listOf(TodoItem.DEFAULT_LIST) + todos.map { it.listName }).distinctBy { it.lowercase() },
            listFilter = filter,
            use24Hour = settings.use24HourClock ?: DateFormat.is24HourFormat(context),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    /** Re-reads calendar events (after the calendar permission is granted, or on resume). */
    fun refresh() {
        refresh.value++
    }

    // --------------------------------------------------------------------------- days

    fun previousDay() = date.update { it.minusDays(1) }
    fun nextDay() = date.update { it.plusDays(1) }
    fun goToToday() = date.update { LocalDate.now(zone) }
    fun goTo(day: LocalDate) = date.update { day }

    private inline fun MutableStateFlow<LocalDate>.update(block: (LocalDate) -> LocalDate) {
        value = block(value)
    }

    // -------------------------------------------------------------------------- to-dos

    fun setListFilter(name: String?) {
        listFilter.value = name
    }

    fun addTodoFromField(priority: TodoPriority) {
        val title = newTodo.text.toString().trim()
        if (title.isEmpty()) return
        newTodo.clearText()
        val list = listFilter.value ?: TodoItem.DEFAULT_LIST
        viewModelScope.launch { todoRepository.add(title, null, list, priority, null) }
    }

    fun saveTodo(existing: TodoItem?, title: String, notes: String?, listName: String, priority: TodoPriority, dueAtMillis: Long?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val list = listName.trim().ifEmpty { TodoItem.DEFAULT_LIST }
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            if (existing == null) {
                todoRepository.add(cleanTitle, cleanNotes, list, priority, dueAtMillis)
            } else {
                todoRepository.update(existing.copy(title = cleanTitle, notes = cleanNotes, listName = list, priority = priority, dueAtMillis = dueAtMillis))
            }
        }
    }

    fun toggleTodo(item: TodoItem) {
        viewModelScope.launch { todoRepository.setCompleted(item.id, !item.completed) }
    }

    fun deleteTodo(item: TodoItem) {
        viewModelScope.launch {
            todoRepository.delete(item.id)
            _messages.send("Deleted “${item.title}”")
        }
    }

    // ------------------------------------------------------------------------- planner

    fun saveBlock(existing: PlannerBlock?, title: String, startMinutes: Int, durationMinutes: Int, linkedTodoId: Long?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val duration = durationMinutes.coerceIn(5, 24 * 60 - startMinutes)
        viewModelScope.launch {
            val saved = if (existing == null) {
                plannerRepository.schedule(date.value, startMinutes, duration, cleanTitle, linkedTodoId)
            } else {
                existing.copy(title = cleanTitle, startMinutes = startMinutes, durationMinutes = duration, linkedTodoId = linkedTodoId)
                    .also { plannerRepository.update(it) }
            }
            val clash = plannerRepository.blocksOn(saved.date).count { it.id != saved.id && it.overlaps(saved) }
            if (clash > 0) _messages.send("Heads-up: this overlaps $clash other block${if (clash == 1) "" else "s"}")
        }
    }

    fun toggleBlock(block: PlannerBlock) {
        viewModelScope.launch {
            plannerRepository.update(block.copy(completed = !block.completed))
            // Finishing a block that is tied to a to-do finishes the to-do as well.
            val todo = block.linkedTodoId?.let { todoRepository.get(it) }
            if (!block.completed && todo != null && !todo.completed) todoRepository.setCompleted(todo.id, true)
        }
    }

    fun removeBlock(block: PlannerBlock) {
        viewModelScope.launch {
            plannerRepository.remove(block.id)
            _messages.send("Removed “${block.title}”")
        }
    }

    private fun minutesOf(millis: Long, day: LocalDate): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        return when {
            time.toLocalDate().isBefore(day) -> 0
            time.toLocalDate().isAfter(day) -> 24 * 60
            else -> time.hour * 60 + time.minute
        }
    }
}
