package com.ailauncher.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppTheme
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG 2.1 contrast checks for every theme. Text must reach 4.5:1 against the background it is drawn
 * on; non-text UI (icons, outlines) 3:1. This is what keeps dark text off dark surfaces.
 */
class ColorSchemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Source-over compositing of a translucent colour onto an opaque one (sRGB, like the GPU). */
    private fun Color.over(background: Color): Color = Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
    )

    private val variants: List<Pair<String, ColorScheme>> = AppTheme.entries.flatMap { theme ->
        val modes = if (theme.supportsLight) listOf(false, true) else listOf(true)
        modes.map { dark -> "$theme/${if (dark) "dark" else "light"}" to LauncherColorSchemes.forTheme(theme, dark) }
    }

    private fun textPairs(s: ColorScheme) = listOf(
        "onPrimary on primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer on primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondary on secondary" to (s.onSecondary to s.secondary),
        "onSecondaryContainer on secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiary on tertiary" to (s.onTertiary to s.tertiary),
        "onTertiaryContainer on tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onBackground on background" to (s.onBackground to s.background),
        "onSurface on surface" to (s.onSurface to s.surface),
        "onSurface on surfaceDim" to (s.onSurface to s.surfaceDim),
        "onSurface on surfaceBright" to (s.onSurface to s.surfaceBright),
        "onSurface on surfaceContainerLowest" to (s.onSurface to s.surfaceContainerLowest),
        "onSurface on surfaceContainerLow" to (s.onSurface to s.surfaceContainerLow),
        "onSurface on surfaceContainer" to (s.onSurface to s.surfaceContainer),
        "onSurface on surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
        "onSurface on surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "onSurfaceVariant on surface" to (s.onSurfaceVariant to s.surface),
        "onSurfaceVariant on surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
        "onSurfaceVariant on surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
        "onSurfaceVariant on surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
        "onError on error" to (s.onError to s.error),
        "onErrorContainer on errorContainer" to (s.onErrorContainer to s.errorContainer),
        "inverseOnSurface on inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
        // Accent-coloured text (section titles, links, chat chips) on the screen background.
        "primary on surface" to (s.primary to s.surface),
        "primary on surfaceContainerLow" to (s.primary to s.surfaceContainerLow),
    )

    private fun uiPairs(s: ColorScheme) = listOf(
        "primary on surfaceContainerHigh" to (s.primary to s.surfaceContainerHigh),
        "secondary on surface" to (s.secondary to s.surface),
        "tertiary on surface" to (s.tertiary to s.surface),
        "outline on surface" to (s.outline to s.surface),
    )

    @Test
    fun textMeetsAaContrastInEveryTheme() {
        variants.forEach { (name, scheme) ->
            textPairs(scheme).forEach { (pair, colors) ->
                assertWithMessage("$name: $pair").that(contrast(colors.first, colors.second)).isAtLeast(4.5)
            }
        }
    }

    @Test
    fun iconsAndOutlinesMeetNonTextContrast() {
        variants.forEach { (name, scheme) ->
            uiPairs(scheme).forEach { (pair, colors) ->
                assertWithMessage("$name: $pair").that(contrast(colors.first, colors.second)).isAtLeast(3.0)
            }
        }
    }

    @Test
    fun translucentHomeSurfacesKeepTextReadableOverAnyWallpaper() {
        // Dock / search row / command bar: onSurface on surface at the alpha used on the home screen,
        // over the worst-case wallpaper pixel for the theme's polarity (black or white).
        variants.forEach { (name, s) ->
            listOf(Color.Black, Color.White).forEach { wallpaper ->
                val dock = s.surface.copy(alpha = 0.74f).over(wallpaper)
                val bar = s.surfaceContainerHigh.copy(alpha = 0.94f).over(wallpaper)
                assertWithMessage("$name: dock text over $wallpaper").that(contrast(s.onSurface, dock)).isAtLeast(4.5)
                assertWithMessage("$name: command bar text over $wallpaper").that(contrast(s.onSurface, bar)).isAtLeast(4.5)
            }
        }
    }

    @Test
    fun amoledIsTrueBlackAndKeepsAccents() {
        val amoled = LauncherColorSchemes.forTheme(AppTheme.AMOLED, dark = true)
        val classicDark = LauncherColorSchemes.forTheme(AppTheme.CLASSIC, dark = true)
        assertWithMessage("background").that(amoled.background).isEqualTo(Color.Black)
        assertWithMessage("surface").that(amoled.surface).isEqualTo(Color.Black)
        assertWithMessage("primary").that(amoled.primary).isEqualTo(classicDark.primary)
        // Even when asked for light, a dark-only theme resolves dark.
        assertWithMessage("resolved brightness")
            .that(resolveDarkTheme(AppTheme.AMOLED, com.ailauncher.domain.model.ThemeMode.LIGHT, systemDark = false)).isTrue()
    }

    @Test
    fun categoryAccentsAreReadableAsTitles() {
        AppTheme.entries.forEach { theme ->
            val modes = if (theme.supportsLight) listOf(false, true) else listOf(true)
            modes.forEach { dark ->
                val accents = LauncherColorSchemes.categoryColors(theme, dark) ?: return@forEach
                val scheme = LauncherColorSchemes.forTheme(theme, dark)
                assertWithMessage("$theme size").that(accents).hasSize(AppCategory.entries.size)
                accents.forEachIndexed { index, accent ->
                    val category = AppCategory.entries[index]
                    listOf(scheme.surface, scheme.surfaceContainerLow).forEach { background ->
                        assertWithMessage("$theme/${if (dark) "dark" else "light"}: $category")
                            .that(contrast(accent, background)).isAtLeast(4.5)
                    }
                }
            }
        }
    }

    @Test
    fun onlyColourfulAndNeonColourCodeCategories() {
        val coded = AppTheme.entries.filter { LauncherColorSchemes.categoryColors(it, dark = true) != null }
        assertWithMessage("themes with category colours").that(coded).containsExactly(AppTheme.COLORFUL, AppTheme.NEON)
        val style = LauncherStyle(categoryColors = LauncherColorSchemes.categoryColors(AppTheme.NEON, dark = true))
        assertWithMessage("lookup by category").that(style.categoryColor(AppCategory.GAMES))
            .isEqualTo(LauncherColorSchemes.categoryColors(AppTheme.NEON, dark = true)!![AppCategory.GAMES.ordinal])
        assertWithMessage("unsorted apps").that(style.categoryColor(null)).isNull()
    }

    @Test
    fun wallpaperChipsKeepTextReadableOnAnyRegion() {
        // The chip behind home-screen text has the opposite polarity, so even when the wallpaper
        // region under it is the worst case for that text, the composite stays readable.
        listOf(Color.Black, Color(0xFF808080), Color.White).forEach { wallpaper ->
            val darkChip = WallpaperTint.Dark.chip.over(wallpaper)
            assertWithMessage("dark text chip over $wallpaper")
                .that(contrast(WallpaperTint.Dark.content, darkChip)).isAtLeast(4.5)
            val lightChip = WallpaperTint.Light.chip.over(wallpaper)
            assertWithMessage("light text chip over $wallpaper")
                .that(contrast(WallpaperTint.Light.content, lightChip)).isAtLeast(3.0)
        }
        assertWithMessage("light text on its own scrim colour")
            .that(contrast(WallpaperTint.Light.content, WallpaperTint.Light.scrim)).isAtLeast(4.5)
        assertWithMessage("dark text on its own scrim colour")
            .that(contrast(WallpaperTint.Dark.content, WallpaperTint.Dark.scrim)).isAtLeast(4.5)
        assertWithMessage("dark text halo is light")
            .that(WallpaperTint.Dark.shadow.color.luminance()).isGreaterThan(0.5f)
        assertWithMessage("light text shadow is dark")
            .that(WallpaperTint.Light.shadow.color.luminance()).isLessThan(0.1f)
    }
}
