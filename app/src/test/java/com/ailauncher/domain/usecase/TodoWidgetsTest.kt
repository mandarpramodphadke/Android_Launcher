package com.ailauncher.domain.usecase

import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TodoWidgetsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 25)
    private val now = LocalDateTime.of(today, java.time.LocalTime.of(10, 30)).atZone(zone).toInstant().toEpochMilli()
    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun todo(
        id: Long,
        title: String = "t$id",
        list: String = TodoItem.DEFAULT_LIST,
        priority: TodoPriority = TodoPriority.MEDIUM,
        due: Long? = null,
        done: Boolean = false,
        doneAt: Long? = null,
    ) = TodoItem(id, title, null, list, priority, due, done, createdAt = id, completedAt = doneAt)

    @Test
    fun dueStatesAreRelativeToNow() {
        assertThat(TodoWidgets.dueState(todo(1, due = at(today.minusDays(1), 18)), now, zone)).isEqualTo(DueState.OVERDUE)
        assertThat(TodoWidgets.dueState(todo(2, due = at(today, 9)), now, zone)).isEqualTo(DueState.OVERDUE)
        assertThat(TodoWidgets.dueState(todo(3, due = at(today, 18)), now, zone)).isEqualTo(DueState.TODAY)
        assertThat(TodoWidgets.dueState(todo(4, due = at(today.plusDays(1), 9)), now, zone)).isEqualTo(DueState.TOMORROW)
        assertThat(TodoWidgets.dueState(todo(5, due = at(today.plusDays(5), 9)), now, zone)).isEqualTo(DueState.LATER)
        assertThat(TodoWidgets.dueState(todo(6), now, zone)).isNull()
    }

    @Test
    fun widgetShowsOverdueThenTodayThenByPriorityThenRecentlyTickedOff() {
        val todos = listOf(
            todo(1, "later low", priority = TodoPriority.LOW),
            todo(2, "today", due = at(today, 18)),
            todo(3, "high", priority = TodoPriority.HIGH),
            todo(4, "overdue", due = at(today.minusDays(2), 9)),
            todo(5, "just done", done = true, doneAt = now - 60_000),
            todo(6, "done yesterday", done = true, doneAt = now - 86_400_000),
            todo(7, "work thing", list = "Work"),
        )
        val rows = TodoWidgets.rows(todos, null, now, zone).map { it.title }
        assertThat(rows).containsExactly("overdue", "today", "high", "work thing", "later low", "just done").inOrder()
        assertThat(TodoWidgets.rows(todos, "work", now, zone).map { it.title }).containsExactly("work thing")
        assertThat(TodoWidgets.openCount(todos, null)).isEqualTo(5)
        assertThat(TodoWidgets.openCount(todos, "Work")).isEqualTo(1)
    }

    @Test
    fun listsPutTheDefaultListFirst() {
        val todos = listOf(todo(1, list = "Shopping"), todo(2, list = "errands"), todo(3, list = "inbox"), todo(4, list = "Shopping"))
        assertThat(TodoWidgets.lists(todos)).containsExactly(TodoItem.DEFAULT_LIST, "errands", "Shopping").inOrder()
        assertThat(TodoWidgets.lists(emptyList())).containsExactly(TodoItem.DEFAULT_LIST)
    }

    @Test
    fun todayShowsTheBlockInProgressTheNextOneAndWhatIsDue() {
        fun block(id: Long, start: Int, minutes: Int, title: String, done: Boolean = false) =
            PlannerBlock(id, today, start, minutes, title, null, done)
        val blocks = listOf(
            block(1, 9 * 60, 60, "Email"),
            block(2, 10 * 60, 90, "Deep work"),
            block(3, 13 * 60, 30, "Lunch walk", done = true),
            block(4, 14 * 60, 60, "Gym"),
        )
        val todos = listOf(
            todo(1, "pay rent", due = at(today, 20)),
            todo(2, "call bank", due = at(today.minusDays(1), 12)),
            todo(3, "next week", due = at(today.plusDays(7), 9)),
            todo(4, "no date"),
        )
        val overview = TodoWidgets.today(todos, blocks, now, zone)
        assertThat(overview.current?.title).isEqualTo("Deep work")
        assertThat(overview.next?.title).isEqualTo("Gym")
        assertThat(overview.due.map { it.title }).containsExactly("call bank", "pay rent").inOrder()
        assertThat(overview.openTodos).isEqualTo(4)

        val evening = at(today, 21)
        val later = TodoWidgets.today(todos, blocks, evening, zone)
        assertThat(later.current).isNull()
        assertThat(later.next).isNull()
    }
}
