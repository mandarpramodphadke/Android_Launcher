package com.ailauncher.domain.ai

import com.ailauncher.domain.ai.ModelOutputParser.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Locale

sealed interface ToolRouting {
    data class Valid(val command: ToolCommand, val summary: String) : ToolRouting
    data class Invalid(val toolName: String, val reason: String) : ToolRouting
}

/**
 * Validates the model's raw JSON arguments and maps them to typed [ToolCommand]s. Small models are
 * sloppy with types, so arguments are parsed leniently (numbers as strings, "6:45" times, ISO or
 * relative reminder times, weekday names) but validated strictly (ranges, past timestamps).
 */
class ToolRouter(private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {

    /**
     * @param userText the user's request, when known. Small models often invent repeat days for
     * one-off alarms ("tomorrow at 6:45"), so days are kept only if the user asked for repetition.
     */
    fun route(call: RawToolCall, userText: String? = null): ToolRouting = try {
        when (call.name.trim().lowercase(Locale.ROOT)) {
            ToolNames.SET_ALARM, "alarm", "create_alarm" -> setAlarm(call.arguments, userText)
            ToolNames.SET_TIMER, "timer", "start_timer" -> setTimer(call.arguments)
            ToolNames.CREATE_REMINDER, "reminder", "set_reminder", "schedule_reminder" -> createReminder(call.arguments)
            ToolNames.LAUNCH_APP, "open_app", "start_app" -> launchApp(call.arguments)
            ToolNames.TOGGLE_SETTING, "toggle", "device_setting" -> toggleSetting(call.arguments)
            ToolNames.SORT_APPS, "organize_apps", "categorize_apps" ->
                ToolRouting.Valid(ToolCommand.SortApps, summarize(ToolCommand.SortApps))
            ToolNames.GET_TODAY_AGENDA, "get_agenda", "today_agenda" ->
                ToolRouting.Valid(ToolCommand.GetTodayAgenda, summarize(ToolCommand.GetTodayAgenda))
            else -> ToolRouting.Invalid(call.name, "Unknown tool \"${call.name}\"")
        }
    } catch (e: IllegalArgumentException) {
        ToolRouting.Invalid(call.name, e.message ?: "Invalid arguments")
    }

    // ---------------------------------------------------------------------------------------- //

    private fun setAlarm(args: JsonObject, userText: String?): ToolRouting {
        var hour = args.int("hour")
        var minute = args.int("minute") ?: args.int("minutes")
        if (hour == null) {
            parseClockTime(args.string("time"))?.let { hour = it.hour; minute = it.minute }
        }
        val h = hour ?: throw IllegalArgumentException("The alarm needs an hour")
        val m = minute ?: 0
        require(h in 0..23) { "Hour must be between 0 and 23" }
        require(m in 0..59) { "Minute must be between 0 and 59" }
        val requestedDays = parseDays(args["days"])
        val days = if (userText != null && !REPEAT_HINT.containsMatchIn(userText.lowercase(Locale.ROOT))) emptyList() else requestedDays
        val label = args.string("label") ?: args.string("message")
        val command = ToolCommand.SetAlarm(hour = h, minute = m, label = label, days = days)
        return ToolRouting.Valid(command, alarmSummary(command))
    }

    private fun setTimer(args: JsonObject): ToolRouting {
        val seconds = args.long("seconds")
            ?: args.long("duration_seconds")
            ?: args.long("minutes")?.times(60)
            ?: args.long("hours")?.times(3600)
            ?: parseDuration(args.string("duration"))
            ?: throw IllegalArgumentException("The timer needs a duration")
        require(seconds in 1..(24 * 3600)) { "Timers must be between 1 second and 24 hours" }
        val command = ToolCommand.SetTimer(seconds.toInt(), args.string("label") ?: args.string("message"))
        return ToolRouting.Valid(command, timerSummary(command))
    }

    private fun createReminder(args: JsonObject): ToolRouting {
        val title = args.string("title") ?: args.string("text") ?: args.string("message")
            ?: throw IllegalArgumentException("The reminder needs a title")
        val current = now()
        val trigger = resolveReminderTime(args, current)
            ?: throw IllegalArgumentException("I couldn't work out when to remind you")
        require(trigger > current.toInstant().toEpochMilli() - 60_000) { "That time has already passed" }
        val command = ToolCommand.CreateReminder(title.take(200), trigger)
        return ToolRouting.Valid(command, reminderSummary(command, current))
    }

    private fun launchApp(args: JsonObject): ToolRouting {
        val name = args.string("appName") ?: args.string("app_name") ?: args.string("app")
            ?: args.string("name") ?: args.string("package")
            ?: throw IllegalArgumentException("Which app should I open?")
        return ToolRouting.Valid(ToolCommand.LaunchApp(name), "Open $name")
    }

    private fun toggleSetting(args: JsonObject): ToolRouting {
        val setting = DeviceSetting.fromWire(args.string("setting") ?: args.string("name"))
            ?: throw IllegalArgumentException("I can toggle Wi-Fi, Bluetooth, the flashlight or Do Not Disturb")
        val state = ToggleState.fromWire(args.string("state") ?: args.string("value"))
        val verb = when (state) {
            ToggleState.ON -> "Turn on"
            ToggleState.OFF -> "Turn off"
            ToggleState.TOGGLE -> "Toggle"
        }
        return ToolRouting.Valid(ToolCommand.ToggleSetting(setting, state), "$verb ${setting.displayName}")
    }

    // ---------------------------------------------------------------------------------------- //

    /**
     * Absolute times win over relative ones: the model sometimes emits both
     * (`"datetime": "2026-11-24T19:00", "in_minutes": 0`). Non-positive offsets are ignored.
     */
    private fun resolveReminderTime(args: JsonObject, current: ZonedDateTime): Long? {
        args.long("timestampMillis")?.takeIf { it > 0 }?.let { ts -> return if (ts < 100_000_000_000L) ts * 1000 else ts }
        val text = args.string("datetime") ?: args.string("when") ?: args.string("date_time") ?: args.string("time")
        text?.let { parseDateTime(it, current) }?.let { return it.toInstant().toEpochMilli() }
        (args.long("in_minutes") ?: args.long("delay_minutes"))?.takeIf { it > 0 }
            ?.let { return current.plusMinutes(it).toInstant().toEpochMilli() }
        args.long("in_hours")?.takeIf { it > 0 }?.let { return current.plusHours(it).toInstant().toEpochMilli() }
        args.long("in_seconds")?.takeIf { it > 0 }?.let { return current.plusSeconds(it).toInstant().toEpochMilli() }
        return null
    }

    private fun parseDateTime(text: String, current: ZonedDateTime): ZonedDateTime? {
        val value = text.trim().replace(' ', 'T').removeSuffix("Z")
        // Full ISO date-time (seconds optional).
        runCatching { return LocalDateTime.parse(value).atZone(current.zone) }
        runCatching { return ZonedDateTime.parse(text.trim()).withZoneSameInstant(current.zone) }
        runCatching { return Instant.parse(text.trim()).atZone(current.zone) }
        // Date only -> 09:00 that day.
        runCatching { return LocalDate.parse(value).atTime(9, 0).atZone(current.zone) }
        // Time only -> next occurrence.
        parseClockTime(text)?.let { time ->
            var candidate = current.toLocalDate().atTime(time).atZone(current.zone)
            if (candidate.isBefore(current)) candidate = candidate.plusDays(1)
            return candidate
        }
        return null
    }

    private fun parseClockTime(text: String?): LocalTime? {
        val value = text?.trim()?.lowercase(Locale.ROOT)?.replace(".", "") ?: return null
        val match = CLOCK.matchEntire(value) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        when (match.groupValues[3]) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    private fun parseDuration(text: String?): Long? {
        val value = text?.trim()?.lowercase(Locale.ROOT) ?: return null
        HMS.matchEntire(value)?.let { m ->
            val parts = m.value.split(':').map { it.toLong() }
            return parts.fold(0L) { acc, p -> acc * 60 + p }
        }
        var total = 0L
        var matched = false
        DURATION_PART.findAll(value).forEach { m ->
            matched = true
            val amount = m.groupValues[1].toDouble()
            total += when (m.groupValues[2].first()) {
                'h' -> (amount * 3600).toLong()
                'm' -> (amount * 60).toLong()
                else -> amount.toLong()
            }
        }
        return if (matched) total else value.toLongOrNull()
    }

    private fun parseDays(element: kotlinx.serialization.json.JsonElement?): List<Int> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val p = item as? JsonPrimitive ?: return@mapNotNull null
            p.longOrNull?.toInt()?.takeIf { it in Calendar.SUNDAY..Calendar.SATURDAY }
                ?: p.contentOrNull?.let(::dayFromName)
        }.distinct().sorted()
    }

    private fun dayFromName(name: String): Int? {
        val n = name.trim().lowercase(Locale.ROOT)
        val day = DayOfWeek.entries.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(n.take(3)) && n.length >= 2 }
            ?: return null
        return (day.value % 7) + 1 // java.time MONDAY=1..SUNDAY=7 -> Calendar SUNDAY=1..SATURDAY=7
    }

    // ---------------------------------------------------------------------------------------- //

    /** Human-readable one-liner for a confirmation card. */
    fun summarize(command: ToolCommand): String = when (command) {
        is ToolCommand.SetAlarm -> alarmSummary(command)
        is ToolCommand.SetTimer -> timerSummary(command)
        is ToolCommand.CreateReminder -> reminderSummary(command, now())
        is ToolCommand.LaunchApp -> "Open ${command.appName}"
        is ToolCommand.ToggleSetting -> when (command.state) {
            ToggleState.ON -> "Turn on ${command.setting.displayName}"
            ToggleState.OFF -> "Turn off ${command.setting.displayName}"
            ToggleState.TOGGLE -> "Toggle ${command.setting.displayName}"
        }
        ToolCommand.SortApps -> "Organize apps into smart categories"
        ToolCommand.GetTodayAgenda -> "Check today's schedule"
    }

    /** Canonical argument object that [route] maps back to exactly the same command. */
    fun canonicalArguments(command: ToolCommand): JsonObject = buildJsonObject {
        when (command) {
            is ToolCommand.SetAlarm -> {
                put("hour", command.hour)
                put("minute", command.minute)
                command.label?.let { put("label", it) }
                if (command.days.isNotEmpty()) putJsonArray("days") { command.days.forEach { add(JsonPrimitive(it)) } }
            }
            is ToolCommand.SetTimer -> {
                put("seconds", command.seconds)
                command.label?.let { put("label", it) }
            }
            is ToolCommand.CreateReminder -> {
                put("title", command.title)
                put("timestampMillis", command.timestampMillis)
            }
            is ToolCommand.LaunchApp -> put("appName", command.appName)
            is ToolCommand.ToggleSetting -> {
                put("setting", command.setting.wireName)
                put("state", command.state.name.lowercase(Locale.ROOT))
            }
            ToolCommand.SortApps, ToolCommand.GetTodayAgenda -> Unit
        }
    }

    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private fun alarmSummary(c: ToolCommand.SetAlarm): String {
        val time = LocalTime.of(c.hour, c.minute).format(timeFormatter)
        val days = if (c.days.isEmpty()) null else c.days.joinToString(", ") { calendarDayShortName(it) }
        return listOfNotNull("Alarm · $time", c.label, days).joinToString(" · ")
    }

    private fun timerSummary(c: ToolCommand.SetTimer): String {
        val h = c.seconds / 3600
        val m = (c.seconds % 3600) / 60
        val s = c.seconds % 60
        val duration = buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0 || isEmpty()) add("${s}s")
        }.joinToString(" ")
        return listOfNotNull("Timer · $duration", c.label).joinToString(" · ")
    }

    private fun reminderSummary(c: ToolCommand.CreateReminder, current: ZonedDateTime): String {
        val at = Instant.ofEpochMilli(c.timestampMillis).atZone(current.zone)
        val day = when (at.toLocalDate()) {
            current.toLocalDate() -> "Today"
            current.toLocalDate().plusDays(1) -> "Tomorrow"
            else -> at.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
        return "Reminder · $day ${at.format(timeFormatter)} · ${c.title}"
    }

    companion object {
        private val CLOCK = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""")
        private val HMS = Regex("""^\d{1,2}(:\d{2}){1,2}$""")
        private val REPEAT_HINT = Regex(
            """\b(every|daily|each|repeat\w*|weekdays?|weekends?|(mon|tues|wednes|thurs|fri|satur|sun)days?|mon|tue|wed|thu|fri|sat|sun)\b""",
        )
        private val DURATION_PART = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""")

        fun calendarDayShortName(day: Int): String =
            DayOfWeek.of(((day + 5) % 7) + 1).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())

        private fun JsonObject.long(key: String): Long? {
            val p = this[key] as? JsonPrimitive ?: return null
            return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.contentOrNull?.trim()?.toLongOrNull()
        }

        private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    }
}
