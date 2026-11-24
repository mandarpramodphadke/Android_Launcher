package com.ailauncher.domain.ai

import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Routing of the v2 launcher tools: lenient argument parsing, strict validation, stable canonical form. */
class LauncherToolRoutingTest {

    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, zone) // Tuesday
    private val today = now.toLocalDate()
    private val router = ToolRouter { now }

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
    private fun route(name: String, json: String) = router.route(RawToolCall(name, args(json)))
    private fun valid(name: String, json: String) = (route(name, json) as ToolRouting.Valid).command
    private fun invalid(name: String, json: String) = (route(name, json) as ToolRouting.Invalid).reason

    /** Every command must survive a round trip through its canonical arguments (confirm-later safety). */
    private fun assertRoundTrip(command: ToolCommand) {
        val again = router.route(RawToolCall(command.toolName, router.canonicalArguments(command))) as ToolRouting.Valid
        assertThat(again.command).isEqualTo(command)
    }

    @Test
    fun todos() {
        val add = valid("manage_todo", """{"action": "add", "title": "Pick up dry cleaning", "listName": "errands list", "priority": "High", "dueIso": "2026-11-25T17:00"}""")
        assertThat(add).isEqualTo(
            ToolCommand.ManageTodo(TodoAction.ADD, "Pick up dry cleaning", "Errands", TodoPriority.HIGH, now.withDayOfMonth(25).withHour(17).toInstant().toEpochMilli()),
        )
        assertRoundTrip(add)
        // Defaults, aliases and natural-language due dates.
        val quick = valid("add_todo", """{"task": "call the bank", "due": "tomorrow"}""") as ToolCommand.ManageTodo
        assertThat(quick.listName).isEqualTo(TodoItem.DEFAULT_LIST)
        assertThat(quick.priority).isEqualTo(TodoPriority.MEDIUM)
        assertThat(quick.dueAtMillis).isEqualTo(now.plusDays(1).withHour(9).toInstant().toEpochMilli())
        assertThat(valid("manage_todo", """{"action": "list", "listName": "shopping"}""")).isEqualTo(ToolCommand.ManageTodo(TodoAction.LIST, listName = "Shopping"))
        assertThat(valid("manage_todo", """{"action": "done", "title": "milk"}""")).isEqualTo(ToolCommand.ManageTodo(TodoAction.COMPLETE, title = "milk"))
        assertThat(invalid("manage_todo", """{"action": "add"}""")).isEqualTo("What should the task say?")
        assertThat(invalid("manage_todo", """{"action": "delete"}""")).isEqualTo("Which task?")
        assertThat(invalid("manage_todo", """{"action": "add", "title": "x", "dueIso": "someday maybe"}""")).contains("due date")
    }

    @Test
    fun planner() {
        val block = valid("manage_planner", """{"action": "schedule_block", "dateIso": "tomorrow", "startTime": "3pm", "durationMinutes": 120, "title": "Deep work"}""")
        assertThat(block).isEqualTo(ToolCommand.ManagePlanner(PlannerAction.SCHEDULE_BLOCK, today.plusDays(1), 15 * 60, 120, "Deep work"))
        assertRoundTrip(block)
        // An ISO start time carries its own date; end time gives the duration.
        assertThat(valid("manage_planner", """{"action": "schedule", "startTime": "2026-11-27T09:30", "endTime": "10:15", "title": "Dentist"}"""))
            .isEqualTo(ToolCommand.ManagePlanner(PlannerAction.SCHEDULE_BLOCK, LocalDate.of(2026, 11, 27), 9 * 60 + 30, 45, "Dentist"))
        assertThat(valid("manage_planner", """{"action": "get_day_plan"}""")).isEqualTo(ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, today))
        assertThat(valid("manage_planner", """{"action": "reschedule", "title": "gym", "startTime": "18:00"}"""))
            .isEqualTo(ToolCommand.ManagePlanner(PlannerAction.RESCHEDULE, null, 18 * 60, null, "gym", null))
        assertThat(invalid("manage_planner", """{"action": "schedule_block", "title": "Gym"}""")).isEqualTo("What time should it start?")
        assertThat(invalid("manage_planner", """{"action": "schedule_block", "title": "Late", "startTime": "23:30", "durationMinutes": 60}"""))
            .isEqualTo("The block has to end by midnight")
        assertThat(invalid("manage_planner", """{"action": "reschedule", "title": "gym"}""")).isEqualTo("When should it move to?")
    }

    @Test
    fun storageInsights() {
        assertThat(valid("list_unused_apps", """{"inactiveDays": 60}""")).isEqualTo(ToolCommand.ListUnusedApps(60))
        assertThat(valid("list_unused_apps", "{}")).isEqualTo(ToolCommand.ListUnusedApps(null))
        assertThat(valid("find_largest_files", """{"limit": "10", "fileType": "videos"}""")).isEqualTo(ToolCommand.FindLargestFiles(10, FileTypeFilter.VIDEO))
        assertThat(valid("find_largest_files", """{"limit": 500}""")).isEqualTo(ToolCommand.FindLargestFiles(50, FileTypeFilter.ALL))

        val downloads = valid("query_files_by_activity", """{"filterMode": "downloaded", "startDateIso": "2026-11-16", "endDateIso": "2026-11-22", "fileType": "pdf"}""")
        assertThat(downloads).isEqualTo(
            ToolCommand.QueryFilesByActivity(FileActivityMode.DOWNLOADED, null, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), FileTypeFilter.DOCUMENT),
        )
        assertRoundTrip(downloads)
        // Range phrases, reversed bounds, future ends and missing bounds are all normalized.
        assertThat(valid("query_files_by_activity", """{"filterMode": "modified_range", "range": "last week"}"""))
            .isEqualTo(ToolCommand.QueryFilesByActivity(FileActivityMode.MODIFIED_RANGE, null, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), FileTypeFilter.ALL))
        assertThat(valid("query_files_by_activity", """{"filterMode": "downloaded", "startDateIso": "2026-12-05", "endDateIso": "2026-11-20"}""") as ToolCommand.QueryFilesByActivity)
            .isEqualTo(ToolCommand.QueryFilesByActivity(FileActivityMode.DOWNLOADED, null, LocalDate.of(2026, 11, 20), today, FileTypeFilter.ALL))
        assertThat(valid("query_files_by_activity", """{"filterMode": "opened"}"""))
            .isEqualTo(ToolCommand.QueryFilesByActivity(FileActivityMode.OPENED_OR_CLICKED, null, today.minusDays(6), today, FileTypeFilter.ALL))
        assertThat(valid("query_files_by_activity", """{"unusedDays": 180, "fileType": "image"}"""))
            .isEqualTo(ToolCommand.QueryFilesByActivity(FileActivityMode.UNUSED_SINCE, 180, null, null, FileTypeFilter.IMAGE))
        assertThat(invalid("query_files_by_activity", "{}")).contains("unused, downloaded, opened or modified")
    }

    @Test
    fun notificationsAndContacts() {
        assertThat(valid("summarize_notifications", "{}")).isEqualTo(ToolCommand.SummarizeNotifications(null))
        assertThat(valid("summarize_notifications", """{"appName": "WhatsApp"}""")).isEqualTo(ToolCommand.SummarizeNotifications("WhatsApp"))
        val text = valid("contact_action", """{"action": "whatsapp", "contactName": "Mom", "message": "I'll be 10 minutes late"}""")
        assertThat(text).isEqualTo(ToolCommand.ContactAction(ContactActionType.WHATSAPP, "Mom", "I'll be 10 minutes late"))
        assertRoundTrip(text)
        // A call never carries a message; tool-name aliases imply the action.
        assertThat(valid("call", """{"name": "Alex Johnson", "message": "hi"}""")).isEqualTo(ToolCommand.ContactAction(ContactActionType.CALL, "Alex Johnson", null))
        assertThat(valid("send_sms", """{"to": "Sam", "text": "on my way"}""")).isEqualTo(ToolCommand.ContactAction(ContactActionType.SMS, "Sam", "on my way"))
        assertThat(invalid("contact_action", """{"action": "call"}""")).isEqualTo("Who should I contact?")
    }

    @Test
    fun summariesReadNaturally() {
        assertThat((route("manage_planner", """{"action": "schedule_block", "startTime": "14:00", "title": "Gym"}""") as ToolRouting.Valid).summary)
            .contains("Today")
        assertThat((route("contact_action", """{"action": "sms", "contactName": "Mom", "message": "Running late"}""") as ToolRouting.Valid).summary)
            .isEqualTo("Text Mom · “Running late”")
        assertThat((route("find_largest_files", """{"fileType": "video"}""") as ToolRouting.Valid).summary).isEqualTo("Find the 15 largest videos")
    }

    /**
     * Verbatim Qwen3-1.7B (Q4_K_M) outputs from replaying app/build/prompt-fixtures/tool_*.txt with
     * llm_host_harness (fixture time: Monday 23 November 2026, 10:15). The model picked the right
     * tool every time but fumbled dates, numbers and list names; the user's own words win.
     */
    @Test
    fun verbatimModelOutputsAreGroundedInTheUsersWords() {
        val monday = ToolRouter { ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, zone) }
        fun grounded(name: String, json: String, userText: String) =
            (monday.route(RawToolCall(name, args(json)), userText) as ToolRouting.Valid).command

        assertThat(
            grounded(
                "manage_todo",
                """{"action": "add", "title": "Dry cleaning", "listName": "Shopping", "priority": "high", "in_minutes": 30}""",
                "I need to pick up the dry cleaning tomorrow at 5pm, put it on my errands list as high priority",
            ),
        ).isEqualTo(
            ToolCommand.ManageTodo(
                TodoAction.ADD, "Dry cleaning", "Errands", TodoPriority.HIGH,
                ZonedDateTime.of(2026, 11, 24, 17, 0, 0, 0, zone).toInstant().toEpochMilli(),
            ),
        )
        assertThat(
            grounded(
                "manage_planner",
                """{"action": "schedule_block", "dateIso": "2026-11-23T12:00", "startTime": "15:00", "durationMinutes": 120, "title": "Deep Work"}""",
                "block 2 hours tomorrow at 3pm for deep work",
            ),
        ).isEqualTo(ToolCommand.ManagePlanner(PlannerAction.SCHEDULE_BLOCK, LocalDate.of(2026, 11, 24), 15 * 60, 120, "Deep Work"))
        assertThat(grounded("list_unused_apps", """{"inactiveDays": 2}""", "which apps haven't I touched in about two months?"))
            .isEqualTo(ToolCommand.ListUnusedApps(60))
        assertThat(
            grounded(
                "query_files_by_activity",
                """{"filterMode": "modified_range", "startDateIso": "2026-11-07", "endDateIso": "2026-11-10", "fileType": "document"}""",
                "show me the documents I downloaded since last monday",
            ),
        ).isEqualTo(
            ToolCommand.QueryFilesByActivity(FileActivityMode.DOWNLOADED, null, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23), FileTypeFilter.DOCUMENT),
        )
        assertThat(grounded("get_today_agenda", "{}", "when am I free tomorrow?"))
            .isEqualTo(ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, LocalDate.of(2026, 11, 24)))
        assertThat(grounded("get_today_agenda", "{}", "summarize my day")).isEqualTo(ToolCommand.GetTodayAgenda)
        assertThat(grounded("summarize_notifications", """{"appName": "All"}""", "anything important in my notifications today?"))
            .isEqualTo(ToolCommand.SummarizeNotifications(null))
        // Correct outputs pass through unchanged.
        assertThat(grounded("query_files_by_activity", """{"filterMode": "unused_since", "unusedDays": 180, "fileType": "image"}""", "find photos I haven't touched in half a year"))
            .isEqualTo(ToolCommand.QueryFilesByActivity(FileActivityMode.UNUSED_SINCE, 180, null, null, FileTypeFilter.IMAGE))
        assertThat(grounded("contact_action", """{"action": "whatsapp", "contactName": "Mom", "message": "I'll be 10 minutes late"}""", "tell mom on whatsapp that I'll be 10 minutes late"))
            .isEqualTo(ToolCommand.ContactAction(ContactActionType.WHATSAPP, "Mom", "I'll be 10 minutes late"))
        // A time range is left to the model rather than guessed.
        assertThat(grounded("manage_planner", """{"action": "schedule_block", "startTime": "15:00", "durationMinutes": 120, "title": "Gym"}""", "gym from 3 to 5pm"))
            .isEqualTo(ToolCommand.ManagePlanner(PlannerAction.SCHEDULE_BLOCK, LocalDate.of(2026, 11, 23), 15 * 60, 120, "Gym"))
    }

    @Test
    fun readOnlyToolsAutoRunWhileWritesNeedConfirmation() {
        assertThat(ToolCommand.ManageTodo(TodoAction.LIST).isReadOnly).isTrue()
        assertThat(ToolCommand.ManageTodo(TodoAction.ADD, "x").isReadOnly).isFalse()
        assertThat(ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, today).isReadOnly).isTrue()
        assertThat(ToolCommand.ManagePlanner(PlannerAction.REMOVE_BLOCK, title = "x").isReadOnly).isFalse()
        assertThat(ToolCommand.ContactAction(ContactActionType.CALL, "Mom", null).isReadOnly).isFalse()
        assertThat(ToolCommand.FindLargestFiles(15, FileTypeFilter.ALL).isReadOnly).isTrue()
    }
}
