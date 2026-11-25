package com.ailauncher.domain.model

/** Why the user reported an assistant reply (Google Play's AI-generated content policy). */
enum class ReportReason(val label: String) {
    OFFENSIVE("Offensive or rude"),
    HARMFUL("Harmful or dangerous"),
    SEXUAL("Sexual content"),
    HATEFUL("Hateful or discriminatory"),
    MISLEADING("Wrong or misleading"),
    OTHER("Something else"),
}

/** A report the user chose to send: only what is listed here leaves the phone. */
data class ContentReport(
    val messageId: Long,
    val reason: ReportReason,
    val details: String?,
    /** The user's question; null when they chose not to include it. */
    val question: String?,
    val reply: String,
    /** e.g. "AI Launcher 1.3.0 (play) · qwen3-1.7b-q4_k_m". */
    val appInfo: String,
)

sealed interface ReportOutcome {
    /** Will be sent in the background (now, or once the phone is online). */
    data object Queued : ReportOutcome

    /** No in-app channel is configured: the UI opens an email draft with these fields. */
    data class Email(val to: String, val subject: String, val body: String) : ReportOutcome

    /** No channel configured at all (developer builds): the reply is only hidden on this phone. */
    data object SavedOnly : ReportOutcome
}
