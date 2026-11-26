package com.ailauncher.domain.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SPOKEN_REPLIES (1.8): what the voice reads, and the "this message was spoken" marker. */
class SpeechTextTest {

    @Test
    fun markdownBecomesSentences() {
        val reply = """
            ## Day 1
            - **Breakfast:** oatmeal with *fresh* berries
            - Lunch: see [this recipe](https://example.com/soup)
            1. Dinner: `lentil` soup

            ```kotlin
            println("hi")
            ```
            Visit https://example.com for more!
        """.trimIndent()
        assertThat(SpeechText.from(reply)).isEqualTo(
            "Day 1. Breakfast: oatmeal with fresh berries. Lunch: see this recipe. Dinner: lentil soup. " +
                "(The code is on the screen.) Visit a link for more!",
        )
    }

    @Test
    fun tablesAndPlainTextStayReadable() {
        assertThat(SpeechText.from("| Model | Size |\n|---|---|\n| 4B | 2.5 GB |")).isEqualTo("Model, Size. 4B, 2.5 GB.")
        assertThat(SpeechText.from("Alarm set for 6:45 AM")).isEqualTo("Alarm set for 6:45 AM.")
        assertThat(SpeechText.from("It's 5 snake_case_words, not emphasis.")).isEqualTo("It's 5 snake_case_words, not emphasis.")
        assertThat(SpeechText.from("   \n  ")).isEmpty()
    }

    @Test
    fun longRepliesAreSplitBetweenSentences() {
        val text = (1..40).joinToString(" ") { "This is sentence number $it." }
        val chunks = SpeechText.chunks(text, maxLength = 120)
        assertThat(chunks.all { it.length <= 120 }).isTrue()
        assertThat(chunks.all { it.endsWith(".") }).isTrue()
        assertThat(chunks.joinToString(" ")).isEqualTo(text)
        // One sentence longer than a chunk is cut between words.
        val long = "word ".repeat(100).trim()
        assertThat(SpeechText.chunks(long, maxLength = 50).all { it.length <= 50 && !it.startsWith(" ") }).isTrue()
        assertThat(SpeechText.chunks("")).isEmpty()
    }

    @Test
    fun aSpokenPromptIsRecognisedOnceAndOnlyBriefly() {
        val prompts = VoicePrompts()
        prompts.mark("what's the weather like ", now = 1_000)
        assertThat(prompts.consume("Something else", now = 2_000)).isFalse()
        assertThat(prompts.consume("what's the weather like", now = 2_000)).isTrue()
        assertThat(prompts.consume("what's the weather like", now = 2_000)).isFalse()
        prompts.mark("set a timer", now = 0)
        assertThat(prompts.consume("set a timer", now = 120_000)).isFalse()
    }
}
