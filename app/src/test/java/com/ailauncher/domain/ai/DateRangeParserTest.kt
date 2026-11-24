package com.ailauncher.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class DateRangeParserTest {

    /** Tuesday. */
    private val today = LocalDate.of(2026, 11, 24)

    private fun span(text: String) = DateRangeParser.parse(text, today)
    private fun range(start: String, end: String) = DateSpan(LocalDate.parse(start), LocalDate.parse(end))

    @Test
    fun relativeDaysAndWeeks() {
        assertThat(span("today")).isEqualTo(range("2026-11-24", "2026-11-24"))
        assertThat(span("Yesterday")).isEqualTo(range("2026-11-23", "2026-11-23"))
        assertThat(span("this week")).isEqualTo(range("2026-11-23", "2026-11-24"))
        assertThat(span("last week")).isEqualTo(range("2026-11-16", "2026-11-22"))
        assertThat(span("in the last 7 days")).isEqualTo(range("2026-11-18", "2026-11-24"))
        assertThat(span("past two weeks")).isEqualTo(range("2026-11-11", "2026-11-24"))
        assertThat(span("3 days ago")).isEqualTo(range("2026-11-21", "2026-11-21"))
    }

    @Test
    fun monthsAndYears() {
        assertThat(span("this month")).isEqualTo(range("2026-11-01", "2026-11-24"))
        assertThat(span("last month")).isEqualTo(range("2026-10-01", "2026-10-31"))
        assertThat(span("in October")).isEqualTo(range("2026-10-01", "2026-10-31"))
        // A month later in the year than today means last year's.
        assertThat(span("december")).isEqualTo(range("2025-12-01", "2025-12-31"))
        assertThat(span("march 2025")).isEqualTo(range("2025-03-01", "2025-03-31"))
        assertThat(span("this year")).isEqualTo(range("2026-01-01", "2026-11-24"))
    }

    @Test
    fun explicitRangesAndAnchors() {
        assertThat(span("2026-11-01 to 2026-11-05")).isEqualTo(range("2026-11-01", "2026-11-05"))
        assertThat(span("2026-11-05..2026-11-01")).isEqualTo(range("2026-11-01", "2026-11-05"))
        assertThat(span("between nov 3 and nov 9")).isEqualTo(range("2026-11-03", "2026-11-09"))
        // "last monday" is the most recent Monday (yesterday here), like "last friday".
        assertThat(span("since last monday")).isEqualTo(range("2026-11-23", "2026-11-24"))
        assertThat(span("since 2026-11-20")).isEqualTo(range("2026-11-20", "2026-11-24"))
        assertThat(span("on 2026-11-02")).isEqualTo(range("2026-11-02", "2026-11-02"))
        assertThat(span("whenever")).isNull()
    }

    @Test
    fun singleDaysPreferPastOrFuture() {
        assertThat(DateRangeParser.parseDay("tomorrow", today)).isEqualTo(LocalDate.of(2026, 11, 25))
        assertThat(DateRangeParser.parseDay("friday", today, preferFuture = true)).isEqualTo(LocalDate.of(2026, 11, 27))
        assertThat(DateRangeParser.parseDay("friday", today, preferFuture = false)).isEqualTo(LocalDate.of(2026, 11, 20))
        assertThat(DateRangeParser.parseDay("next monday", today)).isEqualTo(LocalDate.of(2026, 11, 30))
        assertThat(DateRangeParser.parseDay("3 nov", today)).isEqualTo(LocalDate.of(2026, 11, 3))
        assertThat(DateRangeParser.parseDay("3rd of january", today, preferFuture = true)).isEqualTo(LocalDate.of(2027, 1, 3))
        assertThat(DateRangeParser.parseDay("2026-12-01T14:00", today)).isEqualTo(LocalDate.of(2026, 12, 1))
        assertThat(DateRangeParser.parseDay("in 2 days", today)).isEqualTo(LocalDate.of(2026, 11, 26))
        assertThat(DateRangeParser.parseDay("blue", today)).isNull()
    }
}
