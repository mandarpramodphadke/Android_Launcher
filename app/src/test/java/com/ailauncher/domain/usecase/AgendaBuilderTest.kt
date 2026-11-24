package com.ailauncher.domain.usecase

import com.ailauncher.domain.model.AgendaEvent
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.Reminder
import com.ailauncher.domain.model.ReminderStatus
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.ai.PromptJson
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AgendaBuilderTest {

    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 24)
    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun todo(id: Long, title: String, priority: TodoPriority, due: Long? = null, done: Boolean = false) =
        TodoItem(id, title, null, "Inbox", priority, due, done, createdAt = id, completedAt = null)

    @Test
    fun combinesCalendarRemindersPlannerAndUrgentTodos() {
        val output = AgendaBuilder.build(
            AgendaBuilder.Input(
                date = day,
                zone = zone,
                events = listOf(AgendaEvent("Standup", at(9, 30), at(9, 45), false, null)),
                calendarAccess = true,
                reminders = listOf(Reminder(1, "Call mom", at(19), null, ReminderStatus.SCHEDULED, 0)),
                nextAlarmMillis = at(6, 45) + 86_400_000,
                blocks = listOf(PlannerBlock(1, day, 14 * 60, 90, "Deep work", null, false), PlannerBlock(2, day.plusDays(1), 600, 30, "Other day", null, false)),
                pendingTodos = listOf(
                    todo(1, "Pay rent", TodoPriority.HIGH),
                    todo(2, "Buy milk", TodoPriority.LOW),
                    todo(3, "Send report", TodoPriority.MEDIUM, due = at(17)),
                    todo(4, "Done thing", TodoPriority.HIGH, done = true),
                ),
            ),
        )
        val json = PromptJson.encode(output.data)
        assertThat(json).contains("\"title\": \"Standup\", \"start\": \"09:30\", \"end\": \"09:45\"")
        assertThat(json).contains("\"plan\": [{\"time\": \"14:00-15:30\", \"title\": \"Deep work\"}]")
        assertThat(json).doesNotContain("Other day")
        assertThat(json).contains("Send report")
        assertThat(json).contains("Pay rent")
        assertThat(json).doesNotContain("Buy milk")
        assertThat(json).doesNotContain("Done thing")
        assertThat(output.summary).isEqualTo("1 event · 1 reminder · 1 planned block · 2 urgent to-dos · next alarm 06:45")
    }

    @Test
    fun overdueTodosAreFlagged() {
        val overdue = todo(1, "Renew passport", TodoPriority.MEDIUM, due = at(10) - 3 * 86_400_000)
        assertThat(AgendaBuilder.focusTodos(listOf(overdue), day, zone)).hasSize(1)
        val output = AgendaBuilder.build(AgendaBuilder.Input(day, zone, emptyList(), false, emptyList(), null, emptyList(), listOf(overdue)))
        assertThat(PromptJson.encode(output.data)).contains("\"due\": \"overdue\"")
        assertThat(output.summary).endsWith("(calendar access not granted)")
    }
}
