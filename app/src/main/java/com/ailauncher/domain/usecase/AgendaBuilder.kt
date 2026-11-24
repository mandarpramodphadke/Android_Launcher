package com.ailauncher.domain.usecase

import com.ailauncher.domain.model.AgendaEvent
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.Reminder
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
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

/**
 * Builds the `get_today_agenda` result: calendar events, reminders, the next alarm, the day's
 * planner time blocks and the to-dos that need attention (high priority, due today or overdue).
 * The JSON is what the model reads, so it stays compact (HH:mm times, capped lists).
 */
object AgendaBuilder {

    data class Input(
        val date: LocalDate,
        val zone: ZoneId,
        val events: List<AgendaEvent>,
        val calendarAccess: Boolean,
        val reminders: List<Reminder>,
        val nextAlarmMillis: Long?,
        val blocks: List<PlannerBlock>,
        val pendingTodos: List<TodoItem>,
    )

    data class Output(val data: JsonObject, val summary: String)

    /** To-dos worth mentioning in a day summary, most urgent first. */
    fun focusTodos(todos: List<TodoItem>, date: LocalDate, zone: ZoneId, limit: Int = MAX_TODOS): List<TodoItem> {
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return todos
            .filter { !it.completed && (it.priority == TodoPriority.HIGH || (it.dueAtMillis != null && it.dueAtMillis < endOfDay)) }
            .sortedWith(compareBy<TodoItem>({ it.dueAtMillis ?: Long.MAX_VALUE }, { -it.priority.ordinal }, { it.createdAt }))
            .take(limit)
    }

    fun build(input: Input): Output {
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
        fun fmt(millis: Long) = Instant.ofEpochMilli(millis).atZone(input.zone).format(time)
        val startOfDay = input.date.atStartOfDay(input.zone).toInstant().toEpochMilli()
        val endOfDay = input.date.plusDays(1).atStartOfDay(input.zone).toInstant().toEpochMilli()
        val blocks = input.blocks.filter { it.date == input.date }.sortedBy { it.startMinutes }
        val todos = focusTodos(input.pendingTodos, input.date, input.zone)

        val data = buildJsonObject {
            put("date", input.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)))
            put("calendar_access", input.calendarAccess)
            putJsonArray("events") {
                input.events.sortedBy { it.startMillis }.forEach { e ->
                    add(
                        buildJsonObject {
                            put("title", e.title)
                            if (e.allDay) {
                                put("all_day", true)
                            } else {
                                put("start", fmt(e.startMillis))
                                put("end", fmt(e.endMillis))
                            }
                            e.location?.takeIf { it.isNotBlank() }?.let { put("location", it) }
                        },
                    )
                }
            }
            putJsonArray("reminders") {
                input.reminders.sortedBy { it.triggerAtMillis }.forEach { r ->
                    add(buildJsonObject { put("title", r.title); put("time", fmt(r.triggerAtMillis)) })
                }
            }
            input.nextAlarmMillis?.let { alarm ->
                putJsonObject("next_alarm") {
                    put("time", fmt(alarm))
                    put("is_today", alarm in startOfDay until endOfDay)
                }
            }
            putJsonArray("plan") {
                blocks.forEach { b ->
                    add(
                        buildJsonObject {
                            put("time", "${b.startTime.format(time)}-${b.endTime.format(time)}")
                            put("title", b.title)
                            if (b.completed) put("done", true)
                        },
                    )
                }
            }
            putJsonArray("todos") {
                todos.forEach { t ->
                    add(
                        buildJsonObject {
                            put("title", t.title)
                            if (t.priority == TodoPriority.HIGH) put("priority", "high")
                            t.dueAtMillis?.let { due ->
                                put("due", if (due < startOfDay) "overdue" else fmt(due))
                            }
                        },
                    )
                }
            }
        }

        val parts = buildList {
            add(if (input.events.isEmpty()) "No calendar events" else plural(input.events.size, "event"))
            if (input.reminders.isNotEmpty()) add(plural(input.reminders.size, "reminder"))
            if (blocks.isNotEmpty()) add(plural(blocks.size, "planned block"))
            if (todos.isNotEmpty()) add(plural(todos.size, "urgent to-do"))
            input.nextAlarmMillis?.let { add("next alarm ${fmt(it)}") }
        }
        val summary = parts.joinToString(" · ") + if (!input.calendarAccess) " (calendar access not granted)" else ""
        return Output(data, summary)
    }

    private fun plural(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

    private const val MAX_TODOS = 8
}
