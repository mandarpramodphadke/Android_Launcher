package com.ailauncher.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class ChatRole { USER, ASSISTANT, TOOL }

enum class MessageStatus { STREAMING, COMPLETE, CANCELLED, ERROR }

/** State machine of an inline tool confirmation card. */
@Serializable
enum class ToolStatus {
    /** Shown to the user with Confirm / Cancel buttons. */
    AWAITING_CONFIRMATION,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** A tool call emitted by the model (or the quick-command fast path) and its outcome. */
@Serializable
data class ToolInvocation(
    val id: String,
    val name: String,
    /** Arguments exactly as the model produced them (re-rendered verbatim into the prompt history). */
    val arguments: JsonObject,
    val status: ToolStatus,
    /** Short human-readable summary for the card ("Alarm · 7:00 AM · Wake up"). */
    val summary: String = "",
    /**
     * Validated, canonical arguments produced by the ToolRouter when the call was received
     * (e.g. a relative "in 30 minutes" resolved to an absolute timestamp), so confirming later
     * does not shift the action. Null when the call was invalid.
     */
    val resolved: JsonObject? = null,
    /** Outcome shown on the card once executed. */
    val resultMessage: String? = null,
    /** Structured result fed back to the model in `<tool_response>`. */
    val resultData: JsonObject? = null,
)

data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val role: ChatRole,
    /** Visible answer text (thinking and tool-call markup removed). */
    val content: String,
    /** Contents of the `<think>` block, shown in the "Thought process" accordion. */
    val thinking: String?,
    val toolCalls: List<ToolInvocation>,
    val status: MessageStatus,
    val mode: InferenceMode?,
    val tokensPerSecond: Float?,
    val createdAt: Long,
)

data class Reminder(
    val id: Long,
    val title: String,
    val triggerAtMillis: Long,
    val calendarEventId: Long?,
    val status: ReminderStatus,
    val createdAt: Long,
)

enum class ReminderStatus { SCHEDULED, FIRED, CANCELLED }

/** A signed-in Google account, or the offline guest profile. */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
    val isGuest: Boolean,
    val firebaseLinked: Boolean,
) {
    companion object {
        val Guest = UserProfile(
            uid = "guest",
            displayName = "Guest",
            email = null,
            photoUrl = null,
            isGuest = true,
            firebaseLinked = false,
        )
    }
}

/** Upcoming system alarm or app reminder shown as the home-screen pill. */
data class UpcomingEvent(
    val kind: Kind,
    val title: String,
    val triggerAtMillis: Long,
) {
    enum class Kind { ALARM, REMINDER }
}
