package com.ailauncher.data.download

import com.ailauncher.domain.model.ModelSpec

object ModelCatalog {
    /**
     * Qwen3-1.7B, Q4_K_M quantisation (~1.03 GiB). The official Qwen repository only publishes
     * Q8_0 for the 1.7B model, so the Q4_K_M build comes from Unsloth's GGUF conversion. The URL is
     * pinned to a repository commit so the SHA-256 below can never drift.
     */
    val QWEN3_1_7B_Q4_K_M = ModelSpec(
        id = "qwen3-1.7b-q4_k_m",
        displayName = "Qwen3 1.7B · Q4_K_M",
        fileName = "qwen3-1.7b-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/" +
            "d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
        sizeBytes = 1_107_409_472L,
        sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
        license = "Apache-2.0",
    )

    val DEFAULT = QWEN3_1_7B_Q4_K_M
}
