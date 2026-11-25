package com.ailauncher.domain.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IcsParserTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun ics(vararg events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\n" +
        "BEGIN:VTIMEZONE\r\nTZID:India Standard Time\r\nBEGIN:STANDARD\r\nDTSTART:16010101T000000\r\nTZOFFSETFROM:+0530\r\nTZOFFSETTO:+0530\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\n" +
        events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    private fun millis(text: String, zone: ZoneId = kolkata) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun window(from: String, to: String) = LocalDate.parse(from).atStartOfDay(kolkata).toInstant() to LocalDate.parse(to).atStartOfDay(kolkata).toInstant()

    private fun occurrences(text: String, from: String = "2026-11-01", to: String = "2027-03-01"): List<CalendarOccurrence> {
        val (a, b) = window(from, to)
        return IcsParser.occurrences(IcsParser.parse(text), a, b, kolkata)
    }

    @Test
    fun readsOutlookStyleEventsWithZonesFoldingAndEscapes() {
        val text = ics(
            "UID:1\r\nSUMMARY:Team sync\\, weekly\r\nLOCATION:Room 4\\nFloor 2\r\nDTSTART;TZID=India Standard Time:20261125T100000\r\nDTEND;TZID=India Standard Time:20261125T103000",
            "UID:2\r\nSUMMARY:Call with the London off\r\n ice\r\nDTSTART:20261126T093000Z\r\nDTEND:20261126T100000Z\r\nBEGIN:VALARM\r\nTRIGGER:-PT15M\r\nACTION:DISPLAY\r\nEND:VALARM",
            "UID:3\r\nSUMMARY:Diwali holiday\r\nDTSTART;VALUE=DATE:20261108\r\nDTEND;VALUE=DATE:20261109",
            "UID:4\r\nSUMMARY:Focus\r\nDTSTART;TZID=Europe/Berlin:20261127T090000\r\nDURATION:PT2H",
            "UID:5\r\nSUMMARY:Cancelled meeting\r\nSTATUS:CANCELLED\r\nDTSTART:20261128T090000Z",
        )
        val list = occurrences(text)
        assertThat(list.map { it.title }).containsExactly("Diwali holiday", "Team sync, weekly", "Call with the London office", "Focus").inOrder()
        val sync = list.single { it.title.startsWith("Team") }
        assertThat(sync.startMillis).isEqualTo(millis("2026-11-25T10:00"))
        assertThat(sync.endMillis).isEqualTo(millis("2026-11-25T10:30"))
        assertThat(sync.location).isEqualTo("Room 4\nFloor 2")
        assertThat(list.single { it.title.startsWith("Call") }.startMillis).isEqualTo(Instant.parse("2026-11-26T09:30:00Z").toEpochMilli())
        val holiday = list.single { it.allDay }
        assertThat(holiday.startMillis).isEqualTo(millis("2026-11-08T00:00"))
        assertThat(holiday.endMillis).isEqualTo(millis("2026-11-09T00:00"))
        val focus = list.single { it.title == "Focus" }
        assertThat(focus.endMillis - focus.startMillis).isEqualTo(2 * 3_600_000L)
        assertThat(focus.startMillis).isEqualTo(millis("2026-11-27T09:00", ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun expandsWeeklyRulesWithExceptionsAndMovedOccurrences() {
        val text = ics(
            "UID:standup\r\nSUMMARY:Stand-up\r\nDTSTART;TZID=Asia/Kolkata:20261102T091500\r\nDTEND;TZID=Asia/Kolkata:20261102T093000\r\n" +
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=8\r\nEXDATE;TZID=Asia/Kolkata:20261104T091500",
            // The Friday 6 Nov stand-up moved to 11:00.
            "UID:standup\r\nRECURRENCE-ID;TZID=Asia/Kolkata:20261106T091500\r\nSUMMARY:Stand-up (moved)\r\nDTSTART;TZID=Asia/Kolkata:20261106T110000\r\nDTEND;TZID=Asia/Kolkata:20261106T111500",
        )
        val list = occurrences(text)
        val starts = list.map { Instant.ofEpochMilli(it.startMillis).atZone(kolkata).toLocalDateTime().toString() }
        // 8 occurrences from 2 Nov: Mon 2, (Wed 4 excluded), Fri 6 moved to 11:00, Mon 9, Wed 11, Fri 13, Mon 16, Wed 18.
        assertThat(starts).containsExactly(
            "2026-11-02T09:15", "2026-11-06T11:00", "2026-11-09T09:15", "2026-11-11T09:15",
            "2026-11-13T09:15", "2026-11-16T09:15", "2026-11-18T09:15",
        ).inOrder()
        assertThat(list.single { it.startMillis == millis("2026-11-06T11:00") }.title).isEqualTo("Stand-up (moved)")
    }

    @Test
    fun expandsMonthlyAndYearlyRules() {
        val text = ics(
            "UID:a\r\nSUMMARY:Rent\r\nDTSTART;VALUE=DATE:20261031\r\nRRULE:FREQ=MONTHLY",
            "UID:b\r\nSUMMARY:Last-Friday drinks\r\nDTSTART:20261127T120000Z\r\nDTEND:20261127T140000Z\r\nRRULE:FREQ=MONTHLY;BYDAY=-1FR;UNTIL=20270131T235959Z",
            "UID:c\r\nSUMMARY:Thanksgiving\r\nDTSTART;VALUE=DATE:20251127\r\nRRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=4TH",
            "UID:d\r\nSUMMARY:Every other day\r\nDTSTART;TZID=Asia/Kolkata:20261201T070000\r\nRRULE:FREQ=DAILY;INTERVAL=2;COUNT=3",
        )
        val list = occurrences(text, from = "2026-11-01", to = "2027-03-01")
        fun days(title: String) = list.filter { it.title == title }.map { Instant.ofEpochMilli(it.startMillis).atZone(kolkata).toLocalDate().toString() }
        // The 31st: months without one are skipped (RFC 5545).
        assertThat(days("Rent")).containsExactly("2026-12-31", "2027-01-31").inOrder()
        assertThat(days("Last-Friday drinks")).containsExactly("2026-11-27", "2026-12-25", "2027-01-29").inOrder()
        assertThat(days("Thanksgiving")).containsExactly("2026-11-26")
        assertThat(days("Every other day")).containsExactly("2026-12-01", "2026-12-03", "2026-12-05").inOrder()
    }

    @Test
    fun resolvesTimeZoneNames() {
        assertThat(TimeZones.resolve("India Standard Time")).isEqualTo(kolkata)
        assertThat(TimeZones.resolve("\"Europe/London\"")).isEqualTo(ZoneId.of("Europe/London"))
        assertThat(TimeZones.resolve("/mozilla.org/20050126_1/America/New_York")).isEqualTo(ZoneId.of("America/New_York"))
        assertThat(TimeZones.resolve("Nowhere Standard Time")).isNull()
    }

    @Test
    fun brokenInputNeverThrows() {
        assertThat(IcsParser.parse("")).isEmpty()
        assertThat(IcsParser.parse("BEGIN:VEVENT\nSUMMARY:no start\nEND:VEVENT\nBEGIN:VEVENT\nDTSTART:garbage\nEND:VEVENT")).isEmpty()
        val endless = ics("UID:x\r\nSUMMARY:Daily\r\nDTSTART:20200101T080000Z\r\nRRULE:FREQ=DAILY")
        assertThat(occurrences(endless, from = "2026-11-01", to = "2026-11-08")).hasSize(7)
    }
}
