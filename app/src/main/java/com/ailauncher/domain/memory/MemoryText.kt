package com.ailauncher.domain.memory

import com.ailauncher.domain.model.Memory
import java.util.Locale

/** Pure rules for what is saved as a memory and which memories a "forget …" means. */
object MemoryText {

    /** Chat-template markup can't be smuggled into the system prompt through a memory. */
    private val TEMPLATE_TOKENS = Regex("""<\|[^|>]*\|>|</?(?:tool_call|tool_response|tools|think)>""")
    private val SPACES = Regex("""\s+""")
    private val WORD = Regex("""[\p{L}\p{N}']+""")
    private val EVERYTHING = Regex("""^(?:everything|all|all of it|all memories|all my memories|everything you know(?: about me)?|everything you remember)$""")

    /** Words too common to say which memory is meant. */
    private val STOPWORDS = setOf(
        "the", "a", "an", "my", "me", "i", "is", "are", "was", "that", "this", "it", "to", "of", "on", "in", "at", "and",
        "about", "for", "with", "you", "your", "what", "where", "when", "has", "have", "be", "do", "did",
    )

    /** The text to store: one line, no template markup, at most [Memory.MAX_LENGTH]; null if empty. */
    fun clean(text: String): String? {
        val cleaned = text.replace(TEMPLATE_TOKENS, " ").replace(SPACES, " ").trim().trimEnd('.', '!', ' ')
        if (cleaned.isEmpty()) return null
        return cleaned.take(Memory.MAX_LENGTH).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    /** "Forget everything", "forget all of it". */
    fun meansEverything(query: String): Boolean = EVERYTHING.matches(query.trim().trimEnd('.', '!').lowercase(Locale.ROOT))

    /**
     * Memories a "forget [query]" is about: the ones containing the query, or else those sharing
     * every meaningful word of it ("forget my car" → "My car is on level 3").
     */
    fun matching(memories: List<Memory>, query: String): List<Memory> {
        val q = query.trim().trimEnd('.', '!').lowercase(Locale.ROOT)
        // "Forget my" or "forget it" says nothing about which memory.
        val wanted = words(q)
        if (wanted.isEmpty()) return emptyList()
        memories.filter { it.text.lowercase(Locale.ROOT).contains(q) }.takeIf { it.isNotEmpty() }?.let { return it }
        return memories.filter { words(it.text.lowercase(Locale.ROOT)).containsAll(wanted) }
    }

    /** The memories as the model sees them: newest first, within [maxChars]. */
    fun forPrompt(memories: List<Memory>, maxChars: Int = PROMPT_CHARS): List<String> {
        var used = 0
        return memories.sortedByDescending { it.createdAt }.map { it.text }.takeWhile { text ->
            used += text.length + 3
            used <= maxChars
        }
    }

    private fun words(text: String): Set<String> =
        WORD.findAll(text).map { it.value.removeSuffix("'s") }.filter { it.length >= 3 && it !in STOPWORDS }.toSet()

    /** About 400 tokens: room for the 15 to 30 memories people actually keep. */
    const val PROMPT_CHARS = 1600
}
