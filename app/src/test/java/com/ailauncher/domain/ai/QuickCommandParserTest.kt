package com.ailauncher.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickCommandParserTest {

    private val now = ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, ZoneId.of("UTC"))
    private val parser = QuickCommandParser { now }

    @Test
    fun openApps() {
        assertThat(parser.parse("open Spotify")).isEqualTo(ToolCommand.LaunchApp("spotify"))
        assertThat(parser.parse("Launch the camera app")).isEqualTo(ToolCommand.LaunchApp("camera"))
        assertThat(parser.parse("please open google maps!")).isEqualTo(ToolCommand.LaunchApp("google maps"))
    }

    @Test
    fun timers() {
        assertThat(parser.parse("timer 10 min")).isEqualTo(ToolCommand.SetTimer(600, null))
        assertThat(parser.parse("set a timer for 30 seconds")).isEqualTo(ToolCommand.SetTimer(30, null))
        assertThat(parser.parse("5 minute timer")).isEqualTo(ToolCommand.SetTimer(300, null))
        assertThat(parser.parse("timer for 1.5 hours")).isEqualTo(ToolCommand.SetTimer(5400, null))
    }

    @Test
    fun alarms() {
        assertThat(parser.parse("Set alarm for 7 AM")).isEqualTo(ToolCommand.SetAlarm(7, 0, null, emptyList()))
        assertThat(parser.parse("wake me up at 6:45")).isEqualTo(ToolCommand.SetAlarm(6, 45, null, emptyList()))
        assertThat(parser.parse("alarm at 12 am")).isEqualTo(ToolCommand.SetAlarm(0, 0, null, emptyList()))
        assertThat(parser.parse("set an alarm for 9:30 p.m.")).isEqualTo(ToolCommand.SetAlarm(21, 30, null, emptyList()))
    }

    @Test
    fun toggles() {
        assertThat(parser.parse("Turn on flashlight")).isEqualTo(ToolCommand.ToggleSetting(DeviceSetting.FLASHLIGHT, ToggleState.ON))
        assertThat(parser.parse("turn the torch off")).isEqualTo(ToolCommand.ToggleSetting(DeviceSetting.FLASHLIGHT, ToggleState.OFF))
        assertThat(parser.parse("wifi off")).isEqualTo(ToolCommand.ToggleSetting(DeviceSetting.WIFI, ToggleState.OFF))
        assertThat(parser.parse("toggle bluetooth")).isEqualTo(ToolCommand.ToggleSetting(DeviceSetting.BLUETOOTH, ToggleState.TOGGLE))
    }

    @Test
    fun organizeAndAgendaAndRelativeReminder() {
        assertThat(parser.parse("Organize my apps")).isEqualTo(ToolCommand.SortApps)
        assertThat(parser.parse("Summarize my day")).isEqualTo(ToolCommand.GetTodayAgenda)
        assertThat(parser.parse("what's on my schedule today?")).isEqualTo(ToolCommand.GetTodayAgenda)
        val reminder = parser.parse("remind me in 20 minutes to check the oven") as ToolCommand.CreateReminder
        assertThat(reminder.title).isEqualTo("Check the oven")
        assertThat(reminder.timestampMillis).isEqualTo(now.plusMinutes(20).toInstant().toEpochMilli())
    }

    @Test
    fun everythingElseGoesToTheModel() {
        assertThat(parser.parse("What's the capital of France?")).isNull()
        assertThat(parser.parse("remind me tomorrow to call mom")).isNull()
        assertThat(parser.parse("open a timer")).isNull()
        assertThat(parser.parse("")).isNull()
    }
}
