package com.ailauncher.presentation.home

import com.ailauncher.domain.model.BatteryGlance
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

class ClockFacesTest {

    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 26, h, m, 0, 0, ZoneOffset.UTC)

    @Test
    fun timeInWords() {
        assertThat(timeInWords(at(16, 37))).isEqualTo("Four" to "thirty-seven")
        assertThat(timeInWords(at(0, 0))).isEqualTo("Twelve" to "o'clock")
        assertThat(timeInWords(at(9, 5))).isEqualTo("Nine" to "oh five")
        assertThat(timeInWords(at(12, 15))).isEqualTo("Twelve" to "fifteen")
        assertThat(timeInWords(at(23, 40))).isEqualTo("Eleven" to "forty")
    }

    @Test
    fun dateLineAddsTheBattery() {
        val now = at(9, 0)
        assertThat(dateLine(now, Locale.UK, null)).isEqualTo("Thursday, 26 November")
        assertThat(dateLine(now, Locale.UK, BatteryGlance(82, charging = false))).isEqualTo("Thursday, 26 November · 82%")
        assertThat(dateLine(now, Locale.UK, BatteryGlance(40, charging = true))).isEqualTo("Thursday, 26 November · 40% charging")
    }
}
