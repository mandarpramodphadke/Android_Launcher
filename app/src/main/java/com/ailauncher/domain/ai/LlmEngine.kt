package com.ailauncher.domain.ai

import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.LlmPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * On-device LLM. The implementation (LocalLlmEngine) loads lazily on the first request, serialises
 * all native calls on one thread, frees RAM after an idle period, and lets foreground requests
 * pre-empt background ones.
 */
interface LlmEngine {
    val state: StateFlow<EngineState>

    /** Context window of the currently configured model (n_ctx). */
    val contextSize: Int

    /**
     * Streams a completion for a fully formatted [prompt]. Cancelling the collector stops native
     * generation (including an in-flight prompt evaluation) within one decode step.
     *
     * @throws com.ailauncher.domain.model.LlmException
     */
    fun generate(
        prompt: String,
        params: GenerationParams,
        priority: LlmPriority = LlmPriority.FOREGROUND,
    ): Flow<GenerationEvent>

    /** Stops the current generation, if any. */
    fun stop()

    /** Loads the model now (e.g. when the chat opens) so the first reply is faster. */
    suspend fun preload(): Result<Unit>

    /** Frees the model and KV cache from RAM immediately. */
    suspend fun unload()

    /** Number of tokens [text] encodes to, or null if the model is not loaded. */
    suspend fun countTokens(text: String): Int?

    /** Called by the Application on `onTrimMemory`. */
    fun onTrimMemory(level: Int)
}
