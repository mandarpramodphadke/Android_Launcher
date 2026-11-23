package com.ailauncher.domain.usecase

import com.ailauncher.domain.ai.SystemPrompts
import com.ailauncher.domain.model.AppCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategorizationTest {

    @Test
    fun parsesGrammarConstrainedOutput() {
        // Verbatim (grammar-constrained) output of Qwen3-1.7B in the host harness.
        val output = """
            {
             "com.whatsapp": "Communication",
             "com.spotify.music": "Media",
             "com.phonepe.app": "Finance",
             "com.unknown.app": "Games",
             "com.duolingo": "NotACategory"
            }
        """.trimIndent()
        val parsed = CategorizeAppsUseCase.parse(
            output,
            setOf("com.whatsapp", "com.spotify.music", "com.phonepe.app", "com.duolingo"),
        )
        assertThat(parsed).containsExactly(
            "com.whatsapp", AppCategory.COMMUNICATION,
            "com.spotify.music", AppCategory.MEDIA,
            "com.phonepe.app", AppCategory.FINANCE,
        )
    }

    @Test
    fun toleratesSurroundingTextAndGarbage() {
        assertThat(CategorizeAppsUseCase.parse("Sure! {\"a.b\": \"Utilities\"} done", setOf("a.b")))
            .containsExactly("a.b", AppCategory.UTILITIES)
        assertThat(CategorizeAppsUseCase.parse("no json here", setOf("a.b"))).isEmpty()
    }

    @Test
    fun grammarListsExactlyTheBatchAndTaxonomy() {
        val grammar = SystemPrompts.categorizerGrammar(listOf("com.whatsapp", "org.example_app"))
        assertThat(grammar).contains("""key ::= "\"com.whatsapp\"" | "\"org.example_app\""""")
        AppCategory.entries.forEach { assertThat(grammar).contains("\"\\\"${it.displayName}\\\"\"") }
        assertThat(grammar.lines().first()).startsWith("root ::=")
    }

    @Test
    fun heuristicsCoverCommonAppsAndStayQuietWhenUnsure() {
        assertThat(HeuristicCategorizer.categorize("com.whatsapp", "WhatsApp")).isEqualTo(AppCategory.COMMUNICATION)
        assertThat(HeuristicCategorizer.categorize("com.example.mybank", "My Bank")).isEqualTo(AppCategory.FINANCE)
        assertThat(HeuristicCategorizer.categorize("com.example.puzzlegame", "Puzzle Quest Game")).isEqualTo(AppCategory.GAMES)
        assertThat(HeuristicCategorizer.categorize("com.acme.zorbo", "Zorbo")).isNull()
    }

    @Test
    fun categoryLookupIsLenient() {
        assertThat(AppCategory.fromName(" media ")).isEqualTo(AppCategory.MEDIA)
        assertThat(AppCategory.fromName("\"Travel\"")).isEqualTo(AppCategory.TRAVEL)
        assertThat(AppCategory.fromName("HEALTH")).isEqualTo(AppCategory.HEALTH)
        assertThat(AppCategory.fromName("Cooking")).isNull()
    }
}
