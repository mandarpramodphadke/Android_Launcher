package com.ailauncher.data.llm

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.GenerationStats
import com.ailauncher.domain.model.LlmException
import com.ailauncher.domain.model.LlmPriority
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ModelRuntimeInfo
import com.ailauncher.domain.model.StopReason
import com.ailauncher.domain.model.ThermalLevel
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production wrapper around the llama.cpp JNI bridge.
 *
 *  * **Lazy loading** - the GGUF is memory-mapped on the first request (or [preload]).
 *  * **Single inference thread** - every native call runs on one dedicated thread with a large
 *    stack; llama.cpp contexts are not thread-safe.
 *  * **Auto-unload watchdog** - after `autoUnloadMinutes` (default 3) without requests the model
 *    and KV cache are freed, so the launcher does not become a large Low-Memory-Killer target
 *    while the user is in other apps. `onTrimMemory` frees it immediately under pressure.
 *  * **RAM / thermal guards** - refuses to load without enough free memory and throttles the
 *    thread count when the SoC is hot or in battery saver.
 *  * **Priorities** - a foreground (chat) request pre-empts background work (app sorting),
 *    which receives [LlmException.Preempted] and retries later.
 */
@Singleton
class LocalLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val deviceResources: DeviceResources,
    @ApplicationScope private val scope: CoroutineScope,
) : LlmEngine {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(null, runnable, "llama-inference", NATIVE_STACK_BYTES).apply { isDaemon = true }
    }
    private val inferenceDispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile private var handle = 0L
    @Volatile private var loadedConfig: ModelConfig? = null
    @Volatile private var runtimeInfo: ModelRuntimeInfo? = null
    @Volatile private var configuredContext = ModelConfig().resolvedContextSize(deviceResources.memoryInfo().totalMem)
    @Volatile private var autoUnloadMinutes = ModelConfig().autoUnloadMinutes
    @Volatile private var watchdog: Job? = null

    private val pendingForeground = AtomicInteger(0)
    @Volatile private var backgroundRunning = false
    @Volatile private var preempted = false

    override val contextSize: Int get() = loadedConfig?.effectiveContext() ?: configuredContext

    init {
        scope.launch {
            settingsRepository.modelConfig.collect { config ->
                configuredContext = config.effectiveContext()
                if (autoUnloadMinutes != config.autoUnloadMinutes) {
                    autoUnloadMinutes = config.autoUnloadMinutes
                    if (handle != 0L && !mutex.isLocked) scheduleWatchdog()
                }
            }
        }
        scope.launch {
            modelRepository.fileState.collect { file ->
                if (handle == 0L && !mutex.isLocked) {
                    _state.value = if (file is ModelFileState.Ready) EngineState.Unloaded else EngineState.NoModel
                }
                if (file is ModelFileState.Missing && handle != 0L) unload()
            }
        }
    }

    // ------------------------------------------------------------------------------------------ //
    //  Generation
    // ------------------------------------------------------------------------------------------ //

    override fun generate(
        prompt: String,
        params: GenerationParams,
        priority: LlmPriority,
    ): Flow<GenerationEvent> = channelFlow {
        val foreground = priority == LlmPriority.FOREGROUND
        if (foreground) {
            pendingForeground.incrementAndGet()
            if (backgroundRunning) {
                preempted = true
                LlamaNative.stopGeneration()
            }
        }
        var waiting = foreground
        try {
            mutex.withLock {
                if (waiting) {
                    pendingForeground.decrementAndGet()
                    waiting = false
                }
                if (!foreground && pendingForeground.get() > 0) throw LlmException.Preempted()
                cancelWatchdog()

                val engine = ensureLoadedLocked()
                // Loading takes seconds: if the user asked something meanwhile, let them go first.
                if (!foreground && pendingForeground.get() > 0) {
                    scheduleWatchdog()
                    throw LlmException.Preempted()
                }
                val thermal = deviceResources.thermalLevel()
                if (thermal == ThermalLevel.CRITICAL || (!foreground && thermal >= ThermalLevel.SEVERE)) {
                    scheduleWatchdog()
                    throw LlmException.Overheated()
                }
                val threads = deviceResources.recommendedThreads(loadedConfig?.threads ?: 0)
                val info = runtimeInfo?.copy(threads = threads)
                info?.let { _state.value = EngineState.Generating(it) }
                if (!foreground) {
                    backgroundRunning = true
                    preempted = false
                }
                try {
                    val raw = runNativeCancellable {
                        LlamaNative.setThreads(engine, threads)
                        LlamaNative.generateStream(
                            handle = engine,
                            prompt = prompt.toByteArray(Charsets.UTF_8),
                            temperature = params.temperature,
                            topP = params.topP,
                            topK = params.topK,
                            minP = params.minP,
                            presencePenalty = params.presencePenalty,
                            repeatPenalty = params.repeatPenalty,
                            maxTokens = params.maxTokens,
                            seed = params.seed,
                            grammar = params.grammar?.toByteArray(Charsets.UTF_8),
                            callback = { bytes -> trySend(GenerationEvent.Token(String(bytes, Charsets.UTF_8))).isSuccess },
                        )
                    }
                    val stats = raw.toStats()
                    if (!foreground && preempted && stats.stopReason == StopReason.CANCELLED) throw LlmException.Preempted()
                    if (stats.stopReason == StopReason.CONTEXT_FULL && stats.generatedTokens == 0) throw LlmException.ContextOverflow()
                    send(GenerationEvent.Completed(stats))
                } catch (e: IllegalStateException) {
                    throw LlmException.GenerationFailed(e.message ?: "Generation failed", e)
                } finally {
                    if (!foreground) backgroundRunning = false
                    scheduleWatchdog()
                }
            }
        } finally {
            if (waiting) pendingForeground.decrementAndGet()
        }
    }.buffer(Channel.UNLIMITED)

    override fun stop() {
        if (LlamaNative.isInitialized) LlamaNative.stopGeneration()
    }

    override suspend fun preload(): Result<Unit> = runCatching {
        mutex.withLock {
            ensureLoadedLocked()
            scheduleWatchdog()
        }
    }

    override suspend fun unload() {
        mutex.withLock { unloadLocked() }
    }

    override suspend fun countTokens(text: String): Int? {
        val engine = handle.takeIf { it != 0L } ?: return null
        return withContext(inferenceDispatcher) {
            if (handle == engine) LlamaNative.countTokens(engine, text.toByteArray(Charsets.UTF_8)).takeIf { it >= 0 } else null
        }
    }

    override fun onTrimMemory(level: Int) {
        if (handle == 0L) return
        @Suppress("DEPRECATION")
        val background = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        val hiddenAndLow = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && deviceResources.memoryInfo().lowMemory
        if (background || hiddenAndLow) {
            scope.launch {
                if (mutex.tryLock()) {
                    try {
                        Log.i(TAG, "Freeing model on memory pressure (level=$level)")
                        unloadLocked()
                    } finally {
                        mutex.unlock()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ //
    //  Loading
    // ------------------------------------------------------------------------------------------ //

    private suspend fun ensureLoadedLocked(): Long {
        val config = settingsRepository.modelConfig.first()
        val current = loadedConfig
        if (handle != 0L && current != null) {
            if (!current.requiresReload(config)) {
                loadedConfig = config
                return handle
            }
            unloadLocked()
        }

        try {
            LlamaNative.initialize(context.applicationInfo.nativeLibraryDir)
        } catch (e: Throwable) {
            _state.value = EngineState.Unsupported("On-device AI is not supported on this device.")
            throw LlmException.NativeLibraryUnavailable(e)
        }

        val file = modelRepository.readyModelFile() ?: run {
            _state.value = EngineState.NoModel
            throw LlmException.ModelNotAvailable()
        }

        val available = deviceResources.memoryInfo().availMem
        val required = estimateRequiredBytes(file, config)
        if (available < required) {
            _state.value = EngineState.Unloaded
            throw LlmException.InsufficientMemory(available, required)
        }
        if (deviceResources.thermalLevel() >= ThermalLevel.CRITICAL) throw LlmException.Overheated()

        _state.value = EngineState.Loading(0f)
        val threads = deviceResources.recommendedThreads(config.threads)
        val newHandle = try {
            // Never cancel a load half-way: the native handle would leak ~1.3 GB.
            withContext(inferenceDispatcher + NonCancellable) {
                LlamaNative.loadModel(
                    path = file.absolutePath,
                    nCtx = config.effectiveContext(),
                    nThreads = threads,
                    nBatch = N_BATCH,
                    useMmap = config.useMmap,
                    quantizeKvCache = config.quantizeKvCache,
                    progress = { progress ->
                        _state.value = EngineState.Loading(progress)
                        true
                    },
                )
            }
        } catch (e: IllegalStateException) {
            _state.value = EngineState.Error(e.message ?: "Failed to load the model")
            throw LlmException.LoadFailed(e.message ?: "Failed to load the model", e)
        }

        handle = newHandle
        loadedConfig = config
        val description = withContext(inferenceDispatcher + NonCancellable) { LlamaNative.describe(newHandle) }
        runtimeInfo = parseRuntimeInfo(description, threads, file)
        Log.i(TAG, "Model loaded: $description")
        _state.value = EngineState.Ready(runtimeInfo!!, null)
        return newHandle
    }

    private suspend fun unloadLocked() {
        cancelWatchdog()
        val engine = handle
        if (engine == 0L) return
        handle = 0L
        runtimeInfo = null
        loadedConfig = null
        withContext(inferenceDispatcher + NonCancellable) { LlamaNative.unloadModel(engine) }
        _state.value = if (modelRepository.readyModelFile() != null) EngineState.Unloaded else EngineState.NoModel
        Log.i(TAG, "Model unloaded")
    }

    /** Auto resolves from the phone's total RAM (stable for the device). */
    private fun ModelConfig.effectiveContext(): Int = resolvedContextSize(deviceResources.memoryInfo().totalMem)

    private fun ModelConfig.requiresReload(other: ModelConfig) =
        contextSize != other.contextSize || useMmap != other.useMmap || quantizeKvCache != other.quantizeKvCache

    /**
     * Weights are memory-mapped, so part of the file can be paged out under pressure; the KV cache
     * and compute buffers are anonymous memory and must fit.
     */
    private fun estimateRequiredBytes(file: File, config: ModelConfig): Long {
        val weights = if (config.useMmap) (file.length() * 0.8).toLong() else file.length()
        val kvPerToken = maxOf(QWEN3_1_7B_KV_BYTES_PER_TOKEN, file.length() / 9_600)
        val kv = (kvPerToken * config.effectiveContext() * if (config.quantizeKvCache) 0.53 else 1.0).toLong()
        return weights + kv + COMPUTE_BUFFER_BYTES + SAFETY_MARGIN_BYTES
    }

    // ------------------------------------------------------------------------------------------ //
    //  Watchdog
    // ------------------------------------------------------------------------------------------ //

    private fun scheduleWatchdog() {
        cancelWatchdog()
        val info = runtimeInfo ?: return
        if (handle == 0L) return
        val minutes = autoUnloadMinutes
        if (minutes <= 0) {
            _state.value = EngineState.Ready(info, null)
            return
        }
        val delayMs = minutes * 60_000L
        _state.value = EngineState.Ready(info, System.currentTimeMillis() + delayMs)
        watchdog = scope.launch {
            delay(delayMs)
            watchdog = null
            if (mutex.tryLock()) {
                try {
                    Log.i(TAG, "Auto-unloading model after $minutes idle minute(s)")
                    unloadLocked()
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    private fun cancelWatchdog() {
        watchdog?.cancel()
        watchdog = null
    }

    // ------------------------------------------------------------------------------------------ //

    /**
     * Runs a blocking native call on the inference thread. If the calling coroutine is cancelled
     * the native code is told to stop, and the coroutine resumes immediately; the single-thread
     * executor guarantees the next native call still waits for this one to return.
     */
    private suspend fun <T> runNativeCancellable(block: () -> T): T = suspendCancellableCoroutine { cont ->
        executor.execute {
            try {
                cont.resume(block())
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }
        cont.invokeOnCancellation { LlamaNative.stopGeneration() }
    }

    private fun LongArray.toStats() = GenerationStats(
        stopReason = when (getOrElse(0) { 0L }.toInt()) {
            0 -> StopReason.END_OF_TURN
            1 -> StopReason.MAX_TOKENS
            2 -> StopReason.CANCELLED
            else -> StopReason.CONTEXT_FULL
        },
        promptTokens = getOrElse(1) { 0L }.toInt(),
        cachedPromptTokens = getOrElse(2) { 0L }.toInt(),
        generatedTokens = getOrElse(3) { 0L }.toInt(),
        promptEvalMillis = getOrElse(4) { 0L } / 1000,
        generationMillis = getOrElse(5) { 0L } / 1000,
    )

    private fun parseRuntimeInfo(description: String, threads: Int, file: File): ModelRuntimeInfo {
        val obj = runCatching { json.parseToJsonElement(description).jsonObject }.getOrNull()
        fun str(key: String) = obj?.get(key)?.jsonPrimitive?.contentOrNull
        return ModelRuntimeInfo(
            name = str("name")?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            description = str("description").orEmpty(),
            contextSize = obj?.get("context")?.jsonPrimitive?.intOrNull ?: contextSize,
            threads = threads,
            kvCacheType = str("kvCacheType") ?: "f16",
            sizeBytes = obj?.get("sizeBytes")?.jsonPrimitive?.longOrNull ?: file.length(),
            parameterCount = obj?.get("parameters")?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private companion object {
        const val TAG = "LocalLlmEngine"
        const val NATIVE_STACK_BYTES = 16L * 1024 * 1024
        const val N_BATCH = 512
        const val QWEN3_1_7B_KV_BYTES_PER_TOKEN = 114_688L // 28 layers x 8 KV heads x 128 x (K+V) x f16
        const val COMPUTE_BUFFER_BYTES = 160L * 1024 * 1024
        const val SAFETY_MARGIN_BYTES = 128L * 1024 * 1024
    }
}
