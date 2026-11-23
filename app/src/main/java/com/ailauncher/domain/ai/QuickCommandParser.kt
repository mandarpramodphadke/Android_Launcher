package com.ailauncher.domain.ai

import java.time.ZonedDateTime
import java.util.Locale

/**
 * Deterministic fast path for the most common launcher commands ("open spotify", "timer 5 min",
 * "alarm at 7am", "flashlight on"). They execute instantly, work before the model is downloaded
 * and never wake the LLM; anything else falls through to Qwen3.
 */
class QuickCommandParser(private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {

    fun parse(input: String): ToolCommand? {
        val text = input.trim().lowercase(Locale.ROOT)
            .replace("a.m.", "am").replace("p.m.", "pm")
            .removeSuffix(".").removeSuffix("!").removeSuffix("please").trim()
            .removePrefix("please ").removePrefix("hey ").trim()
        if (text.isEmpty() || text.length > 80) return null

        ORGANIZE.matchEntire(text)?.let { return ToolCommand.SortApps }
        AGENDA.matchEntire(text)?.let { return ToolCommand.GetTodayAgenda }

        TOGGLE.matchEntire(text)?.let { m ->
            val setting = DeviceSetting.fromWire(m.groupValues[3]) ?: return@let
            val state = m.groupValues[1].ifEmpty { m.groupValues[4] }
            return ToolCommand.ToggleSetting(setting, ToggleState.fromWire(state))
        }
        TOGGLE_SUFFIX.matchEntire(text)?.let { m ->
            val setting = DeviceSetting.fromWire(m.groupValues[1]) ?: return@let
            return ToolCommand.ToggleSetting(setting, ToggleState.fromWire(m.groupValues[2]))
        }

        TIMER.matchEntire(text)?.let { m ->
            val seconds = toSeconds(m.groupValues[1].toDouble(), m.groupValues[2]) ?: return@let
            if (seconds in 1..86_400) return ToolCommand.SetTimer(seconds, null)
        }
        TIMER_SUFFIX.matchEntire(text)?.let { m ->
            val seconds = toSeconds(m.groupValues[1].toDouble(), m.groupValues[2]) ?: return@let
            if (seconds in 1..86_400) return ToolCommand.SetTimer(seconds, null)
        }

        ALARM.matchEntire(text)?.let { m ->
            val time = clock(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return@let
            return ToolCommand.SetAlarm(time.first, time.second, null, emptyList())
        }

        REMIND_IN.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].toDouble()
            val seconds = toSeconds(amount, m.groupValues[2]) ?: return@let
            val title = m.groupValues[4].trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }
            val at = now().plusSeconds(seconds.toLong()).toInstant().toEpochMilli()
            return ToolCommand.CreateReminder(title, at)
        }

        OPEN.matchEntire(text)?.let { m ->
            val name = m.groupValues[2].removePrefix("the ").removeSuffix(" app").trim()
            if (name.isNotEmpty() && name !in NOT_APPS) return ToolCommand.LaunchApp(name)
        }
        return null
    }

    private fun toSeconds(amount: Double, unit: String): Int? = when {
        unit.startsWith("h") -> (amount * 3600).toInt()
        unit.startsWith("m") -> (amount * 60).toInt()
        unit.startsWith("s") -> amount.toInt()
        else -> null
    }

    private fun clock(hourText: String, minuteText: String, meridiem: String): Pair<Int, Int>? {
        var hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.ifEmpty { "0" }.toIntOrNull() ?: return null
        when (meridiem.replace(".", "")) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    private companion object {
        val ORGANIZE = Regex("""^(organi[sz]e|sort|categori[sz]e|tidy( up)?|clean up)\s+(all\s+)?(my\s+)?(apps|app drawer|home screen)$""")
        val AGENDA = Regex("""^(summari[sz]e my day|what'?s (on )?my (day|schedule|agenda)( today)?|my (day|schedule|agenda)( today)?|what do i have today)\??$""")
        val TOGGLE = Regex("""^(?:turn|switch|toggle)\s+(on|off)?\s*(the\s+)?(flashlight|torch|wi-?fi|bluetooth|dnd|do not disturb)\s*(on|off)?$""")
        val TOGGLE_SUFFIX = Regex("""^(flashlight|torch|wi-?fi|bluetooth|dnd|do not disturb)\s+(on|off)$""")
        val TIMER = Regex("""^(?:set\s+)?(?:a\s+)?timer\s+(?:for\s+)?(\d+(?:\.\d+)?)\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)$""")
        val TIMER_SUFFIX = Regex("""^(?:set\s+)?(?:a\s+)?(\d+(?:\.\d+)?)\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\s+timer$""")
        val ALARM = Regex("""^(?:set\s+)?(?:an?\s+)?(?:alarm|wake me up|wake me)\s+(?:for|at)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?$""")
        val REMIND_IN = Regex("""^remind me in\s+(\d+(?:\.\d+)?)\s*(minutes?|mins?|m|hours?|hrs?|h|seconds?|secs?|s)\s+(to\s+|about\s+)?(.+)$""")
        val OPEN = Regex("""^(open|launch|start|run)\s+(.+)$""")
        val NOT_APPS = setOf("a timer", "an alarm", "the timer", "the alarm", "settings for")
    }
}
