package com.ailauncher.domain.system

import com.ailauncher.domain.model.ModelSpec

/** How well one on-device model suits this phone. */
data class ModelFit(
    val spec: ModelSpec,
    /** "best", "good", "tight" or "too big". */
    val verdict: String,
    val note: String,
    val inUse: Boolean,
    val enoughStorage: Boolean,
)

/**
 * Which of the downloadable models suit a phone, from its RAM and free storage. Pure, so the
 * assistant's answer to "which model can I run?" is the same rule the model picker uses.
 */
object ModelAdvice {

    /** RAM the rest of the phone keeps using while the model runs. */
    private const val SYSTEM_RESERVE = 3L * 1024 * 1024 * 1024

    fun forDevice(catalog: List<ModelSpec>, totalRamBytes: Long, freeStorageBytes: Long?, activeId: String?): List<ModelFit> {
        val fits = catalog.filter { !it.imported }.sortedBy { it.sizeBytes }
        val usable = fits.filter { totalRamBytes >= it.recommendedRamBytes }
        // The largest model the phone runs comfortably gives the best answers; smaller ones are faster.
        val best = usable.lastOrNull()
        return fits.map { spec ->
            val headroom = totalRamBytes - SYSTEM_RESERVE - spec.sizeBytes
            val verdict = when {
                spec == best -> "best"
                totalRamBytes >= spec.recommendedRamBytes -> "good"
                headroom > 0 -> "tight"
                else -> "too big"
            }
            val note = when (verdict) {
                "best" -> "best answers this phone can run well"
                "good" -> "faster, simpler answers"
                "tight" -> "may be slow or close other apps"
                else -> "needs more RAM than this phone has"
            }
            ModelFit(
                spec = spec,
                verdict = verdict,
                note = note,
                inUse = spec.id == activeId,
                enoughStorage = freeStorageBytes == null || spec.id == activeId || freeStorageBytes > spec.sizeBytes + 500L * 1024 * 1024,
            )
        }
    }
}
