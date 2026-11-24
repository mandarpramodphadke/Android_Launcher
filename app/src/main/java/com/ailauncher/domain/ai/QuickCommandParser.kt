package com.ailauncher.domain.ai

import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Deterministic fast path for the most common launcher commands ("open spotify", "timer 5 min",
 * "alarm at 7am", "flashlight on"). They execute instantly, work before the model is downloaded
 * and never wake the LLM; anything else falls through to Qwen3.
 */
class QuickCommandParser(private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) {

    fun parse(input: String): ToolCommand? {
        val text = clean(input).lowercase(Locale.ROOT)
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        // Same cleanup without lower-casing, for groups whose case matters (messages, task titles).
        val original = clean(input)
        // Longer inputs are only quick commands when they are clearly a message or a task.
        if (text.length > SHORT_LENGTH) return parseDictated(original)

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

        parseLauncherTools(text, original)?.let { return it }

        OPEN.matchEntire(text)?.let { m ->
            val name = m.groupValues[2].removePrefix("the ").removePrefix("my ").removeSuffix(" app").trim()
            if (name.isNotEmpty() && name !in NOT_APPS) return ToolCommand.LaunchApp(name)
        }
        return null
    }

    /** To-dos, planner, storage insights, notifications and contacts. */
    private fun parseLauncherTools(text: String, original: String): ToolCommand? {
        val today = now().toLocalDate()

        UNUSED_APPS.firstNotNullOfOrNull { it.matchEntire(text) }?.let { m ->
            val days = m.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..3650 }
            return ToolCommand.ListUnusedApps(days)
        }
        LARGEST_FILES.matchEntire(text)?.let { m ->
            val limit = m.groupValues[1].toIntOrNull()?.coerceIn(1, 50) ?: 15
            return ToolCommand.FindLargestFiles(limit, FileTypeFilter.fromWire(m.groupValues[3]) ?: FileTypeFilter.ALL)
        }
        if (STORAGE_HOG.matches(text)) return ToolCommand.FindLargestFiles(15, FileTypeFilter.ALL)
        STALE_FILES.matchEntire(text)?.let { m ->
            val days = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..3650 }
            return ToolCommand.QueryFilesByActivity(FileActivityMode.UNUSED_SINCE, days, null, null, fileType(m.groupValues[1]))
        }
        (DOWNLOADED.matchEntire(text) ?: DOWNLOADED_QUESTION.matchEntire(text))?.let { m ->
            val span = DateRangeParser.parse(m.groupValues[2], today) ?: return@let
            val end = if (span.endInclusive.isAfter(today)) today else span.endInclusive
            return ToolCommand.QueryFilesByActivity(FileActivityMode.DOWNLOADED, null, span.start, end, fileType(m.groupValues[1]))
        }

        if (TODO_LIST.matches(text)) return ToolCommand.ManageTodo(TodoAction.LIST)
        NAMED_LIST.matchEntire(text)?.let { m ->
            val name = m.groupValues[1]
            if (name !in NOT_LISTS) return ToolCommand.ManageTodo(TodoAction.LIST, listName = name.replaceFirstChar { it.titlecase(Locale.getDefault()) })
        }
        parseTodo(original)?.let { return it }

        DAY_PLAN.matchEntire(text)?.let { m ->
            val day = m.groupValues[1].ifBlank { "today" }
            val date = DateRangeParser.parseDay(day, today, preferFuture = true) ?: return@let
            return ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, date)
        }

        NOTIFICATIONS.matchEntire(text)?.let { m ->
            return ToolCommand.SummarizeNotifications(m.groupValues[1].trim().takeIf { it.isNotEmpty() })
        }
        if (WHAT_DID_I_MISS.matches(text)) return ToolCommand.SummarizeNotifications(null)

        parseMessage(original)?.let { return it }
        CALL.matchEntire(original)?.let { m ->
            var name = m.groupValues[1].trim()
            val viaWhatsApp = name.endsWith(" on whatsapp", ignoreCase = true)
            name = name.removeSuffix(" on whatsapp").removeSuffix(" on WhatsApp").removeSuffix(" on Whatsapp")
                .replace(Regex("^my\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (name.isNotEmpty() && !NOT_CONTACT.containsMatchIn(name.lowercase(Locale.ROOT))) {
                return ToolCommand.ContactAction(if (viaWhatsApp) ContactActionType.WHATSAPP else ContactActionType.CALL, name, null)
            }
        }
        return null
    }

    /** Commands that can legitimately be long: a message to send or a task to add. */
    private fun parseDictated(original: String): ToolCommand? = parseMessage(original) ?: parseTodo(original)

    private fun parseTodo(original: String): ToolCommand? {
        TODO_ADD_TO_LIST.matchEntire(original)?.let { m ->
            val title = m.groupValues[1].trim().trim('"', '\'')
            val list = m.groupValues[2].trim()
            if (title.isNotEmpty() && title.length <= MAX_TITLE && list.lowercase(Locale.ROOT) !in NOT_LISTS) {
                val listName = if (GENERIC_LIST.matches(list.lowercase(Locale.ROOT))) TodoItem.DEFAULT_LIST else list.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                return ToolCommand.ManageTodo(TodoAction.ADD, title.replaceFirstChar { it.titlecase(Locale.getDefault()) }, listName, TodoPriority.MEDIUM)
            }
        }
        TODO_ADD.matchEntire(original)?.let { m ->
            val title = m.groupValues[1].trim().trim('"', '\'')
            if (title.isNotEmpty() && title.length <= MAX_TITLE && !GENERIC_LIST.matches(title.lowercase(Locale.ROOT)) && title.lowercase(Locale.ROOT) != "list") {
                return ToolCommand.ManageTodo(TodoAction.ADD, title.replaceFirstChar { it.titlecase(Locale.getDefault()) }, TodoItem.DEFAULT_LIST, TodoPriority.MEDIUM)
            }
        }
        return null
    }

    private fun parseMessage(original: String): ToolCommand? {
        val m = MESSAGE.matchEntire(original) ?: return null
        val action = if (m.groupValues[1].equals("whatsapp", ignoreCase = true)) ContactActionType.WHATSAPP else ContactActionType.SMS
        val name = m.groupValues[2].trim().replace(Regex("^my\\s+", RegexOption.IGNORE_CASE), "")
        val message = m.groupValues[3].trim()
        return if (name.isNotEmpty() && name.length <= 40 && message.isNotEmpty()) ToolCommand.ContactAction(action, name, message) else null
    }

    private fun fileType(word: String): FileTypeFilter = FileTypeFilter.fromWire(word) ?: FileTypeFilter.ALL

    private fun clean(input: String) = input.trim()
        .replace("a.m.", "am").replace("p.m.", "pm").replace("A.M.", "AM").replace("P.M.", "PM")
        .removeSuffix(".").removeSuffix("!").removeSuffix("please").removeSuffix("Please").trim()
        .replace(Regex("^(please|hey)\\s+", RegexOption.IGNORE_CASE), "")
        .trim()

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

        const val MAX_LENGTH = 240
        const val SHORT_LENGTH = 80
        const val MAX_TITLE = 160
        private const val DAYS = """(?:\s+(?:for|in|over|from|since)\s+(?:the\s+)?(?:last\s+|past\s+)?(\d+)\s+days?)?"""
        val UNUSED_APPS = listOf(
            Regex("""^(?:show|list|find|which|what)?\s*(?:are\s+)?(?:me\s+)?(?:my\s+)?(?:unused|inactive)\s+apps$DAYS\??$"""),
            Regex("""^(?:show\s+|list\s+|find\s+|which\s+)?(?:me\s+)?apps\s+(?:i\s+)?(?:haven'?t|have\s+not|didn'?t|never|not|don'?t)\s+(?:been\s+)?(?:use|used|open|opened)$DAYS\??$"""),
        )
        val LARGEST_FILES = Regex(
            """^(?:show|find|list|what\s+are)?\s*(?:me\s+)?(?:my\s+)?(?:the\s+)?(?:(\d+)\s+)?(biggest|largest|large|huge|heaviest)\s+""" +
                """(files?|videos?|photos?|images?|pictures?|documents?|pdfs?|archives?|zips?|audio|songs?|music|recordings?)""" +
                """(?:\s+(?:on\s+(?:my\s+)?(?:phone|storage)|taking\s+(?:up\s+)?space))?\??$""",
        )
        val STORAGE_HOG = Regex("""^what'?s?\s+(?:is\s+)?(?:taking\s+up|using|eating|filling)\s+(?:all\s+)?(?:my\s+)?(?:space|storage)( on my phone)?\??$""")
        val STALE_FILES = Regex(
            """^(?:show|find|list)?\s*(?:me\s+)?(?:my\s+)?(?:old|stale|unused|forgotten)\s+(files|videos|photos|documents|downloads|pdfs)""" +
                """(?:\s+(?:older\s+than|not\s+(?:used|opened|touched)\s+(?:in|for))\s+(\d+)\s+days?)?\??$""",
        )
        val DOWNLOADED = Regex("""^(?:show\s+|list\s+|find\s+)?(?:me\s+)?(?:my\s+)?(?:the\s+)?(files|pdfs|videos|photos|documents|stuff|things)?\s*(?:i\s+)?downloaded\s+(.+?)\??$""")
        val TODO_ADD_TO_LIST = Regex("""^add\s+(.+?)\s+to\s+(?:my\s+|the\s+)?(.+?)\s+list$""", RegexOption.IGNORE_CASE)
        val TODO_ADD = Regex("""^(?:add\s+(?:a\s+)?(?:to-?do|task)|new\s+(?:to-?do|task)|to-?do|task)\s*[:\-]?\s+(.+)$""", RegexOption.IGNORE_CASE)
        val GENERIC_LIST = Regex("""^(to-?do|todos?|tasks?|task|my)$""")
        val TODO_LIST = Regex(
            """^(?:(?:show|list|open|view|see|check)\s+)?(?:me\s+)?(?:my\s+)?(?:to-?dos?|tasks|to-?do\s+list|task\s+list)\??$|""" +
                """^what'?s?\s+(?:is\s+)?on\s+my\s+(?:to-?do|task)\s+list\??$|^what\s+do\s+i\s+(?:still\s+)?(?:need|have)\s+to\s+do\??$""",
        )
        val NAMED_LIST = Regex("""^(?:show|open|view|see|check)\s+(?:me\s+)?(?:my\s+)?([a-z]+)\s+list$""")
        val NOT_LISTS = setOf("to", "my", "the", "play", "contact", "contacts", "favorites", "favourites", "block", "black", "white", "guest", "mailing", "email")
        val DAY_PLAN = Regex("""^(?:show\s+(?:me\s+)?|what'?s\s+|what\s+is\s+)?(?:my\s+)?(?:day\s+plan|plan|planner|time\s+blocks)(?:\s+for)?\s*(today|tomorrow|tonight|(?:next\s+|this\s+)?(?:mon|tues|wednes|thurs|fri|satur|sun)day)?\??$""")
        val NOTIFICATIONS = Regex("""^(?:summari[sz]e|show|read|check)\s+(?:me\s+)?(?:my\s+)?(?:recent\s+|latest\s+|today'?s\s+)?notifications(?:\s+from\s+(.+))?$""")
        val WHAT_DID_I_MISS = Regex("""^what\s+did\s+i\s+miss(\s+today)?\??$""")
        val MESSAGE = Regex("""^(text|sms|message|whatsapp)\s+(.+?)\s*(?:[:,]|\s+saying|\s+that)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val CALL = Regex("""^(?:call|phone|dial|ring)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val NOT_CONTACT = Regex("""^(me|it|back|a|an|the|for|someone|somebody|911|112|999|100|108)\b""")
        val DOWNLOADED_QUESTION = Regex("""^what\s+(files\s+|pdfs\s+|videos\s+|photos\s+)?did\s+i\s+download\s+(.+?)\??$""")
    }
}
