package com.ailauncher.domain.ai

import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.TodoPriority
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Instant, LLM-free commands for the v2 launcher tools. */
class LauncherQuickCommandsTest {

    private val now = ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, ZoneId.of("UTC")) // Tuesday
    private val parser = QuickCommandParser { now }

    @Test
    fun todos() {
        assertThat(parser.parse("add Oat milk to my shopping list"))
            .isEqualTo(ToolCommand.ManageTodo(TodoAction.ADD, "Oat milk", "Shopping", TodoPriority.MEDIUM))
        assertThat(parser.parse("add call the bank to my to-do list"))
            .isEqualTo(ToolCommand.ManageTodo(TodoAction.ADD, "Call the bank", "Inbox", TodoPriority.MEDIUM))
        assertThat(parser.parse("todo: Renew passport")).isEqualTo(ToolCommand.ManageTodo(TodoAction.ADD, "Renew passport", "Inbox", TodoPriority.MEDIUM))
        assertThat(parser.parse("todo list")).isEqualTo(ToolCommand.ManageTodo(TodoAction.LIST))
        assertThat(parser.parse("what's on my to-do list?")).isEqualTo(ToolCommand.ManageTodo(TodoAction.LIST))
        assertThat(parser.parse("show my shopping list")).isEqualTo(ToolCommand.ManageTodo(TodoAction.LIST, listName = "Shopping"))
        // Not lists, not to-dos.
        assertThat(parser.parse("add mom to my contact list")).isNull()
    }

    @Test
    fun plannerAndNotifications() {
        assertThat(parser.parse("show my plan for tomorrow"))
            .isEqualTo(ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, LocalDate.of(2026, 11, 25)))
        assertThat(parser.parse("my day plan")).isEqualTo(ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, LocalDate.of(2026, 11, 24)))
        assertThat(parser.parse("summarize my notifications")).isEqualTo(ToolCommand.SummarizeNotifications(null))
        assertThat(parser.parse("read notifications from slack")).isEqualTo(ToolCommand.SummarizeNotifications("slack"))
        assertThat(parser.parse("What did I miss?")).isEqualTo(ToolCommand.SummarizeNotifications(null))
    }

    @Test
    fun storage() {
        assertThat(parser.parse("unused apps")).isEqualTo(ToolCommand.ListUnusedApps(null))
        assertThat(parser.parse("apps I haven't used in 60 days")).isEqualTo(ToolCommand.ListUnusedApps(60))
        assertThat(parser.parse("apps not used for the last 90 days")).isEqualTo(ToolCommand.ListUnusedApps(90))
        assertThat(parser.parse("show me the 10 biggest videos")).isEqualTo(ToolCommand.FindLargestFiles(10, FileTypeFilter.VIDEO))
        assertThat(parser.parse("largest files")).isEqualTo(ToolCommand.FindLargestFiles(15, FileTypeFilter.ALL))
        assertThat(parser.parse("what's taking up space?")).isEqualTo(ToolCommand.FindLargestFiles(15, FileTypeFilter.ALL))
        assertThat(parser.parse("old photos not opened in 200 days"))
            .isEqualTo(ToolCommand.QueryFilesByActivity(FileActivityMode.UNUSED_SINCE, 200, null, null, FileTypeFilter.IMAGE))
        assertThat(parser.parse("pdfs I downloaded last week")).isEqualTo(
            ToolCommand.QueryFilesByActivity(FileActivityMode.DOWNLOADED, null, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), FileTypeFilter.DOCUMENT),
        )
        assertThat(parser.parse("what did i download yesterday")).isEqualTo(
            ToolCommand.QueryFilesByActivity(FileActivityMode.DOWNLOADED, null, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 23), FileTypeFilter.ALL),
        )
    }

    @Test
    fun contactsKeepTheMessageCase() {
        assertThat(parser.parse("call Mom")).isEqualTo(ToolCommand.ContactAction(ContactActionType.CALL, "Mom", null))
        assertThat(parser.parse("call my sister on WhatsApp")).isEqualTo(ToolCommand.ContactAction(ContactActionType.WHATSAPP, "sister", null))
        assertThat(parser.parse("text Alex: I'm running 10 minutes late, sorry!"))
            .isEqualTo(ToolCommand.ContactAction(ContactActionType.SMS, "Alex", "I'm running 10 minutes late, sorry"))
        assertThat(parser.parse("whatsapp Priya saying Happy birthday 🎉"))
            .isEqualTo(ToolCommand.ContactAction(ContactActionType.WHATSAPP, "Priya", "Happy birthday 🎉"))
        // Not contacts.
        assertThat(parser.parse("call me a taxi")).isNull()
        assertThat(parser.parse("call it a day")).isNull()
    }

    @Test
    fun foldersOpenThroughLaunchApp() {
        assertThat(parser.parse("open my Work folder")).isEqualTo(ToolCommand.LaunchApp("work folder"))
    }

    @Test
    fun longSentencesStillGoToTheModel() {
        // Over the short-command length, only clear messages and to-dos are quick commands.
        assertThat(parser.parse("open the document I sent you yesterday about the quarterly planning meeting and summarize it")).isNull()
        assertThat(parser.parse("text Jordan: the quarterly planning meeting moved to Thursday at three in the big conference room"))
            ?.isInstanceOf(ToolCommand.ContactAction::class.java)
    }
}
