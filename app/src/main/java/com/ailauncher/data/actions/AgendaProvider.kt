package com.ailauncher.data.actions

import android.app.AlarmManager
import android.content.Context
import com.ailauncher.data.reminders.CalendarDataSource
import com.ailauncher.domain.repository.CalendarSourcesRepository
import com.ailauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.ailauncher.domain.model.AgendaEvent
import com.ailauncher.domain.repository.PlannerRepository
import com.ailauncher.domain.repository.ReminderRepository
import com.ailauncher.domain.repository.TodoRepository
import com.ailauncher.domain.usecase.AgendaBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source of the read-only `get_today_agenda` tool ("Summarize my day"): calendar events,
 * reminders, the next alarm, today's planner blocks and the to-dos that need attention.
 */
@Singleton
class AgendaProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendar: CalendarDataSource,
    private val calendarSources: CalendarSourcesRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderRepository,
    private val planner: PlannerRepository,
    private val todos: TodoRepository,
) {
    data class Agenda(val data: JsonObject, val summary: String)

    suspend fun today(): Agenda = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val output = AgendaBuilder.build(
            AgendaBuilder.Input(
                date = today,
                zone = zone,
                events = eventsOn(today),
                calendarAccess = calendar.canRead(),
                reminders = reminders.remindersBetween(start, end),
                nextAlarmMillis = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime,
                blocks = planner.blocksOn(today),
                pendingTodos = todos.all().filter { !it.completed },
            ),
        )
        Agenda(output.data, output.summary)
    }

    /** Calendar events of [date] (empty without calendar permission). */
    suspend fun eventsOn(date: LocalDate): List<AgendaEvent> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val hidden = settings.launcherSettings.first().hiddenCalendarIds
        val device = calendar.eventsBetween(start, end, hidden).map { AgendaEvent(it.title, it.startMillis, it.endMillis, it.allDay, it.location) }
        // Subscribed and imported calendars (Outlook links, iCloud, school timetables, …).
        (device + calendarSources.feedEventsBetween(start, end)).sortedWith(compareBy({ !it.allDay }, { it.startMillis }))
    }
}
