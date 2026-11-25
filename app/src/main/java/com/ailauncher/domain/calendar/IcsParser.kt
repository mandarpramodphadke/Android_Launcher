package com.ailauncher.domain.calendar

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** A date or a date-time from an .ics file. A date-time without a zone is "floating" (local). */
sealed interface IcsTime {
    data class Date(val date: LocalDate) : IcsTime
    data class DateTime(val local: LocalDateTime, val zone: ZoneId?) : IcsTime

    fun toInstant(defaultZone: ZoneId): Instant = when (this) {
        is Date -> date.atStartOfDay(defaultZone).toInstant()
        is DateTime -> local.atZone(zone ?: defaultZone).toInstant()
    }
}

/** One BYDAY entry: "MO", "2TU" (second Tuesday) or "-1FR" (last Friday). */
data class WeekdayNum(val ordinal: Int?, val day: DayOfWeek)

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** The parts of an RRULE that calendars use in practice. */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val count: Int? = null,
    val until: IcsTime? = null,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
)

data class IcsEvent(
    val uid: String?,
    val title: String,
    val location: String?,
    val start: IcsTime,
    val end: IcsTime?,
    val duration: Duration?,
    val rule: RecurrenceRule?,
    val exceptions: List<IcsTime>,
    /** Set on a changed copy of one occurrence of a recurring event. */
    val recurrenceId: IcsTime?,
    val cancelled: Boolean,
) {
    val allDay: Boolean get() = start is IcsTime.Date
}

