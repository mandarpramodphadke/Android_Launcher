package com.ailauncher.data.llm

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash protection for "GPU Offload (Vulkan)". A buggy GPU driver can take the whole launcher
 * process down while the model is uploaded or first run on the GPU, which no try/catch survives.
 * A marker is written (synchronously) before that and cleared after it; finding it at the next
 * start means the GPU path crashed, so the engine switches back to CPU-only by itself.
 */
@Singleton
class GpuOffloadGuard @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gpu_offload", Context.MODE_PRIVATE)

    // commit, not apply: the marker must be on disk before a driver crash can kill the process.
    fun begin() {
        prefs.edit(commit = true) { putBoolean(KEY_PENDING, true) }
    }

    fun end() {
        prefs.edit { putBoolean(KEY_PENDING, false) }
    }

    /** True once after a crash on the GPU path (the marker is consumed). */
    fun consumeCrashMarker(): Boolean {
        val crashed = prefs.getBoolean(KEY_PENDING, false)
        if (crashed) prefs.edit(commit = true) { putBoolean(KEY_PENDING, false) }
        return crashed
    }

    /** Why the last GPU attempt fell back to the CPU (shown in Settings), or null. */
    var lastFallbackReason: String?
        get() = prefs.getString(KEY_REASON, null)
        set(value) {
            prefs.edit { putString(KEY_REASON, value) }
        }

    private companion object {
        const val KEY_PENDING = "gpu_path_pending"
        const val KEY_REASON = "fallback_reason"
    }
}
