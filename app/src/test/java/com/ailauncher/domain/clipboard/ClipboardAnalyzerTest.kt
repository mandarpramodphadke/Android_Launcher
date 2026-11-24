package com.ailauncher.domain.clipboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClipboardAnalyzerTest {

    @Test
    fun classifiesClipboardContent() {
        assertThat(ClipboardAnalyzer.analyze("https://example.com/article?id=3")?.kind).isEqualTo(ClipKind.URL)
        assertThat(ClipboardAnalyzer.analyze("+1 (555) 123-4567")?.actions).containsExactly(ClipAction.CALL)
        assertThat(ClipboardAnalyzer.analyze("alex@example.com")?.kind).isEqualTo(ClipKind.EMAIL)
        assertThat(ClipboardAnalyzer.analyze("G-834221")?.kind).isEqualTo(ClipKind.CODE)
        assertThat(ClipboardAnalyzer.analyze("buy oat milk")?.actions).containsExactly(ClipAction.ADD_TODO, ClipAction.ASK).inOrder()
        val long = "Notes from the meeting:\n- Sam sends the deck by Friday\n- I book the venue"
        assertThat(ClipboardAnalyzer.analyze(long)?.actions).containsExactly(ClipAction.SUMMARIZE, ClipAction.EXTRACT_TASKS).inOrder()
        assertThat(ClipboardAnalyzer.analyze("   ")).isNull()
    }

    @Test
    fun promptsAreTruncatedToFitTheContext() {
        val prompt = ClipboardAnalyzer.promptFor(ClipAction.SUMMARIZE, "x".repeat(10_000), maxChars = 100)!!
        assertThat(prompt).endsWith("[…truncated]")
        assertThat(prompt.length).isLessThan(200)
        assertThat(ClipboardAnalyzer.promptFor(ClipAction.OPEN_LINK, "https://a.b")).isNull()
    }
}
