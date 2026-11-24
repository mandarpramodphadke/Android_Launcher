package com.ailauncher.domain.ai

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * Pulls dates, times, durations and list names out of the user's own words. A 1.7B model picks the
 * right tool reliably but often fumbles these slots ("two months" → 2, "tomorrow" → today), so the
 * router lets an unambiguous phrase in the request win over the model's argument.
 */
internal object ArgumentGrounding {

    /** A single day the user named ("tomorrow", "next friday", "on 3 nov"), leaning to the future. */
    fun mentionedDay(text: String, today: LocalDate): LocalDate? {
        val t = normalize(text)
        DAY_PHRASES.forEach { regex ->
            regex.findAll(t).forEach { m -> DateRangeParser.parseDay(m.value, today, preferFuture = true)?.let { return it } }
        }
        return null
    }

    /** A past range ("since last monday", "last week", "in october", "yesterday"). */
    fun mentionedRange(text: String, today: LocalDate): DateSpan? {
        val t = normalize(text)
        RANGE_PHRASES.forEach { regex ->
            regex.findAll(t).forEach { m -> DateRangeParser.parse(m.value, today)?.let { return it } }
        }
        return null
    }

    /** "two months" → 60, "half a year" → 180, "3 weeks" → 21, "90 days" → 90. */
    fun mentionedDays(text: String): Int? {
        val t = normalize(text)
        if (HALF_YEAR.containsMatchIn(t)) return 180
        val m = AMOUNT.find(t) ?: return null
        val amount = amountOf(m.groupValues[1]) ?: return null
        val days = when (m.groupValues[2].first()) {
            'd' -> amount
            'w' -> amount * 7
            'm' -> amount * 30
            else -> amount * 365
        }
        return days.toInt().takeIf { it in 1..3650 }
    }

    /** Duration of a block: "2 hours", "90 minutes", "an hour and a half". */
    fun mentionedDurationMinutes(text: String): Int? {
        val t = normalize(text)
        if (HOUR_AND_HALF.containsMatchIn(t)) return 90
        DURATION.find(t)?.let { m ->
            val amount = amountOf(m.groupValues[1]) ?: return null
            val minutes = if (m.groupValues[2].startsWith("h")) amount * 60 else amount
            return minutes.toInt().takeIf { it in 5..720 }
        }
        return null
    }

    /** A clock time ("5pm", "at 17:30", "noon"). */
    fun mentionedTime(text: String): LocalTime? {
        val t = normalize(text)
        if (Regex("""\bnoon\b""").containsMatchIn(t)) return LocalTime.NOON
        if (Regex("""\bmidnight\b""").containsMatchIn(t)) return LocalTime.MIDNIGHT
        MERIDIEM_TIME.find(t)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (m.groupValues[3] == "pm" && hour < 12) hour += 12
            if (m.groupValues[3] == "am" && hour == 12) hour = 0
            return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
        }
        CLOCK_TIME.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
        }
        return null
    }

    /** "on my errands list" → "Errands" (generic to-do lists → null). */
    fun mentionedList(text: String): String? {
        val m = LIST.find(normalize(text)) ?: return null
        val name = m.groupValues[1].trim()
        if (name in GENERIC_LISTS) return null
        return name.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
    }

    /** "downloaded" / "opened" / "haven't touched" in the request. */
    fun mentionsDownload(text: String) = DOWNLOAD.containsMatchIn(normalize(text))
    fun mentionsOpened(text: String) = OPENED.containsMatchIn(normalize(text))
    fun mentionsUnused(text: String) = UNUSED.containsMatchIn(normalize(text))

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace("a.m.", "am").replace("p.m.", "pm")

    private fun amountOf(word: String): Long? = word.toLongOrNull() ?: WORDS[word.trim()]

    private const val NUM = """(\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|a couple of|couple of|a few|few)"""
    private val AMOUNT = Regex("""\b$NUM\s+(days?|weeks?|months?|years?)\b""")
    private val HALF_YEAR = Regex("""\b(half a year|six months|6 months)\b""")
    private val DURATION = Regex("""\b$NUM\s+(hours?|hrs?|minutes?|mins?)\b""")
    private val HOUR_AND_HALF = Regex("""\b(an?|one) hour and a half\b|\b1\.5 hours?\b""")
    private val MERIDIEM_TIME = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
    private val CLOCK_TIME = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val LIST = Regex("""\b(?:on|to|in|onto|into)\s+(?:my|the|our)\s+([a-z][\w-]*(?:\s+[a-z][\w-]*)?)\s+list\b""")
    private val GENERIC_LISTS = setOf("to-do", "todo", "to do", "task", "tasks", "todos")
    private val DOWNLOAD = Regex("""\bdownload(ed|s)?\b""")
    private val OPENED = Regex("""\b(opened|clicked|accessed|viewed)\b""")
    private val UNUSED = Regex("""\b(haven'?t|have not|didn'?t|not)\s+(touched|used|opened)|\buntouched\b|\bstale\b""")

    private const val WEEKDAY = """(?:mon|tues|wednes|thurs|fri|satur|sun)day"""
    private const val MONTH = """(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"""
    private val DAY_PHRASES = listOf(
        Regex("""\d{4}-\d{1,2}-\d{1,2}"""),
        Regex("""\bday after tomorrow\b"""),
        Regex("""\b(today|tonight|tomorrow|tmrw)\b"""),
        Regex("""\b(?:next|this|on)?\s*$WEEKDAY\b"""),
        Regex("""\b\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH\b"""),
        Regex("""\b$MONTH\s+\d{1,2}(?:st|nd|rd|th)?\b"""),
        Regex("""\bin\s+\d+\s+days?\b"""),
    )
    private val RANGE_PHRASES = listOf(
        Regex("""\bsince\s+(?:last\s+|this\s+)?(?:$WEEKDAY|week|month|year|yesterday|\d{4}-\d{1,2}-\d{1,2}|\d{1,2}(?:st|nd|rd|th)?\s+$MONTH|$MONTH\s+\d{1,2})\b"""),
        Regex("""\b(?:between|from)\s+.+?\s+(?:and|to|until)\s+\S+(?:\s+\d{1,2})?"""),
        Regex("""\b(?:in\s+the\s+|over\s+the\s+|within\s+the\s+)?(?:last|past|previous)\s+$NUM\s+(?:days?|weeks?|months?|years?)\b"""),
        Regex("""\b(?:last|this|previous|past)\s+(?:week|month|year)\b"""),
        Regex("""\b\d+\s+(?:days?|weeks?)\s+ago\b"""),
        Regex("""\b(today|yesterday)\b"""),
        Regex("""\bin\s+$MONTH\b"""),
    )
    private val WORDS = mapOf(
        "a" to 1L, "an" to 1L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L,
        "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L,
        "a couple of" to 2L, "couple of" to 2L, "a few" to 3L, "few" to 3L,
    )
}
