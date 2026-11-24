package com.ailauncher.data.download

import com.ailauncher.domain.model.ModelConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun catalogOffersThreeQwen3SizesWithTheDefaultFirstClass() {
        assertThat(ModelCatalog.all.map { it.id })
            .containsExactly("qwen3-0.6b-q4_k_m", ModelConfig.DEFAULT_MODEL_ID, "qwen3-4b-q4_k_m").inOrder()
        assertThat(ModelCatalog.DEFAULT.id).isEqualTo(ModelConfig.DEFAULT_MODEL_ID)
        assertThat(ModelCatalog.byId(ModelConfig.DEFAULT_MODEL_ID)).isSameInstanceAs(ModelCatalog.DEFAULT)
        assertThat(ModelCatalog.byId("imported:foo.gguf")).isNull()
    }

    @Test
    fun everyDownloadIsPinnedToACommitAndVerified() {
        val pinned = Regex("""^https://huggingface\.co/unsloth/Qwen3-[0-9.]+B-GGUF/resolve/[0-9a-f]{40}/Qwen3-[0-9.]+B-Q4_K_M\.gguf$""")
        ModelCatalog.all.forEach { spec ->
            assertThat(spec.downloadUrl).matches(pinned.pattern)
            assertThat(spec.sha256).matches("[0-9a-f]{64}")
            assertThat(spec.fileName).isEqualTo(spec.id + ".gguf")
            assertThat(spec.license).isEqualTo("Apache-2.0")
            assertThat(spec.imported).isFalse()
        }
        assertThat(ModelCatalog.all.map { it.sha256 }.toSet()).hasSize(ModelCatalog.all.size)
    }

    @Test
    fun largerModelsAskForMoreRam() {
        val (small, medium, large) = ModelCatalog.all
        assertThat(small.sizeBytes).isLessThan(medium.sizeBytes)
        assertThat(medium.sizeBytes).isLessThan(large.sizeBytes)
        assertThat(small.recommendedRamBytes).isLessThan(medium.recommendedRamBytes)
        assertThat(medium.recommendedRamBytes).isLessThan(large.recommendedRamBytes)
    }
}
