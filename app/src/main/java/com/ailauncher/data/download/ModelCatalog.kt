package com.ailauncher.data.download

import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelSpec

/**
 * Downloadable models. All are Qwen3 (Apache-2.0) Q4_K_M builds from Unsloth's GGUF conversions
 * (the official Qwen repositories publish no Q4_K_M for these sizes), with URLs pinned to a
 * repository commit so the SHA-256 can never drift. They share one chat template and tool format.
 */
object ModelCatalog {
    private const val GIB = 1024L * 1024 * 1024

    val QWEN3_0_6B_Q4_K_M = ModelSpec(
        id = "qwen3-0.6b-q4_k_m",
        displayName = "Qwen3 0.6B · Q4_K_M",
        fileName = "qwen3-0.6b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/" +
            "50968a4468ef4233ed78cd7c3de230dd1d61a56b/Qwen3-0.6B-Q4_K_M.gguf",
        sizeBytes = 396_705_472L,
        sha256 = "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a",
        license = "Apache-2.0",
        summary = "Fastest and smallest. Fine for quick commands; weaker at planning and summaries.",
        recommendedRamBytes = 3 * GIB,
    )

    /**
     * Qwen3-1.7B, Q4_K_M quantisation (~1.03 GiB): the default balance of quality, speed and RAM.
     */
    val QWEN3_1_7B_Q4_K_M = ModelSpec(
        id = ModelConfig.DEFAULT_MODEL_ID,
        displayName = "Qwen3 1.7B · Q4_K_M",
        fileName = "qwen3-1.7b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/" +
            "d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
        sizeBytes = 1_107_409_472L,
        sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
        license = "Apache-2.0",
        summary = "Recommended. Good tool use and answers at a few tokens per second.",
        recommendedRamBytes = 4 * GIB,
    )

    val QWEN3_4B_Q4_K_M = ModelSpec(
        id = "qwen3-4b-q4_k_m",
        displayName = "Qwen3 4B · Q4_K_M",
        fileName = "qwen3-4b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/" +
            "22c9fc8a8c7700b76a1789366280a6a5a1ad1120/Qwen3-4B-Q4_K_M.gguf",
        sizeBytes = 2_497_281_312L,
        sha256 = "f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a",
        license = "Apache-2.0",
        summary = "Best quality, about 2.5x slower. For phones with 8 GB of RAM or more.",
        recommendedRamBytes = 7 * GIB,
    )

    val DEFAULT = QWEN3_1_7B_Q4_K_M

    val all: List<ModelSpec> = listOf(QWEN3_0_6B_Q4_K_M, QWEN3_1_7B_Q4_K_M, QWEN3_4B_Q4_K_M)

    fun byId(id: String?): ModelSpec? = all.firstOrNull { it.id == id }
}
