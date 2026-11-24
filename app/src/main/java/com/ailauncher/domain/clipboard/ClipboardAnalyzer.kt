package com.ailauncher.domain.clipboard

import com.ailauncher.domain.notifications.NotificationSmartExtractor

enum class ClipKind { URL, PHONE, EMAIL, CODE, TEXT }

/** Contextual chips offered above the command bar for what's on the clipboard. */
enum class ClipAction(val label: String) {
    OPEN_LINK("Open link"),
    CALL("Call"),
    EMAIL("Email"),
    COPY_CODE("Use code"),
    SUMMARIZE("Summarize"),
    EXTRACT_TASKS("Extract tasks"),
    ADD_TODO("Add to to-do"),
    ASK("Ask about it"),
}

data class ClipInsight(
    val text: String,
    val kind: ClipKind,
    val actions: List<ClipAction>,
) {
    /** Single-line preview for the chip row. */
    val preview: String get() = text.replace(Regex("""\s+"""), " ").take(48) + if (text.length > 48) "…" else ""
}

/**
 * Classifies clipboard text so the command bar can suggest one-tap actions. Runs only when the
 * user focuses the command bar, and never sends anything anywhere: summarizing and task
 * extraction go to the on-device model.
 */
object ClipboardAnalyzer {

    fun analyze(raw: String?): ClipInsight? {
        val text = raw?.trim()?.take(MAX_CHARS) ?: return null
        if (text.isEmpty()) return null
        val singleLine = '\n' !in text
        return when {
            singleLine && URL.matches(text) -> ClipInsight(text, ClipKind.URL, listOf(ClipAction.OPEN_LINK))
            singleLine && EMAIL.matches(text) -> ClipInsight(text, ClipKind.EMAIL, listOf(ClipAction.EMAIL))
            singleLine && PHONE.matches(text) && text.count(Char::isDigit) in 6..15 ->
                ClipInsight(text, ClipKind.PHONE, listOf(ClipAction.CALL))
            singleLine && CODE.matches(text) -> ClipInsight(text, ClipKind.CODE, listOf(ClipAction.COPY_CODE))
            text.length >= LONG_TEXT || !singleLine -> ClipInsight(
                text,
                ClipKind.TEXT,
                buildList {
                    add(ClipAction.SUMMARIZE)
                    add(ClipAction.EXTRACT_TASKS)
                    if (NotificationSmartExtractor.findOtp(text) != null) add(0, ClipAction.COPY_CODE)
                },
            )
            else -> ClipInsight(text, ClipKind.TEXT, listOf(ClipAction.ADD_TODO, ClipAction.ASK))
        }
    }

    /** The prompt sent to the assistant for an AI chip ([text] is truncated to fit the context). */
    fun promptFor(action: ClipAction, text: String, maxChars: Int = PROMPT_CHARS): String? {
        val body = if (text.length > maxChars) text.take(maxChars) + "\n[…truncated]" else text
        return when (action) {
            ClipAction.SUMMARIZE -> "Summarize this in a few bullet points:\n\n$body"
            ClipAction.EXTRACT_TASKS -> "List the action items in this text, then add each one with manage_todo:\n\n$body"
            ClipAction.ADD_TODO -> "Add a to-do: ${body.take(120)}"
            ClipAction.ASK -> "$body\n\nWhat is this about?"
            else -> null
        }
    }

    private const val MAX_CHARS = 20_000
    private const val LONG_TEXT = 120
    const val PROMPT_CHARS = 3_000

    private val URL = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("""^[\w.+-]+@[\w-]+(\.[\w-]+)+$""")
    private val PHONE = Regex("""^\+?[\d\s().-]{6,20}$""")
    private val CODE = Regex("""^[A-Z]?-?\d{4,8}$""", RegexOption.IGNORE_CASE)
}
