package com.ailauncher.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class TodoPriority(val wireName: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    ;

    companion object {
        fun fromWire(value: String?): TodoPriority? = when (value?.trim()?.lowercase()) {
            "low", "l", "0", "minor" -> LOW
            "medium", "normal", "m", "1", "mid" -> MEDIUM
            "high", "h", "2", "urgent", "important", "top", "critical" -> HIGH
            else -> null
        }
    }
}

data class TodoItem(
    val id: Long,
    val title: String,
    val notes: String?,
    val listName: String,
    val priority: TodoPriority,
    val dueAtMillis: Long?,
    val completed: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
) {
    companion object {
        const val DEFAULT_LIST = "Inbox"
    }
}

/** A time block of the daily planner (e.g. 09:00-10:30 "Deep work"), optionally tied to a to-do. */
data class PlannerBlock(
    val id: Long,
    val date: LocalDate,
    val startMinutes: Int,
    val durationMinutes: Int,
    val title: String,
    val linkedTodoId: Long?,
    val completed: Boolean,
) {
    val endMinutes: Int get() = startMinutes + durationMinutes
    val startTime: LocalTime get() = LocalTime.of((startMinutes / 60).coerceIn(0, 23), startMinutes % 60)
    val endTime: LocalTime get() = LocalTime.of(((endMinutes / 60) % 24), endMinutes % 60)

    fun overlaps(other: PlannerBlock): Boolean =
        date == other.date && startMinutes < other.endMinutes && other.startMinutes < endMinutes
}

/** One calendar event of the day, as the agenda tool sees it. */
data class AgendaEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
)
