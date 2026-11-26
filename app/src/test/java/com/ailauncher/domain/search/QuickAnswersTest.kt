package com.ailauncher.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuickAnswersTest {

    private fun answer(q: String) = QuickAnswers.answer(q)?.answer

    @Test
    fun sums() {
        assertThat(answer("2+2")).isEqualTo("4")
        assertThat(answer("(3 + 4) * 2")).isEqualTo("14")
        assertThat(answer("10 / 4")).isEqualTo("2.5")
        assertThat(answer("2^10")).isEqualTo("1024")
        assertThat(answer("12 x 3")).isEqualTo("36")
        assertThat(answer("7 × 6")).isEqualTo("42")
        assertThat(answer("100 ÷ 8")).isEqualTo("12.5")
        assertThat(answer("-5 + 3")).isEqualTo("-2")
        assertThat(answer("1,200 * 2")).isEqualTo("2400")
        assertThat(answer("sqrt 81")).isEqualTo("9")
        assertThat(answer("1/3")).isEqualTo("0.3333333333")
        assertThat(answer("2 + 3 * 4 =")).isEqualTo("14")
    }

    @Test
    fun percentages() {
        assertThat(answer("15% of 2400")).isEqualTo("360")
        assertThat(answer("200 + 10%")).isEqualTo("220")
        assertThat(answer("80 - 25%")).isEqualTo("60")
        assertThat(answer("50 * 10%")).isEqualTo("5")
    }

    @Test
    fun notSums() {
        assertThat(answer("call mum")).isNull()
        assertThat(answer("2024")).isNull()
        assertThat(answer("set alarm for 7")).isNull()
        assertThat(answer("whatsapp")).isNull()
        assertThat(answer("1/0")).isNull()
        assertThat(answer("2 +")).isNull()
        assertThat(answer("box 2+2")).isNull()
    }

    @Test
    fun conversions() {
        assertThat(answer("5 km in miles")).isEqualTo("3.10686 mi")
        assertThat(answer("72f to c")).isEqualTo("22.2222 °C")
        assertThat(answer("100 c in f")).isEqualTo("212 °F")
        assertThat(answer("2 kg to lb")).isEqualTo("4.40925 lb")
        assertThat(answer("1.5 l in cups")).isEqualTo("6.34014 cup")
        assertThat(answer("500 mb to gb")).isEqualTo("0.5 GB")
        assertThat(answer("100 mph in km/h")).isEqualTo("160.934 km/h")
        assertThat(answer("6 ft in cm")).isEqualTo("182.88 cm")
        assertThat(answer("90 minutes in hours")).isEqualTo("1.5 h")
        assertThat(answer("convert 3 fl oz to ml")).isEqualTo("88.7205 ml")
        assertThat(answer("5,5 km in m")).isEqualTo("5500 m")
    }

    @Test
    fun conversionsBetweenDifferentKindsAreRefused() {
        assertThat(answer("5 km in kg")).isNull()
        assertThat(answer("5 parsecs in km")).isNull()
        assertThat(answer("5 km in km")).isNull()
    }

    @Test
    fun conversionKeepsANumberToCopy() {
        val result = QuickAnswers.answer("5 km in m")!!
        assertThat(result.copyText).isEqualTo("5000")
        assertThat(result.question).isEqualTo("5 km")
    }

    @Test
    fun settingsPages() {
        assertThat(SettingsShortcuts.search("wifi").map { it.title }).contains("Wi-Fi")
        assertThat(SettingsShortcuts.search("blue").first().title).isEqualTo("Bluetooth")
        assertThat(SettingsShortcuts.search("dark mode").first().title).isEqualTo("Display")
        assertThat(SettingsShortcuts.search("battery").first().title).isEqualTo("Battery")
        assertThat(SettingsShortcuts.search("wi")).isEmpty()
        assertThat(SettingsShortcuts.search("spotify")).isEmpty()
    }
}