/** One event as it appears on a day, after recurring events are expanded. */
data class CalendarOccurrence(
    val title: String,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

/**
 * Reads iCalendar (.ics) data: what Outlook, iCloud, Proton, Fastmail, school and sports
 * calendars publish as a subscription link or export as a file. Pure and offline.
 */
object IcsParser {

    fun parse(text: String): List<IcsEvent> {
        val events = ArrayList<IcsEvent>()
        var current: MutableMap<String, MutableList<Property>>? = null
        var depth = 0 // nested blocks inside a VEVENT (VALARM)
        unfold(text).forEach { line ->
            val property = parseLine(line) ?: return@forEach
            when {
                property.name == "BEGIN" && property.value.equals("VEVENT", ignoreCase = true) -> current = LinkedHashMap()
                property.name == "BEGIN" && current != null -> depth++
                property.name == "END" && current != null && depth > 0 -> depth--
                property.name == "END" && property.value.equals("VEVENT", ignoreCase = true) -> {
                    current?.let { toEvent(it) }?.let(events::add)
                    current = null
                    depth = 0
                }
                current != null && depth == 0 -> current.getOrPut(property.name) { ArrayList() } += property
            }
        }
        return events
    }

    /**
     * Every occurrence that overlaps [from, to), recurring events expanded, cancelled ones and
     * exceptions removed, and changed occurrences (RECURRENCE-ID) replacing the originals.
     */
    fun occurrences(events: List<IcsEvent>, from: Instant, to: Instant, defaultZone: ZoneId, limit: Int = 5_000): List<CalendarOccurrence> {
        val overrides = events.filter { it.recurrenceId != null && it.uid != null }
        val replaced = overrides.groupBy({ it.uid!! }) { it.recurrenceId!!.toInstant(defaultZone) }
        val out = ArrayList<CalendarOccurrence>()
        for (event in events) {
            if (out.size >= limit) break
            if (event.cancelled) continue
            val skip = if (event.recurrenceId == null) replaced[event.uid].orEmpty().toSet() else emptySet()
            for ((start, end) in expand(event, from, to, defaultZone, limit - out.size)) {
                if (start.toInstant() in skip) continue
                if (end.toInstant() <= from || start.toInstant() >= to) continue
                out += CalendarOccurrence(event.title, event.location, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), event.allDay)
            }
        }
        return out.sortedBy { it.startMillis }
    }

    // ------------------------------------------------------------------------ expansion

    private fun expand(event: IcsEvent, from: Instant, to: Instant, zone: ZoneId, limit: Int): List<Pair<java.time.ZonedDateTime, java.time.ZonedDateTime>> {
        val eventZone = (event.start as? IcsTime.DateTime)?.zone ?: zone
        val first = when (val s = event.start) {
            is IcsTime.Date -> s.date.atStartOfDay(eventZone)
            is IcsTime.DateTime -> s.local.atZone(eventZone)
        }
        val length: Duration = when {
            event.end != null -> Duration.between(first.toInstant(), event.end.toInstant(eventZone)).takeIf { !it.isNegative } ?: Duration.ZERO
            event.duration != null -> event.duration
            event.allDay -> Duration.ofDays(1)
            else -> Duration.ZERO
        }
        val rule = event.rule ?: return listOf(first to first.plus(length))
        val excluded = event.exceptions.map { it.toInstant(eventZone) }.toSet()
        val until = rule.until?.toInstant(eventZone)
        val result = ArrayList<Pair<java.time.ZonedDateTime, java.time.ZonedDateTime>>()
        var produced = 0
        val time = first.toLocalTime()
        for (date in candidateDates(rule, first.toLocalDate())) {
            val start = date.atTime(time).atZone(eventZone)
            if (until != null && start.toInstant() > until) break
            if (rule.count != null && produced >= rule.count) break
            if (start.toInstant() >= to) break
            produced++
            if (start.toInstant() in excluded) continue
            if (start.plus(length).toInstant() > from) result += start to start.plus(length)
            if (result.size >= limit) break
        }
        return result
    }

    /** Dates of a rule from [start] on, in order (bounded so a bad rule can't loop forever). */
    private fun candidateDates(rule: RecurrenceRule, start: LocalDate): Sequence<LocalDate> = sequence {
        val step = rule.interval.coerceAtLeast(1).toLong()
        var period = 0L
        while (period < MAX_PERIODS) {
            val dates: List<LocalDate> = when (rule.frequency) {
                Frequency.DAILY -> listOf(start.plusDays(period * step))
                Frequency.WEEKLY -> {
                    val weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(period * step)
                    val days = rule.byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }
                    days.map { weekStart.with(TemporalAdjusters.nextOrSame(it)) }.sorted()
                }
                Frequency.MONTHLY -> monthDates(rule, start, start.withDayOfMonth(1).plusMonths(period * step))
                Frequency.YEARLY -> {
                    val year = start.year + (period * step).toInt()
                    val months = rule.byMonth.ifEmpty { listOf(start.monthValue) }
                    months.sorted().flatMap { month -> monthDates(rule, start, LocalDate.of(year, month, 1)) }
                }
            }
            for (date in dates) if (!date.isBefore(start)) yield(date)
            period++
        }
    }

    private fun monthDates(rule: RecurrenceRule, start: LocalDate, month: LocalDate): List<LocalDate> {
        val length = month.lengthOfMonth()
        return when {
            rule.byMonthDay.isNotEmpty() -> rule.byMonthDay.mapNotNull { day ->
                val d = if (day < 0) length + day + 1 else day
                if (d in 1..length) month.withDayOfMonth(d) else null
            }.sorted()
            rule.byDay.isNotEmpty() -> rule.byDay.flatMap { wd ->
                val all = (1..length).map { month.withDayOfMonth(it) }.filter { it.dayOfWeek == wd.day }
                when (val n = wd.ordinal) {
                    null -> all
                    else -> listOfNotNull(if (n > 0) all.getOrNull(n - 1) else all.getOrNull(all.size + n))
                }
            }.sorted()
            // Same day as the first event; months without it (the 31st) are skipped, as RFC 5545 says.
            start.dayOfMonth <= length -> listOf(month.withDayOfMonth(start.dayOfMonth))
            else -> emptyList()
        }
    }

    // --------------------------------------------------------------------------- reading

    private data class Property(val name: String, val params: Map<String, String>, val value: String)

    /** Joins folded lines (a line starting with a space or tab continues the previous one). */
    private fun unfold(text: String): List<String> {
        val lines = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + raw.substring(1)
            } else if (raw.isNotBlank()) {
                lines += raw
            }
        }
        return lines
    }

    private fun parseLine(line: String): Property? {
        var inQuotes = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) {
                colon = i
                break
            }
        }
        if (colon <= 0) return null
        val head = splitOutsideQuotes(line.substring(0, colon), ';')
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Property(head.first().uppercase().trim(), params, line.substring(colon + 1))
    }

    private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in text) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                c == separator && !inQuotes -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts
    }

    private fun toEvent(props: Map<String, List<Property>>): IcsEvent? {
        val start = props["DTSTART"]?.firstOrNull()?.let(::parseTime) ?: return null
        return IcsEvent(
            uid = props["UID"]?.firstOrNull()?.value?.trim(),
            title = props["SUMMARY"]?.firstOrNull()?.value?.let(::unescape)?.trim()?.ifEmpty { null } ?: "(No title)",
            location = props["LOCATION"]?.firstOrNull()?.value?.let(::unescape)?.trim()?.ifEmpty { null },
            start = start,
            end = props["DTEND"]?.firstOrNull()?.let(::parseTime),
            duration = props["DURATION"]?.firstOrNull()?.value?.let(::parseDuration),
            rule = props["RRULE"]?.firstOrNull()?.value?.let(::parseRule),
            exceptions = props["EXDATE"].orEmpty().flatMap { p -> p.value.split(',').mapNotNull { parseTime(p.copy(value = it)) } },
            recurrenceId = props["RECURRENCE-ID"]?.firstOrNull()?.let(::parseTime),
            cancelled = props["STATUS"]?.firstOrNull()?.value.equals("CANCELLED", ignoreCase = true),
        )
    }

    private fun parseTime(p: Property): IcsTime? {
        val value = p.value.trim()
        return runCatching {
            if (p.params["VALUE"].equals("DATE", ignoreCase = true) || value.length == 8) {
                IcsTime.Date(LocalDate.of(value.substring(0, 4).toInt(), value.substring(4, 6).toInt(), value.substring(6, 8).toInt()))
            } else {
                val date = LocalDate.of(value.substring(0, 4).toInt(), value.substring(4, 6).toInt(), value.substring(6, 8).toInt())
                val time = LocalTime.of(value.substring(9, 11).toInt(), value.substring(11, 13).toInt(), value.substring(13, 15).toInt())
                val zone = when {
                    value.endsWith("Z") -> ZoneOffset.UTC
                    else -> p.params["TZID"]?.let(TimeZones::resolve)
                }
                IcsTime.DateTime(LocalDateTime.of(date, time), zone)
            }
        }.getOrNull()
    }

    private fun parseRule(value: String): RecurrenceRule? {
        val parts = value.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1)
        }.toMap()
        val frequency = runCatching { Frequency.valueOf(parts["FREQ"].orEmpty().uppercase()) }.getOrNull() ?: return null
        return RecurrenceRule(
            frequency = frequency,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 1,
            count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(0),
            until = parts["UNTIL"]?.let { parseTime(Property("UNTIL", emptyMap(), it)) },
            byDay = parts["BYDAY"]?.split(',')?.mapNotNull(::parseWeekdayNum).orEmpty(),
            byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in -31..31 && it != 0 }.orEmpty(),
            byMonth = parts["BYMONTH"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..12 }.orEmpty(),
        )
    }

    private fun parseWeekdayNum(text: String): WeekdayNum? {
        val t = text.trim().uppercase()
        if (t.length < 2) return null
        val day = when (t.takeLast(2)) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> return null
        }
        val ordinal = t.dropLast(2).takeIf { it.isNotEmpty() }?.toIntOrNull()
        return WeekdayNum(ordinal, day)
    }

    /** "PT1H30M", "P1D", "-PT15M" (only the sign-less forms matter for event length). */
    private fun parseDuration(value: String): Duration? = runCatching {
        val v = value.trim().removePrefix("+")
        if (v.contains('W')) Duration.ofDays(v.filter { it.isDigit() }.toLong() * 7) else Duration.parse(v)
    }.getOrNull()?.takeIf { !it.isNegative }

    private fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n', 'N' -> append('\n')
                    else -> append(text[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    private const val MAX_PERIODS = 20_000L
}

/** Time zone names in .ics files: IANA ids, Outlook's Windows names, and prefixed variants. */
object TimeZones {
    fun resolve(tzid: String): ZoneId? {
        val id = tzid.trim().trim('"')
        runCatching { return ZoneId.of(id) }
        WINDOWS[id]?.let { return ZoneId.of(it) }
        // "/mozilla.org/20050126_1/Europe/London" and similar.
        val tail = id.split('/').takeLast(2).joinToString("/")
        return runCatching { ZoneId.of(tail) }.getOrNull()
    }

    private val WINDOWS = mapOf(
        "India Standard Time" to "Asia/Kolkata",
        "Sri Lanka Standard Time" to "Asia/Colombo",
        "Nepal Standard Time" to "Asia/Kathmandu",
        "Bangladesh Standard Time" to "Asia/Dhaka",
        "Pakistan Standard Time" to "Asia/Karachi",
        "Afghanistan Standard Time" to "Asia/Kabul",
        "Myanmar Standard Time" to "Asia/Yangon",
        "SE Asia Standard Time" to "Asia/Bangkok",
        "Singapore Standard Time" to "Asia/Singapore",
        "China Standard Time" to "Asia/Shanghai",
        "Taipei Standard Time" to "Asia/Taipei",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "Korea Standard Time" to "Asia/Seoul",
        "West Asia Standard Time" to "Asia/Tashkent",
        "Arabian Standard Time" to "Asia/Dubai",
        "Arab Standard Time" to "Asia/Riyadh",
        "Arabic Standard Time" to "Asia/Baghdad",
        "Iran Standard Time" to "Asia/Tehran",
        "Israel Standard Time" to "Asia/Jerusalem",
        "Turkey Standard Time" to "Europe/Istanbul",
        "Russian Standard Time" to "Europe/Moscow",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Romance Standard Time" to "Europe/Paris",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Central European Standard Time" to "Europe/Warsaw",
        "E. Europe Standard Time" to "Europe/Chisinau",
        "FLE Standard Time" to "Europe/Kiev",
        "GTB Standard Time" to "Europe/Bucharest",
        "Egypt Standard Time" to "Africa/Cairo",
        "South Africa Standard Time" to "Africa/Johannesburg",
        "W. Central Africa Standard Time" to "Africa/Lagos",
        "E. Africa Standard Time" to "Africa/Nairobi",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "E. Australia Standard Time" to "Australia/Brisbane",
        "Cen. Australia Standard Time" to "Australia/Adelaide",
        "W. Australia Standard Time" to "Australia/Perth",
        "New Zealand Standard Time" to "Pacific/Auckland",
        "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Alaskan Standard Time" to "America/Anchorage",
        "Pacific Standard Time" to "America/Los_Angeles",
        "US Mountain Standard Time" to "America/Phoenix",
        "Mountain Standard Time" to "America/Denver",
        "Central Standard Time" to "America/Chicago",
        "Canada Central Standard Time" to "America/Regina",
        "Central Standard Time (Mexico)" to "America/Mexico_City",
        "Eastern Standard Time" to "America/New_York",
        "Atlantic Standard Time" to "America/Halifax",
        "Newfoundland Standard Time" to "America/St_Johns",
        "SA Pacific Standard Time" to "America/Bogota",
        "E. South America Standard Time" to "America/Sao_Paulo",
        "Argentina Standard Time" to "America/Argentina/Buenos_Aires",
        "UTC" to "UTC",
        "Coordinated Universal Time" to "UTC",
    )
}
