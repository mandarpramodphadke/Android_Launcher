package com.ailauncher.presentation.theme

import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.HomeTextColor
import com.ailauncher.domain.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WallpaperPolarityTest {

    private fun auto(hint: Boolean?, vararg luminances: Float) =
        WallpaperPolarity.darkText(HomeTextColor.AUTO, hint, luminances.toList())

    @Test
    fun userOverrideWins() {
        assertThat(WallpaperPolarity.darkText(HomeTextColor.LIGHT, supportsDarkTextHint = true, listOf(1f, 1f))).isFalse()
        assertThat(WallpaperPolarity.darkText(HomeTextColor.DARK, supportsDarkTextHint = false, listOf(0f))).isTrue()
    }

    @Test
    fun systemHintDecidesOnAndroid12() {
        assertThat(auto(true, 0.1f)).isTrue()
        assertThat(auto(false, 0.95f, 0.9f, 0.9f)).isFalse()
    }

    @Test
    fun withoutHintDarkTextNeedsAnAllLightWallpaper() {
        assertThat(auto(null, 0.92f, 0.81f, 0.7f)).isTrue()
        // A bright wallpaper with one dark colour (e.g. a dark band behind the clock) keeps light text.
        assertThat(auto(null, 0.9f, 0.85f, 0.12f)).isFalse()
        assertThat(auto(null, 0.55f)).isFalse()
        // No colours at all (some live wallpapers): light text with a shadow is the safe default.
        assertThat(auto(null)).isFalse()
    }

    @Test
    fun darkOnlyThemesIgnoreTheLightDarkSetting() {
        listOf(AppTheme.AMOLED, AppTheme.NEON).forEach { theme ->
            ThemeMode.entries.forEach { mode ->
                assertThat(resolveDarkTheme(theme, mode, systemDark = false)).isTrue()
            }
        }
        assertThat(resolveDarkTheme(AppTheme.CLASSIC, ThemeMode.LIGHT, systemDark = true)).isFalse()
        assertThat(resolveDarkTheme(AppTheme.COLORFUL, ThemeMode.DARK, systemDark = false)).isTrue()
        assertThat(resolveDarkTheme(AppTheme.MONOCHROME, ThemeMode.SYSTEM, systemDark = true)).isTrue()
        assertThat(resolveDarkTheme(AppTheme.MATERIAL_YOU, ThemeMode.SYSTEM, systemDark = false)).isFalse()
    }
}
