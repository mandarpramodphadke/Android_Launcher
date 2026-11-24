package com.ailauncher.data.llm

/** Invoked from native code for every complete UTF-8 chunk. Return false to stop generating. */
fun interface NativeTokenCallback {
    fun onToken(utf8: ByteArray): Boolean
}

/** Invoked from native code while the GGUF loads (0..1). Return false to abort loading. */
fun interface NativeProgressCallback {
    fun onProgress(progress: Float): Boolean
}

/**
 * JNI surface of libailauncher_llm.so (see app/src/main/cpp/llama_jni.cpp). All calls except
 * [stopGeneration] must happen on the single inference thread owned by [LocalLlmEngine].
 */
internal object LlamaNative {

    @Volatile
    private var initialized = false
    private var initFailure: Throwable? = null

    /** Loads the shared libraries and lets ggml pick the best CPU backend variant for this SoC. */
    @Synchronized
    fun initialize(nativeLibraryDir: String) {
        if (initialized) return
        initFailure?.let { throw it }
        try {
            System.loadLibrary("ailauncher_llm")
            nativeInit(nativeLibraryDir)
            initialized = true
        } catch (t: Throwable) {
            initFailure = t
            throw t
        }
    }

    val isInitialized: Boolean get() = initialized

    @JvmStatic external fun nativeInit(nativeLibDir: String)

    @JvmStatic external fun systemInfo(): String

    /** @return an opaque engine handle; throws IllegalStateException on failure. */
    @JvmStatic external fun loadModel(
        path: String,
        nCtx: Int,
        nThreads: Int,
        nBatch: Int,
        useMmap: Boolean,
        quantizeKvCache: Boolean,
        nGpuLayers: Int,
        progress: NativeProgressCallback?,
    ): Long

    /**
     * Loads the Vulkan backend on first call (only ever when the user enabled GPU offload) and
     * returns the GPU device name, or "" when there is no usable Vulkan GPU.
     */
    @JvmStatic external fun enableGpu(): String

    /**
     * Blocking streaming generation. Returns
     * `[stopReason, promptTokens, cachedTokens, generatedTokens, promptEvalUs, generationUs]`.
     */
    @JvmStatic external fun generateStream(
        handle: Long,
        prompt: ByteArray,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        presencePenalty: Float,
        repeatPenalty: Float,
        maxTokens: Int,
        seed: Int,
        grammar: ByteArray?,
        callback: NativeTokenCallback,
    ): LongArray

    /** Thread-safe: aborts the running prompt evaluation / generation. */
    @JvmStatic external fun stopGeneration()

    @JvmStatic external fun unloadModel(handle: Long)

    @JvmStatic external fun countTokens(handle: Long, text: ByteArray): Int

    @JvmStatic external fun setThreads(handle: Long, nThreads: Int)

    @JvmStatic external fun clearCache(handle: Long)

    /** JSON with model metadata (name, size, context, KV cache type, …). */
    @JvmStatic external fun describe(handle: Long): String
}
