package com.ailauncher.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FuzzyMatcherTest {

    private val apps = listOf("WhatsApp", "Google Maps", "Spotify", "Settings", "Calculator", "Camera", "Messages", "Gmail", "Café Finder")

    private fun top(query: String) = FuzzyMatcher.rank(query, apps) { listOf(it) }.firstOrNull()?.first

    @Test
    fun ranksPrefixWordPrefixAcronymSubsequenceAndTypos() {
        assertThat(top("spot")).isEqualTo("Spotify")
        assertThat(top("maps")).isEqualTo("Google Maps")
        assertThat(top("gm")).isEqualTo("Gmail") // prefix beats acronym of Google Maps
        assertThat(top("wapp")).isEqualTo("WhatsApp")
        assertThat(top("spotfy")).isEqualTo("Spotify")
        assertThat(top("cafe")).isEqualTo("Café Finder") // diacritics are ignored
        assertThat(top("zzz")).isNull()
    }

    @Test
    fun exactMatchesAreConfident() {
        assertThat(FuzzyMatcher.bestScore("camera", listOf("Camera"))).isAtLeast(FuzzyMatcher.CONFIDENT_MATCH)
        assertThat(FuzzyMatcher.bestScore("spotify", listOf("Spotify"))).isAtLeast(FuzzyMatcher.CONFIDENT_MATCH)
        assertThat(FuzzyMatcher.bestScore("notes", listOf("Camera"))).isLessThan(FuzzyMatcher.CONFIDENT_MATCH)
    }

    @Test
    fun normalizeKeepsNonLatinLetters() {
        assertThat(FuzzyMatcher.normalize("  Ünïcödé — App!  ")).isEqualTo("unicode app")
        assertThat(FuzzyMatcher.normalize("फ़ोन")).isNotEmpty()
    }
}
