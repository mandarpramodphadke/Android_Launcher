package com.ailauncher.domain.model

/**
 * Something the user asked the assistant to remember ("my car is on level 3"). Stored on the
 * phone only and shown to the model in its system prompt (ASSISTANT_MEMORY).
 */
data class Memory(val id: Long, val text: String, val createdAt: Long) {
    companion object {
        /** Longest single memory, in characters. */
        const val MAX_LENGTH = 200

        /** Most memories kept; saving one more drops the oldest. */
        const val MAX_COUNT = 50
    }
}
