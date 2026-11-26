package com.ailauncher.domain.search

import com.ailauncher.data.packages.IconPackRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IconPackKeyTest {

    @Test
    fun readsAppFilterComponents() {
        assertThat(IconPackRepository.componentKey("ComponentInfo{com.spotify.music/com.spotify.music.MainActivity}"))
            .isEqualTo("com.spotify.music/com.spotify.music.MainActivity")
        assertThat(IconPackRepository.componentKey(" ComponentInfo{com.app/.Main} ")).isEqualTo("com.app/com.app.Main")
        assertThat(IconPackRepository.componentKey("ComponentInfo{com.app}")).isEqualTo("com.app")
        assertThat(IconPackRepository.componentKey("ComponentInfo{}")).isNull()
        assertThat(IconPackRepository.componentKey("ComponentInfo{/x}")).isNull()
    }
}
