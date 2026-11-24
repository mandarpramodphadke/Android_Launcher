package com.ailauncher.domain.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** An inclusive range of calendar days. */
data class DateSpan(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Range ends before it starts" }
    }

    fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMillisExclusive(zone: ZoneId): Long = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        fun day(date: LocalDate) = DateSpan(date, date)

        /** The [days] calendar days ending today, e.g. 7 = today and the six days before it. */
        fun lastDays(days: Long, today: LocalDate) = DateSpan(today.minusDays((days - 1).coerceAtLeast(0)), today)
    }
}

/**
 * Turns the date phrases people use with the file and planner tools ("yesterday", "last week",
 * "past 3 days", "in October", "since Nov 3", "2026-11-01 to 2026-11-05") into concrete days.
 * Pure and locale-independent (English phrases; ISO dates work in any language).
 */
object DateRangeParser {

    /** A range phrase, or null if [text] isn't one. Ranges never extend past [today] unless stated. */
    fun parse(text: String, today: LocalDate): DateSpan? {
        val t = normalize(text)
        if (t.isEmpty()) return null

        when (t) {
            "today" -> return DateSpan.day(today)
            "yesterday" -> return DateSpan.day(today.minusDays(1))
            "this week", "current week" -> return DateSpan(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)
            "last week", "previous week", "past week" -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
                return DateSpan(monday, monday.plusDays(6))
            }
            "this month", "current month" -> return DateSpan(today.withDayOfMonth(1), today)
            "last month", "previous month", "past month" -> {
                val first = today.withDayOfMonth(1).minusMonths(1)
                return DateSpan(first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            "this year" -> return DateSpan(today.withDayOfYear(1), today)
            "last year", "previous year" -> {
                val first = today.withDayOfYear(1).minusYears(1)
                return DateSpan(first, first.with(TemporalAdjusters.lastDayOfYear()))
            }
            "recently", "lately" -> return DateSpan.lastDays(7, today)
        }

        LAST_N.matchEntire(t)?.let { m ->
            val n = numberOf(m.groupValues[1]) ?: return@let
            if (n <= 0) return@let
            return when (m.groupValues[2].first()) {
                'd' -> DateSpan.lastDays(n, today)
                'w' -> DateSpan.lastDays(n * 7, today)
                'm' -> DateSpan(today.minusMonths(n).plusDays(1), today)
                else -> DateSpan(today.minusYears(n).plusDays(1), today)
            }
        }

        AGO.matchEntire(t)?.let { m ->
            val n = numberOf(m.groupValues[1]) ?: return@let
            val day = when (m.groupValues[2].first()) {
                'd' -> today.minusDays(n)
                'w' -> today.minusWeeks(n)
                'm' -> today.minusMonths(n)
                else -> today.minusYears(n)
            }
            return DateSpan.day(day)
        }

        SINCE.matchEntire(t)?.let { m ->
            val from = parseDay(m.groupValues[1], today) ?: parse(m.groupValues[1], today)?.start ?: return@let
            return if (from.isAfter(today)) DateSpan.day(from) else DateSpan(from, today)
        }

        (DOTS.matchEntire(t) ?: BETWEEN.matchEntire(t))?.let { m ->
            val a = parseDay(m.groupValues[1], today) ?: return@let
            val b = parseDay(m.groupValues[2], today) ?: return@let
            return if (b.isBefore(a)) DateSpan(b, a) else DateSpan(a, b)
        }

        MONTH.matchEntire(t)?.let { m ->
            val month = monthOf(m.groupValues[1]) ?: return@let
            val year = m.groupValues[2].toIntOrNull()
                ?: if (month.value > today.monthValue) today.year - 1 else today.year
            val first = LocalDate.of(year, month, 1)
            val last = first.with(TemporalAdjusters.lastDayOfMonth())
            return DateSpan(first, if (last.isAfter(today) && first <= today) today else last)
        }

        parseDay(t.removePrefix("on "), today)?.let { return DateSpan.day(it) }
        return null
    }

    /**
     * A single day: ISO (`2026-11-24`, also with a time), today / tomorrow / yesterday, weekdays,
     * "3 nov", "november 3rd", "in 2 days", "3 days ago". Without a year or a "next"/"last",
     * [preferFuture] picks the next occurrence (planning) instead of the most recent one (files).
     */
    fun parseDay(text: String, today: LocalDate, preferFuture: Boolean = false): LocalDate? {
        val t = normalize(text)
        if (t.isEmpty()) return null
        ISO_DATE.find(t)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        when (t) {
            "today", "tonight", "this evening", "this morning", "this afternoon", "now" -> return today
            "tomorrow", "tmrw", "tmr" -> return today.plusDays(1)
            "yesterday" -> return today.minusDays(1)
            "day after tomorrow" -> return today.plusDays(2)
            "day before yesterday" -> return today.minusDays(2)
        }
        IN_N_DAYS.matchEntire(t)?.let { m -> numberOf(m.groupValues[1])?.let { return today.plusDays(it) } }
        AGO.matchEntire(t)?.let { m ->
            val n = numberOf(m.groupValues[1]) ?: return@let
            if (m.groupValues[2].first() == 'd') return today.minusDays(n)
        }
        WEEKDAY.matchEntire(t)?.let { m ->
            val day = weekdayOf(m.groupValues[2]) ?: return@let
            return when (m.groupValues[1].trim()) {
                "next" -> today.with(TemporalAdjusters.next(day))
                "last", "previous", "past" -> today.with(TemporalAdjusters.previous(day))
                "this" -> if (preferFuture) today.with(TemporalAdjusters.nextOrSame(day)) else today.with(TemporalAdjusters.previousOrSame(day))
                else -> if (preferFuture) today.with(TemporalAdjusters.nextOrSame(day)) else today.with(TemporalAdjusters.previousOrSame(day))
            }
        }
        val dayMonth = DAY_MONTH.matchEntire(t)?.let { m -> Triple(m.groupValues[1].toInt(), monthOf(m.groupValues[2]), m.groupValues[3]) }
            ?: MONTH_DAY.matchEntire(t)?.let { m -> Triple(m.groupValues[2].toInt(), monthOf(m.groupValues[1]), m.groupValues[3]) }
        if (dayMonth != null) {
            val (day, month, yearText) = dayMonth
            month ?: return null
            val explicitYear = yearText.toIntOrNull()
            fun at(year: Int) = runCatching { LocalDate.of(year, month, day) }.getOrNull()
            if (explicitYear != null) return at(explicitYear)
            val thisYear = at(today.year) ?: return null
            return when {
                preferFuture && thisYear.isBefore(today) -> at(today.year + 1)
                !preferFuture && thisYear.isAfter(today) -> at(today.year - 1)
                else -> thisYear
            }
        }
        return null
    }

    private fun normalize(text: String) = text.trim().lowercase(Locale.ROOT)
        .replace(Regex("""[,.!?]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .removePrefix("from ").removePrefix("during ").removePrefix("over ").removePrefix("in the ").removePrefix("within the ")
        .removePrefix("within ").removePrefix("the ")
        .trim()

    private fun numberOf(text: String): Long? = text.toLongOrNull() ?: WORD_NUMBERS[text]

    private fun monthOf(text: String): Month? {
        val t = text.trim().removeSuffix(".")
        if (t.length < 3) return null
        return Month.entries.firstOrNull { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT).startsWith(t) }
    }

    private fun weekdayOf(text: String): DayOfWeek? {
        val t = text.trim()
        if (t.length < 3) return null
        return DayOfWeek.entries.firstOrNull { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT).startsWith(t) }
    }

    private const val UNIT = """(days?|weeks?|months?|years?)"""
    private const val NUM = """(\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fourteen|fifteen|twenty|thirty|sixty|ninety)"""
    private val LAST_N = Regex("""^(?:the\s+)?(?:last|past|previous)\s+$NUM\s+$UNIT$""")
    private val AGO = Regex("""^$NUM\s+$UNIT\s+ago$""")
    private val IN_N_DAYS = Regex("""^in\s+$NUM\s+days?$""")
    private val SINCE = Regex("""^since\s+(.+)$""")
    private val DOTS = Regex("""^(\d{4}-\d{1,2}-\d{1,2})\s*(?:\.\.|/)\s*(\d{4}-\d{1,2}-\d{1,2})$""")
    private val BETWEEN = Regex("""^(?:between\s+)?(.+?)\s+(?:to|until|till|through|and|-|–|\.\.)\s+(.+)$""")
    private val MONTH = Regex("""^(?:in\s+|during\s+)?([a-z]{3,9})\.?(?:\s+(\d{4}))?$""")
    private val ISO_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val WEEKDAY = Regex("""^(next\s+|last\s+|previous\s+|past\s+|this\s+|on\s+)?([a-z]{3,9})$""")
    private val DAY_MONTH = Regex("""^(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([a-z]{3,9})\.?(?:,?\s+(\d{4}))?$""")
    private val MONTH_DAY = Regex("""^([a-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?$""")

    private val WORD_NUMBERS = mapOf(
        "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L, "seven" to 7L,
        "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "fourteen" to 14L,
        "fifteen" to 15L, "twenty" to 20L, "thirty" to 30L, "sixty" to 60L, "ninety" to 90L,
    )
}
