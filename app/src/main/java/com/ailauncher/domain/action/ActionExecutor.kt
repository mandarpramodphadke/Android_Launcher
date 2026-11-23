package com.ailauncher.domain.action

import com.ailauncher.domain.ai.ToolCommand
import kotlinx.serialization.json.JsonObject

/** Outcome of running a [ToolCommand] against the Android system. */
data class ActionResult(
    val success: Boolean,
    /** Short, user-facing message shown on the tool card. */
    val message: String,
    /** Structured result returned to the model inside `<tool_response>`. */
    val data: JsonObject? = null,
) {
    companion object {
        fun ok(message: String, data: JsonObject? = null) = ActionResult(true, message, data)
        fun fail(message: String) = ActionResult(false, message)
    }
}

/**
 * Executes validated tool commands (alarms, timers, reminders, app launches, device settings).
 * Implemented in the data layer with Android system APIs.
 */
interface ActionExecutor {
    suspend fun execute(command: ToolCommand): ActionResult
}
