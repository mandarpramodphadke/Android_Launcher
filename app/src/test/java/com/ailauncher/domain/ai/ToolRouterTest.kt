package com.ailauncher.domain.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

class ToolRouterTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, zone) // Monday
    private val router = ToolRouter { now }

    private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
    private fun route(name: String, json: String) = router.route(RawToolCall(name, args(json)))
    private fun valid(name: String, json: String) = (route(name, json) as ToolRouting.Valid).command

    @Test
    fun alarmWithIntegers() {
        assertThat(valid("set_alarm", """{"hour": 6, "minute": 45, "label": "Run"}"""))
            .isEqualTo(ToolCommand.SetAlarm(6, 45, "Run", emptyList()))
    }

    @Test
    fun alarmWithStringsTimeAndDayNames() {
        val command = valid("set_alarm", """{"time": "7:30 pm", "days": ["monday", "Fri", 1]}""") as ToolCommand.SetAlarm
        assertThat(command.hour).isEqualTo(19)
        assertThat(command.minute).isEqualTo(30)
        assertThat(command.days).containsExactly(Calendar.SUNDAY, Calendar.MONDAY, Calendar.FRIDAY).inOrder()
        assertThat(valid("set_alarm", """{"hour": "6", "minute": "5"}""")).isEqualTo(ToolCommand.SetAlarm(6, 5, null, emptyList()))
    }

    @Test
    fun inventedRepeatDaysAreDroppedUnlessTheUserAskedForRepetition() {
        val call = RawToolCall("set_alarm", args("""{"hour": 6, "minute": 45, "days": [1]}"""))
        // Verbatim model output for "Wake me up at 6:45 tomorrow".
        assertThat((router.route(call, "Wake me up at 6:45 tomorrow") as ToolRouting.Valid).command)
            .isEqualTo(ToolCommand.SetAlarm(6, 45, null, emptyList()))
        assertThat(((router.route(call, "wake me at 6:45 every Sunday") as ToolRouting.Valid).command as ToolCommand.SetAlarm).days)
            .containsExactly(Calendar.SUNDAY)
        assertThat(((router.route(call, "alarm 6:45 on weekdays") as ToolRouting.Valid).command as ToolCommand.SetAlarm).days)
            .isNotEmpty()
    }

    @Test
    fun alarmOutOfRangeIsRejected() {
        assertThat(route("set_alarm", """{"hour": 25, "minute": 0}""")).isInstanceOf(ToolRouting.Invalid::class.java)
        assertThat(route("set_alarm", """{"minute": 10}""")).isInstanceOf(ToolRouting.Invalid::class.java)
    }

    @Test
    fun timerDurations() {
        assertThat(valid("set_timer", """{"seconds": 300}""")).isEqualTo(ToolCommand.SetTimer(300, null))
        assertThat(valid("set_timer", """{"minutes": 2}""")).isEqualTo(ToolCommand.SetTimer(120, null))
        assertThat(valid("set_timer", """{"duration": "1h 30m"}""")).isEqualTo(ToolCommand.SetTimer(5400, null))
        assertThat(valid("set_timer", """{"duration": "00:10:00"}""")).isEqualTo(ToolCommand.SetTimer(600, null))
        assertThat(route("set_timer", """{"seconds": 0}""")).isInstanceOf(ToolRouting.Invalid::class.java)
    }

    @Test
    fun reminderFromIsoDateTime() {
        val command = valid("create_reminder", """{"title": "Call mom", "datetime": "2026-11-24T09:00"}""") as ToolCommand.CreateReminder
        assertThat(command.timestampMillis).isEqualTo(ZonedDateTime.of(2026, 11, 24, 9, 0, 0, 0, zone).toInstant().toEpochMilli())
    }

    @Test
    fun reminderRelativeAndTimeOnly() {
        val relative = valid("create_reminder", """{"title": "Tea", "in_minutes": 30}""") as ToolCommand.CreateReminder
        assertThat(relative.timestampMillis).isEqualTo(now.plusMinutes(30).toInstant().toEpochMilli())
        // 9:00 has passed today, so "9am" means tomorrow.
        val timeOnly = valid("create_reminder", """{"title": "Gym", "time": "9am"}""") as ToolCommand.CreateReminder
        assertThat(timeOnly.timestampMillis).isEqualTo(now.plusDays(1).withHour(9).withMinute(0).toInstant().toEpochMilli())
    }

    @Test
    fun absoluteDatetimeWinsOverZeroRelativeOffset() {
        // Verbatim model output for "remind me to call mom tomorrow evening at 7".
        val command = valid(
            "create_reminder",
            """{"title": "call mom", "datetime": "2026-11-24T19:00", "in_minutes": 0}""",
        ) as ToolCommand.CreateReminder
        assertThat(command.timestampMillis).isEqualTo(ZonedDateTime.of(2026, 11, 24, 19, 0, 0, 0, zone).toInstant().toEpochMilli())
        assertThat(route("create_reminder", """{"title": "x", "in_minutes": 0}""")).isInstanceOf(ToolRouting.Invalid::class.java)
    }

    @Test
    fun reminderAcceptsEpochSecondsAndMillis() {
        val millis = now.plusHours(2).toInstant().toEpochMilli()
        assertThat((valid("create_reminder", """{"title": "x", "timestampMillis": $millis}""") as ToolCommand.CreateReminder).timestampMillis)
            .isEqualTo(millis)
        assertThat((valid("create_reminder", """{"title": "x", "timestampMillis": ${millis / 1000}}""") as ToolCommand.CreateReminder).timestampMillis)
            .isEqualTo(millis / 1000 * 1000)
    }

    @Test
    fun reminderInThePastIsRejected() {
        val result = route("create_reminder", """{"title": "Late", "datetime": "2026-11-22T09:00"}""")
        assertThat(result).isInstanceOf(ToolRouting.Invalid::class.java)
    }

    @Test
    fun launchAndToggleAndAliases() {
        assertThat(valid("launch_app", """{"appName": "Spotify"}""")).isEqualTo(ToolCommand.LaunchApp("Spotify"))
        assertThat(valid("open_app", """{"app": "Maps"}""")).isEqualTo(ToolCommand.LaunchApp("Maps"))
        assertThat(valid("toggle_setting", """{"setting": "torch", "state": "on"}"""))
            .isEqualTo(ToolCommand.ToggleSetting(DeviceSetting.FLASHLIGHT, ToggleState.ON))
        assertThat(valid("toggle_setting", """{"setting": "wi-fi"}"""))
            .isEqualTo(ToolCommand.ToggleSetting(DeviceSetting.WIFI, ToggleState.TOGGLE))
        assertThat(route("toggle_setting", """{"setting": "airplane"}""")).isInstanceOf(ToolRouting.Invalid::class.java)
        assertThat(route("make_coffee", "{}")).isInstanceOf(ToolRouting.Invalid::class.java)
    }

    @Test
    fun canonicalArgumentsRoundTrip() {
        val commands = listOf(
            ToolCommand.SetAlarm(7, 0, "Wake up", listOf(2, 3)),
            ToolCommand.SetTimer(90, "Eggs"),
            ToolCommand.CreateReminder("Pay rent", now.plusDays(3).toInstant().toEpochMilli()),
            ToolCommand.LaunchApp("Camera"),
            ToolCommand.ToggleSetting(DeviceSetting.DND, ToggleState.OFF),
            ToolCommand.SortApps,
            ToolCommand.GetTodayAgenda,
        )
        for (command in commands) {
            val routed = router.route(RawToolCall(command.toolName, router.canonicalArguments(command)))
            assertThat((routed as ToolRouting.Valid).command).isEqualTo(command)
        }
    }
}
