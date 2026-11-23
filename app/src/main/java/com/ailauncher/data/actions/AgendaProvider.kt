package com.ailauncher.data.actions

import android.app.AlarmManager
import android.content.Context
import com.ailauncher.data.reminders.CalendarDataSource
import com.ailauncher.domain.repository.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Data source of the read-only `get_today_agenda` tool ("Summarize my day"). */
@Singleton
class AgendaProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendar: CalendarDataSource,
    private val reminders: ReminderRepository,
) {
    data class Agenda(val data: JsonObject, val summary: String)

    suspend fun today(): Agenda = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

        val events = calendar.eventsBetween(start, end)
        val todaysReminders = reminders.remindersBetween(start, end)
        val nextAlarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime

        fun fmt(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).format(time)

        val data = buildJsonObject {
            put("date", today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)))
            put("calendar_access", calendar.canRead())
            putJsonArray("events") {
                events.forEach { e ->
                    add(
                        buildJsonObject {
                            put("title", e.title)
                            if (e.allDay) put("all_day", true) else {
                                put("start", fmt(e.startMillis))
                                put("end", fmt(e.endMillis))
                            }
                            e.location?.let { put("location", it) }
                        },
                    )
                }
            }
            putJsonArray("reminders") {
                todaysReminders.forEach { r ->
                    add(buildJsonObject { put("title", r.title); put("time", fmt(r.triggerAtMillis)) })
                }
            }
            if (nextAlarm != null) {
                putJsonObject("next_alarm") {
                    put("time", fmt(nextAlarm))
                    put("is_today", nextAlarm < end)
                }
            }
        }
        val parts = buildList {
            add(if (events.isEmpty()) "No calendar events" else "${events.size} event${if (events.size == 1) "" else "s"}")
            if (todaysReminders.isNotEmpty()) add("${todaysReminders.size} reminder${if (todaysReminders.size == 1) "" else "s"}")
            nextAlarm?.let { add("next alarm ${fmt(it)}") }
        }
        Agenda(data, parts.joinToString(" · ") + if (!calendar.canRead()) " (calendar access not granted)" else "")
    }
}
