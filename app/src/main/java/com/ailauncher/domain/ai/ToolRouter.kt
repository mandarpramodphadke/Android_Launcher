package com.ailauncher.domain.ai

import com.ailauncher.domain.ai.ModelOutputParser.string
import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
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
            ToolNames.GET_TODAY_AGENDA, "get_agenda", "today_agenda" -> valid(agenda(userText))
            ToolNames.GET_DEVICE_INFO, "device_info", "get_device_specs", "get_system_info", "system_info", "phone_info" ->
                valid(ToolCommand.GetDeviceInfo)
            ToolNames.LIST_UNUSED_APPS, "unused_apps", "list_inactive_apps", "find_unused_apps", "get_unused_apps" ->
                valid(listUnusedApps(call.arguments, userText))
            ToolNames.FIND_LARGEST_FILES, "largest_files", "find_large_files", "biggest_files", "find_biggest_files" ->
                valid(findLargestFiles(call.arguments))
            ToolNames.QUERY_FILES_BY_ACTIVITY, "query_files", "find_files", "search_files", "files_by_date" ->
                valid(queryFiles(call.arguments, userText))
            ToolNames.MANAGE_TODO, "todo", "todos", "manage_todos", "add_todo", "add_task", "task", "tasks", "manage_tasks" ->
                valid(manageTodo(call.arguments, call.name, userText))
            ToolNames.MANAGE_PLANNER, "planner", "plan_day", "schedule_block", "day_plan", "get_day_plan" ->
                valid(managePlanner(call.arguments, call.name, userText))
            ToolNames.SUMMARIZE_NOTIFICATIONS, "get_notifications", "read_notifications", "notifications", "notification_digest" ->
                valid(ToolCommand.SummarizeNotifications(call.arguments.firstString("appName", "app_name", "app")?.takeUnless { it.lowercase(Locale.ROOT) in ALL_APPS }))
            ToolNames.CONTACT_ACTION, "call_contact", "call", "send_sms", "send_message", "sms", "whatsapp", "text_contact", "message_contact" ->
                valid(contactAction(call.arguments, call.name))
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

    private fun valid(command: ToolCommand) = ToolRouting.Valid(command, summarize(command))

    /** "What's on tomorrow?" with get_today_agenda: that day's plan (it includes calendar events). */
    private fun agenda(userText: String?): ToolCommand {
        val today = now().toLocalDate()
        val day = userText?.let { ArgumentGrounding.mentionedDay(it, today) }
        return if (day != null && day != today) ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, day) else ToolCommand.GetTodayAgenda
    }

    private fun listUnusedApps(args: JsonObject, userText: String?): ToolCommand {
        val days = userText?.let(ArgumentGrounding::mentionedDays)
            ?: args.int("inactiveDays") ?: args.int("inactive_days") ?: args.int("days") ?: args.int("unusedDays")
        days?.let { require(it in 1..3650) { "Days must be between 1 and 3650" } }
        return ToolCommand.ListUnusedApps(days)
    }

    private fun findLargestFiles(args: JsonObject): ToolCommand {
        val limit = (args.int("limit") ?: args.int("count") ?: args.int("top") ?: DEFAULT_FILE_LIMIT).coerceIn(1, MAX_FILE_LIMIT)
        val type = FileTypeFilter.fromWire(args.firstString("fileType", "file_type", "type", "kind")) ?: FileTypeFilter.ALL
        return ToolCommand.FindLargestFiles(limit, type)
    }

    private fun queryFiles(args: JsonObject, userText: String?): ToolCommand {
        val today = now().toLocalDate()
        var unusedDays = args.int("unusedDays") ?: args.int("unused_days") ?: args.int("days") ?: args.int("olderThanDays")
        var start = args.firstString("startDateIso", "start_date_iso", "startDate", "start_date", "from", "since")
            ?.let { dayArg(it, today, preferFuture = false) }
        var end = args.firstString("endDateIso", "end_date_iso", "endDate", "end_date", "to", "until")
            ?.let { dayArg(it, today, preferFuture = false) }
        args.firstString("range", "period", "when", "date", "dateRange")?.let { phrase ->
            DateRangeParser.parse(phrase, today)?.let { span ->
                if (start == null) start = span.start
                if (end == null) end = span.endInclusive
            }
        }
        // The user's own words decide the mode and dates when they are explicit.
        val spoken = userText?.let {
            when {
                ArgumentGrounding.mentionsUnused(it) -> FileActivityMode.UNUSED_SINCE
                ArgumentGrounding.mentionsDownload(it) -> FileActivityMode.DOWNLOADED
                ArgumentGrounding.mentionsOpened(it) -> FileActivityMode.OPENED_OR_CLICKED
                else -> null
            }
        }
        val mode = spoken ?: FileActivityMode.fromWire(args.firstString("filterMode", "filter_mode", "mode", "filter"))
            ?: when {
                unusedDays != null -> FileActivityMode.UNUSED_SINCE
                start != null || end != null -> FileActivityMode.MODIFIED_RANGE
                else -> throw IllegalArgumentException("Should I look for unused, downloaded, opened or modified files?")
            }
        if (userText != null) {
            if (mode == FileActivityMode.UNUSED_SINCE) {
                ArgumentGrounding.mentionedDays(userText)?.let { unusedDays = it }
            } else {
                ArgumentGrounding.mentionedRange(userText, today)?.let { span ->
                    start = span.start
                    end = span.endInclusive
                }
            }
        }
        val type = FileTypeFilter.fromWire(args.firstString("fileType", "file_type", "type", "kind")) ?: FileTypeFilter.ALL
        if (mode == FileActivityMode.UNUSED_SINCE) {
            unusedDays?.let { require(it in 1..3650) { "Days must be between 1 and 3650" } }
            return ToolCommand.QueryFilesByActivity(mode, unusedDays, null, null, type)
        }
        // Ranges: default to the last 7 days, never end in the future, and accept reversed bounds.
        var from = start ?: end?.minusDays(DEFAULT_RANGE_DAYS - 1) ?: today.minusDays(DEFAULT_RANGE_DAYS - 1)
        var to = end ?: today
        if (from.isAfter(to)) {
            val swap = from
            from = to
            to = swap
        }
        if (to.isAfter(today)) to = today
        if (from.isAfter(to)) from = to
        return ToolCommand.QueryFilesByActivity(mode, null, from, to, type)
    }

    private fun manageTodo(args: JsonObject, toolName: String, userText: String?): ToolCommand {
        val title = args.firstString("title", "task", "text", "item", "name", "todo")?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
        val action = TodoAction.fromWire(args.firstString("action", "operation", "op"))
            ?: when (toolName.lowercase(Locale.ROOT)) {
                "add_todo", "add_task" -> TodoAction.ADD
                else -> if (title != null) TodoAction.ADD else TodoAction.LIST
            }
        val listName = (userText?.let(ArgumentGrounding::mentionedList) ?: args.firstString("listName", "list_name", "list", "category"))
            ?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeListName)
        val priority = TodoPriority.fromWire(args.firstString("priority", "importance"))
        val due = userText?.let(::spokenDueTime)
            ?: args.long("dueTimestampMillis")?.takeIf { it > 0 }
            ?: args.firstString("dueIso", "due_iso", "due", "dueDate", "due_date", "deadline", "when")
                ?.let { parseDateTime(it, now()) ?: throw IllegalArgumentException("I couldn't understand the due date \"$it\"") }
                ?.toInstant()?.toEpochMilli()
        val id = args.long("todoId") ?: args.long("todo_id") ?: args.long("id")
        when (action) {
            TodoAction.ADD -> requireNotNull(title) { "What should the task say?" }
            TodoAction.COMPLETE, TodoAction.DELETE -> require(id != null || title != null) { "Which task?" }
            TodoAction.LIST -> Unit
        }
        return when (action) {
            TodoAction.ADD -> ToolCommand.ManageTodo(action, title, listName ?: TodoItem.DEFAULT_LIST, priority ?: TodoPriority.MEDIUM, due, null)
            TodoAction.LIST -> ToolCommand.ManageTodo(action, listName = listName)
            else -> ToolCommand.ManageTodo(action, title = title, todoId = id)
        }
    }

    private fun managePlanner(args: JsonObject, toolName: String, userText: String?): ToolCommand {
        val current = now()
        val today = current.toLocalDate()
        val title = args.firstString("title", "name", "task", "activity", "what")?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
        var date = args.firstString("dateIso", "date_iso", "date", "day")
            ?.let { dayArg(it, today, preferFuture = true) ?: throw IllegalArgumentException("I couldn't understand the date \"$it\"") }
        val startText = args.firstString("startTime", "start_time", "start", "time", "at")
        var startMinutes: Int? = null
        if (startText != null) {
            val dateTime = runCatching { LocalDateTime.parse(startText.trim().replace(' ', 'T')) }.getOrNull()
            if (dateTime != null) {
                if (date == null) date = dateTime.toLocalDate()
                startMinutes = dateTime.hour * 60 + dateTime.minute
            } else {
                val time = parseClockTime(startText) ?: throw IllegalArgumentException("I couldn't understand the time \"$startText\"")
                startMinutes = time.hour * 60 + time.minute
            }
        }
        val endMinutes = args.firstString("endTime", "end_time", "end")?.let(::parseClockTime)?.let { it.hour * 60 + it.minute }
        var duration = args.int("durationMinutes") ?: args.int("duration_minutes") ?: args.int("minutes")
            ?: args.string("duration")?.let { parseDuration(it)?.let { s -> (s / 60).toInt() } }
            ?: args.long("hours")?.let { (it * 60).toInt() }
        if (duration == null && startMinutes != null && endMinutes != null && endMinutes > startMinutes) duration = endMinutes - startMinutes
        if (userText != null) {
            ArgumentGrounding.mentionedDay(userText, today)?.let { date = it }
            // Only an unambiguous single time ("at 3pm") replaces the model's; "3 to 5pm" is left to it.
            val lower = userText.lowercase(Locale.ROOT)
            if (!TIME_RANGE.containsMatchIn(lower) && TIME_MENTIONS.findAll(lower).count() == 1) {
                ArgumentGrounding.mentionedTime(userText)?.let { startMinutes = it.hour * 60 + it.minute }
            }
            ArgumentGrounding.mentionedDurationMinutes(userText)?.let { duration = it }
        }
        val blockId = args.long("blockId") ?: args.long("block_id") ?: args.long("id")
        val action = PlannerAction.fromWire(args.firstString("action", "operation", "op"))
            ?: when {
                toolName.equals("schedule_block", ignoreCase = true) -> PlannerAction.SCHEDULE_BLOCK
                toolName.equals("get_day_plan", ignoreCase = true) -> PlannerAction.GET_DAY_PLAN
                title != null && startMinutes != null -> PlannerAction.SCHEDULE_BLOCK
                else -> PlannerAction.GET_DAY_PLAN
            }
        duration?.let { require(it in MIN_BLOCK_MINUTES..MAX_BLOCK_MINUTES) { "Blocks can be 5 minutes to 12 hours long" } }
        return when (action) {
            PlannerAction.SCHEDULE_BLOCK -> {
                requireNotNull(title) { "What is the time block for?" }
                val start = requireNotNull(startMinutes) { "What time should it start?" }
                val length = duration ?: DEFAULT_BLOCK_MINUTES
                require(start + length <= 24 * 60) { "The block has to end by midnight" }
                ToolCommand.ManagePlanner(action, date ?: today, start, length, title, null)
            }
            PlannerAction.RESCHEDULE -> {
                require(blockId != null || title != null) { "Which block should I move?" }
                require(date != null || startMinutes != null || duration != null) { "When should it move to?" }
                ToolCommand.ManagePlanner(action, date, startMinutes, duration, title, blockId)
            }
            PlannerAction.REMOVE_BLOCK -> {
                require(blockId != null || title != null) { "Which block should I remove?" }
                ToolCommand.ManagePlanner(action, date, null, null, title, blockId)
            }
            PlannerAction.GET_DAY_PLAN -> ToolCommand.ManagePlanner(action, date ?: today)
        }
    }

    private fun contactAction(args: JsonObject, toolName: String): ToolCommand {
        val action = ContactActionType.fromWire(args.firstString("action", "type", "method", "via"))
            ?: when (toolName.lowercase(Locale.ROOT)) {
                "call", "call_contact" -> ContactActionType.CALL
                "send_sms", "sms", "text_contact", "send_message", "message_contact" -> ContactActionType.SMS
                "whatsapp" -> ContactActionType.WHATSAPP
                else -> null
            }
            ?: throw IllegalArgumentException("Should I call, text or WhatsApp them?")
        val name = args.firstString("contactName", "contact_name", "contact", "name", "to", "recipient", "person")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Who should I contact?")
        val message = args.firstString("message", "text", "body", "content")?.trim()?.take(MAX_MESSAGE_CHARS)?.takeIf { it.isNotEmpty() }
        return ToolCommand.ContactAction(action, name.take(80), if (action == ContactActionType.CALL) null else message)
    }

    /** A due time spoken in the request ("tomorrow at 5pm", "friday", "at 6pm"), else null. */
    private fun spokenDueTime(userText: String): Long? {
        val current = now()
        val day = ArgumentGrounding.mentionedDay(userText, current.toLocalDate())
        val time = ArgumentGrounding.mentionedTime(userText)
        if (day == null && time == null) return null
        var at = (day ?: current.toLocalDate()).atTime(time ?: LocalTime.of(9, 0)).atZone(current.zone)
        if (day == null && at.isBefore(current)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /** "2026-11-24", "tomorrow", "friday", "nov 3"; null if unparseable. */
    private fun dayArg(text: String, today: LocalDate, preferFuture: Boolean): LocalDate? =
        DateRangeParser.parseDay(text, today, preferFuture)

    private fun normalizeListName(name: String): String =
        name.trim().removeSuffix(" list").removeSuffix(" List").trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }

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
        // "tomorrow", "friday 5pm", "nov 3 at 14:30" (day words first, then an optional time).
        val lower = text.trim().lowercase(Locale.ROOT)
        DAY_AND_TIME.matchEntire(lower)?.let { m ->
            val day = DateRangeParser.parseDay(m.groupValues[1], current.toLocalDate(), preferFuture = true) ?: return@let
            val time = parseClockTime(m.groupValues[2]) ?: return@let
            return day.atTime(time).atZone(current.zone)
        }
        DateRangeParser.parseDay(lower, current.toLocalDate(), preferFuture = true)?.let { return it.atTime(9, 0).atZone(current.zone) }
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
        ToolCommand.GetDeviceInfo -> "Check this phone's specs"
        is ToolCommand.ListUnusedApps -> "Find apps unused for ${command.inactiveDays?.let { "$it days" } ?: "a while"}"
        is ToolCommand.FindLargestFiles -> "Find the ${command.limit} largest ${command.fileType.displayName}"
        is ToolCommand.QueryFilesByActivity -> filesSummary(command)
        is ToolCommand.ManageTodo -> todoSummary(command)
        is ToolCommand.ManagePlanner -> plannerSummary(command)
        is ToolCommand.SummarizeNotifications -> command.appName?.let { "Read notifications from $it" } ?: "Read today's notifications"
        is ToolCommand.ContactAction -> listOfNotNull(
            "${command.action.verb} ${command.contactName}",
            command.message?.let { "\u201c${it.take(60)}${if (it.length > 60) "…" else ""}\u201d" },
        ).joinToString(" · ")
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
            ToolCommand.SortApps, ToolCommand.GetTodayAgenda, ToolCommand.GetDeviceInfo -> Unit
            is ToolCommand.ListUnusedApps -> command.inactiveDays?.let { put("inactiveDays", it) }
            is ToolCommand.FindLargestFiles -> {
                put("limit", command.limit)
                put("fileType", command.fileType.wireName)
            }
            is ToolCommand.QueryFilesByActivity -> {
                put("filterMode", command.mode.wireName)
                command.unusedDays?.let { put("unusedDays", it) }
                command.startDate?.let { put("startDateIso", it.toString()) }
                command.endDate?.let { put("endDateIso", it.toString()) }
                put("fileType", command.fileType.wireName)
            }
            is ToolCommand.ManageTodo -> {
                put("action", command.action.wireName)
                command.title?.let { put("title", it) }
                command.listName?.let { put("listName", it) }
                command.priority?.let { put("priority", it.wireName) }
                command.dueAtMillis?.let { put("dueTimestampMillis", it) }
                command.todoId?.let { put("todoId", it) }
            }
            is ToolCommand.ManagePlanner -> {
                put("action", command.action.wireName)
                command.date?.let { put("dateIso", it.toString()) }
                command.startMinutes?.let { put("startTime", "%02d:%02d".format(Locale.ROOT, it / 60, it % 60)) }
                command.durationMinutes?.let { put("durationMinutes", it) }
                command.title?.let { put("title", it) }
                command.blockId?.let { put("blockId", it) }
            }
            is ToolCommand.SummarizeNotifications -> command.appName?.let { put("appName", it) }
            is ToolCommand.ContactAction -> {
                put("action", command.action.wireName)
                put("contactName", command.contactName)
                command.message?.let { put("message", it) }
            }
        }
    }

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    private fun dayLabel(date: LocalDate): String {
        val today = now().toLocalDate()
        return when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            today.minusDays(1) -> "yesterday"
            else -> date.format(dayFormatter)
        }
    }

    private fun filesSummary(c: ToolCommand.QueryFilesByActivity): String {
        val what = c.fileType.displayName.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return when (c.mode) {
            FileActivityMode.UNUSED_SINCE -> "$what untouched for ${c.unusedDays?.let { "$it days" } ?: "a while"}"
            FileActivityMode.DOWNLOADED -> "$what downloaded ${rangeLabel(c)}"
            FileActivityMode.OPENED_OR_CLICKED -> "$what opened ${rangeLabel(c)}"
            FileActivityMode.MODIFIED_RANGE -> "$what changed ${rangeLabel(c)}"
        }
    }

    private fun rangeLabel(c: ToolCommand.QueryFilesByActivity): String {
        val start = c.startDate ?: return ""
        val end = c.endDate ?: start
        return if (start == end) dayLabel(start) else "${dayLabel(start)} – ${dayLabel(end)}"
    }

    private fun todoSummary(c: ToolCommand.ManageTodo): String = when (c.action) {
        TodoAction.ADD -> listOfNotNull(
            "Add to-do · ${c.title}",
            c.listName?.takeIf { it != TodoItem.DEFAULT_LIST },
            c.priority?.takeIf { it == TodoPriority.HIGH }?.let { "high priority" },
            c.dueAtMillis?.let { "due " + Instant.ofEpochMilli(it).atZone(now().zone).let { at -> "${dayLabel(at.toLocalDate())} ${at.format(timeFormatter)}" } },
        ).joinToString(" · ")
        TodoAction.COMPLETE -> "Mark done · ${c.title ?: "task #${c.todoId}"}"
        TodoAction.DELETE -> "Delete to-do · ${c.title ?: "task #${c.todoId}"}"
        TodoAction.LIST -> c.listName?.let { "Show the $it list" } ?: "Show your to-dos"
    }

    private fun plannerSummary(c: ToolCommand.ManagePlanner): String {
        fun time(minutes: Int) = LocalTime.of(minutes / 60 % 24, minutes % 60).format(timeFormatter)
        return when (c.action) {
            PlannerAction.SCHEDULE_BLOCK -> {
                val start = c.startMinutes ?: 0
                val end = start + (c.durationMinutes ?: DEFAULT_BLOCK_MINUTES)
                "Plan · ${c.date?.let(::dayLabel)?.replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${time(start)}–${time(end)} · ${c.title}"
            }
            PlannerAction.RESCHEDULE -> listOfNotNull(
                "Move · ${c.title ?: "block #${c.blockId}"}",
                c.date?.let(::dayLabel),
                c.startMinutes?.let(::time),
                c.durationMinutes?.let { "$it min" },
            ).joinToString(" · ")
            PlannerAction.REMOVE_BLOCK -> "Remove block · ${c.title ?: "#${c.blockId}"}"
            PlannerAction.GET_DAY_PLAN -> "Show the plan for ${c.date?.let(::dayLabel) ?: "today"}"
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
        private val DAY_AND_TIME = Regex("""^(.+?)\s+(?:at\s+)?(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)$""")
        private val TIME_MENTIONS = Regex("""\b\d{1,2}(?::\d{2})?\s*(?:am|pm)\b|\b(?:[01]?\d|2[0-3]):[0-5]\d\b|\bnoon\b""")
        /** "from 3 to 5pm", "3-5pm", "between 9 and 11": a range, not a start time. */
        private val TIME_RANGE = Regex("""\b\d{1,2}(?::\d{2})?\s*(?:am|pm)?\s*(?:-|–|to|until|till|and)\s*\d{1,2}(?::\d{2})?\s*(?:am|pm)?\b""")
        private val ALL_APPS = setOf("all", "any", "everything", "every app", "all apps", "notifications", "none", "null")
        private const val DEFAULT_FILE_LIMIT = 15
        private const val MAX_FILE_LIMIT = 50
        private const val DEFAULT_RANGE_DAYS = 7L
        const val DEFAULT_BLOCK_MINUTES = 60
        private const val MIN_BLOCK_MINUTES = 5
        private const val MAX_BLOCK_MINUTES = 12 * 60
        private const val MAX_MESSAGE_CHARS = 1000
        private val DURATION_PART = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""")

        fun calendarDayShortName(day: Int): String =
            DayOfWeek.of(((day + 5) % 7) + 1).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())

        private fun JsonObject.long(key: String): Long? {
            val p = this[key] as? JsonPrimitive ?: return null
            return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.contentOrNull?.trim()?.toLongOrNull()
        }

        private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

        private fun JsonObject.firstString(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> string(key)?.trim()?.takeIf { it.isNotEmpty() && it != "null" } }

    }
}
