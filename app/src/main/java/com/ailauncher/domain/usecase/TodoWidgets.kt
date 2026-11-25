package com.ailauncher.domain.usecase

import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** When a to-do is due, relative to today. */
enum class DueState { OVERDUE, TODAY, TOMORROW, LATER }

/** What the "Today" widget shows: the block in progress or next, and to-dos due today or earlier. */
data class TodayOverview(
    val current: PlannerBlock?,
    val next: PlannerBlock?,
    val due: List<TodoItem>,
    val openTodos: Int,
)

/** Pure selection and ordering for the launcher's to-do widgets. */
object TodoWidgets {

    /** Ticked-off items stay visible (struck through) this long, so a mis-tap is easy to undo. */
    const val RECENTLY_DONE_MILLIS = 10 * 60_000L

    fun dueState(item: TodoItem, nowMillis: Long, zone: ZoneId): DueState? {
        val due = item.dueAtMillis ?: return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when {
            day < today -> DueState.OVERDUE
            // A time today that has already passed counts as overdue.
            day == today -> if (due < nowMillis) DueState.OVERDUE else DueState.TODAY
            day == today.plusDays(1) -> DueState.TOMORROW
            else -> DueState.LATER
        }
    }

    /**
     * Open to-dos for a widget (optionally one list): overdue first, then due today, then by
     * priority, due date and age. Then items ticked off in the last few minutes, struck through.
     */
    fun rows(todos: List<TodoItem>, listName: String?, nowMillis: Long, zone: ZoneId): List<TodoItem> {
        val inList = todos.filter { listName == null || it.listName.equals(listName, ignoreCase = true) }
        val open = inList.filter { !it.completed }.sortedWith(openOrder(nowMillis, zone))
        val recentlyDone = inList
            .filter { it.completed && (it.completedAt ?: 0L) >= nowMillis - RECENTLY_DONE_MILLIS }
            .sortedByDescending { it.completedAt }
        return open + recentlyDone
    }

    fun openCount(todos: List<TodoItem>, listName: String?): Int =
        todos.count { !it.completed && (listName == null || it.listName.equals(listName, ignoreCase = true)) }

    /** List names in use, the default list first, then A–Z. */
    fun lists(todos: List<TodoItem>): List<String> {
        val names = todos.map { it.listName.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        return (listOf(TodoItem.DEFAULT_LIST) + names.sortedWith(String.CASE_INSENSITIVE_ORDER))
            .distinctBy { it.lowercase() }
    }

    fun today(todos: List<TodoItem>, blocks: List<PlannerBlock>, nowMillis: Long, zone: ZoneId): TodayOverview {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val date: LocalDate = now.toLocalDate()
        val minute = now.hour * 60 + now.minute
        val todaysBlocks = blocks.filter { it.date == date && !it.completed }.sortedBy { it.startMinutes }
        val current = todaysBlocks.firstOrNull { minute >= it.startMinutes && minute < it.endMinutes }
        val next = todaysBlocks.firstOrNull { it.startMinutes > minute }
        val due = todos
            .filter { !it.completed && dueState(it, nowMillis, zone).let { s -> s == DueState.OVERDUE || s == DueState.TODAY } }
            .sortedWith(openOrder(nowMillis, zone))
        return TodayOverview(current, next, due, todos.count { !it.completed })
    }

    private fun openOrder(nowMillis: Long, zone: ZoneId): Comparator<TodoItem> = compareBy<TodoItem> {
        when (dueState(it, nowMillis, zone)) {
            DueState.OVERDUE -> 0
            DueState.TODAY -> 1
            else -> 2
        }
    }
        .thenByDescending { it.priority.ordinal }
        .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
        .thenBy { it.createdAt }
}
