package com.ailauncher.domain.model

/** Qwen3 dual-mode inference. */
enum class InferenceMode {
    /** `enable_thinking = false` (`/no_think`): low latency, used for tool calls and app sorting. */
    FAST_ACTION,

    /** `enable_thinking = true`: the model reasons inside `<think>…</think>` before answering. */
    REASONING,
}

/** Decoding parameters passed to llama.cpp for one generation. */
data class GenerationParams(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float = 0f,
    val presencePenalty: Float = 0f,
    val repeatPenalty: Float = 1f,
    val maxTokens: Int,
    val seed: Int = -1,
    /** Optional GBNF grammar constraining the output (start rule `root`). */
    val grammar: String? = null,
) {
    companion object {
        /** Qwen3 recommended non-thinking settings: T=0.7, TopP=0.8, TopK=20, MinP=0. */
        fun fastAction(maxTokens: Int = 512, grammar: String? = null) = GenerationParams(
            temperature = 0.7f, topP = 0.8f, topK = 20, minP = 0f, maxTokens = maxTokens, grammar = grammar,
        )

        /**
         * Qwen3 recommended thinking settings: T=0.6, TopP=0.95, TopK=20, MinP=0 (never greedy). A
         * mild presence penalty counters the repetition loops quantised models are prone to.
         */
        fun reasoning(maxTokens: Int = 2048) = GenerationParams(
            temperature = 0.6f, topP = 0.95f, topK = 20, minP = 0f, presencePenalty = 0.5f, maxTokens = maxTokens,
        )

        fun forMode(mode: InferenceMode) = when (mode) {
            InferenceMode.FAST_ACTION -> fastAction()
            InferenceMode.REASONING -> reasoning()
        }
    }
}

enum class StopReason { END_OF_TURN, MAX_TOKENS, CANCELLED, CONTEXT_FULL }

data class GenerationStats(
    val stopReason: StopReason,
    val promptTokens: Int,
    val cachedPromptTokens: Int,
    val generatedTokens: Int,
    val promptEvalMillis: Long,
    val generationMillis: Long,
) {
    val tokensPerSecond: Float
        get() = if (generationMillis <= 0) 0f else generatedTokens * 1000f / generationMillis
}

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Completed(val stats: GenerationStats) : GenerationEvent
}

/** Who is asking: foreground chat requests pre-empt background work (app categorization). */
enum class LlmPriority { FOREGROUND, BACKGROUND }

data class ModelRuntimeInfo(
    val name: String,
    val description: String,
    val contextSize: Int,
    val threads: Int,
    val kvCacheType: String,
    val sizeBytes: Long,
    val parameterCount: Long,
    /** "CPU", or the GPU the layers run on ("GPU · Adreno (TM) 740 · 29 layers"). */
    val accelerator: String = "CPU",
)

/** Lifecycle of the on-device model, observed by the AI drawer status indicator. */
sealed interface EngineState {
    /** The GGUF file has not been downloaded/imported yet. */
    data object NoModel : EngineState

    /** The file is present but not in RAM (cold, or auto-unloaded by the watchdog). */
    data object Unloaded : EngineState

    data class Loading(val progress: Float) : EngineState

    data class Ready(val info: ModelRuntimeInfo, val idleUnloadAtMillis: Long?) : EngineState

    data class Generating(val info: ModelRuntimeInfo) : EngineState

    data class Error(val message: String) : EngineState

    /** The device cannot run the model at all (e.g. native library missing for this ABI). */
    data class Unsupported(val reason: String) : EngineState
}

/** Point-in-time memory/thermal snapshot for the live RAM indicator. */
data class DeviceHealth(
    val processRssBytes: Long,
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val lowMemory: Boolean,
    val thermalStatus: ThermalLevel,
)

enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL }

/** Failures surfaced by the engine; messages are user-presentable. */
sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotAvailable : LlmException("The on-device model has not been downloaded yet.")
    class NativeLibraryUnavailable(cause: Throwable?) :
        LlmException("On-device AI is not available on this device's CPU architecture.", cause)
    class InsufficientMemory(val availableBytes: Long, val requiredBytes: Long) : LlmException(
        "Not enough free memory to load the model (%.1f GB free, ~%.1f GB needed). Close a few apps and try again."
            .format(availableBytes / 1e9, requiredBytes / 1e9),
    )
    class Overheated : LlmException("Your phone is too hot to run the AI model right now. Try again in a few minutes.")
    class LoadFailed(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class GenerationFailed(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class ContextOverflow : LlmException("The conversation is too long for the model's context window.")
    class Preempted : LlmException("Paused to answer a foreground request.")
}
