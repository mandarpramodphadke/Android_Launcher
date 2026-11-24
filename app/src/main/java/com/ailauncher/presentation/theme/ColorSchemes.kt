package com.ailauncher.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppTheme

/**
 * Hand-tuned colour schemes. Every role is set explicitly (Material's defaults would otherwise mix
 * baseline purple surfaces into these themes), and every text/background pair is verified to meet
 * WCAG AA contrast by ColorSchemeContrastTest, so no theme can put dark text on a dark surface.
 */
object LauncherColorSchemes {

    /** Themes that take their accents from the wallpaper (Android 12+). */
    fun usesWallpaperColors(theme: AppTheme): Boolean = theme == AppTheme.MATERIAL_YOU || theme == AppTheme.AMOLED

    /**
     * @param dark resolved brightness (always true for themes without a light variant).
     * @param dynamic the system's wallpaper-based scheme for [dark], or null (Android 10/11, tests).
     */
    fun forTheme(theme: AppTheme, dark: Boolean, dynamic: ColorScheme? = null): ColorScheme = when (theme) {
        AppTheme.MATERIAL_YOU -> dynamic ?: classic(dark)
        AppTheme.CLASSIC -> classic(dark)
        AppTheme.AMOLED -> amoled(dynamic?.takeIf { dark } ?: classic(dark = true))
        AppTheme.COLORFUL -> colorful(dark)
        AppTheme.NEON -> neon()
        AppTheme.MONOCHROME -> monochrome(dark)
    }

    /** Accent per [AppCategory] (by ordinal) for themes that colour-code the drawer, else null. */
    fun categoryColors(theme: AppTheme, dark: Boolean): List<Color>? = when (theme) {
        AppTheme.COLORFUL -> if (dark) colorfulCategoriesDark else colorfulCategoriesLight
        AppTheme.NEON -> neonCategories
        else -> null
    }

    // ---------------------------------------------------------------------------------------- //

    fun classic(dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFA5B4FC), onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
            inversePrimary = Color(0xFF4F46E5),
            secondary = Color(0xFFC4B5FD), onSecondary = Color(0xFF2E1065),
            secondaryContainer = Color(0xFF5B21B6), onSecondaryContainer = Color(0xFFEDE9FE),
            tertiary = Color(0xFF5EEAD4), onTertiary = Color(0xFF003731),
            tertiaryContainer = Color(0xFF115E59), onTertiaryContainer = Color(0xFFCCFBF1),
            background = Color(0xFF131318), onBackground = Color(0xFFE4E1E9),
            surface = Color(0xFF131318), onSurface = Color(0xFFE4E1E9),
            surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
            surfaceTint = Color(0xFFA5B4FC),
            inverseSurface = Color(0xFFE4E1E9), inverseOnSurface = Color(0xFF303036),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
            outline = Color(0xFF918F9A), outlineVariant = Color(0xFF46464F), scrim = Color.Black,
            surfaceBright = Color(0xFF39383F), surfaceDim = Color(0xFF131318),
            surfaceContainerLowest = Color(0xFF0E0E13), surfaceContainerLow = Color(0xFF1B1B21),
            surfaceContainer = Color(0xFF1F1F25), surfaceContainerHigh = Color(0xFF2A292F),
            surfaceContainerHighest = Color(0xFF35343A),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5), onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
            inversePrimary = Color(0xFFC0C1FF),
            secondary = Color(0xFF7C3AED), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDE9FE), onSecondaryContainer = Color(0xFF2E1065),
            tertiary = Color(0xFF0F766E), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFCCFBF1), onTertiaryContainer = Color(0xFF042F2E),
            background = Color(0xFFFBFBFF), onBackground = Color(0xFF1B1B21),
            surface = Color(0xFFFBFBFF), onSurface = Color(0xFF1B1B21),
            surfaceVariant = Color(0xFFE4E1EC), onSurfaceVariant = Color(0xFF46464F),
            surfaceTint = Color(0xFF4F46E5),
            inverseSurface = Color(0xFF303036), inverseOnSurface = Color(0xFFF2EFF7),
            error = Color(0xFFBA1A1A), onError = Color.White,
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
            outline = Color(0xFF777680), outlineVariant = Color(0xFFC7C5D0), scrim = Color.Black,
            surfaceBright = Color(0xFFFBFBFF), surfaceDim = Color(0xFFDBD9E0),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F5FC),
            surfaceContainer = Color(0xFFEFEFF7), surfaceContainerHigh = Color(0xFFE9E9F1),
            surfaceContainerHighest = Color(0xFFE3E3EB),
        )
    }

    /** True-black variant of any dark scheme (keeps its accents, e.g. Material You's). */
    fun amoled(base: ColorScheme): ColorScheme = base.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF232323),
        surfaceBright = Color(0xFF2C2C2C),
        surfaceVariant = Color(0xFF1C1C1C),
        outlineVariant = Color(0xFF2E2E2E),
    )

    fun colorful(dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFFFB0C8), onPrimary = Color(0xFF5E1133),
            primaryContainer = Color(0xFF8E2350), onPrimaryContainer = Color(0xFFFFD9E4),
            inversePrimary = Color(0xFFC2185B),
            secondary = Color(0xFFFFB77C), onSecondary = Color(0xFF4A2800),
            secondaryContainer = Color(0xFF6A3B00), onSecondaryContainer = Color(0xFFFFDCC2),
            tertiary = Color(0xFFD0BCFF), onTertiary = Color(0xFF3B0B91),
            tertiaryContainer = Color(0xFF5B21B6), onTertiaryContainer = Color(0xFFEADDFF),
            background = Color(0xFF1A1113), onBackground = Color(0xFFF0DEE0),
            surface = Color(0xFF1A1113), onSurface = Color(0xFFF0DEE0),
            surfaceVariant = Color(0xFF524346), onSurfaceVariant = Color(0xFFD7C1C4),
            surfaceTint = Color(0xFFFFB0C8),
            inverseSurface = Color(0xFFF0DEE0), inverseOnSurface = Color(0xFF382E30),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
            outline = Color(0xFFA08C8F), outlineVariant = Color(0xFF524346), scrim = Color.Black,
            surfaceBright = Color(0xFF42373A), surfaceDim = Color(0xFF1A1113),
            surfaceContainerLowest = Color(0xFF140C0E), surfaceContainerLow = Color(0xFF22191B),
            surfaceContainer = Color(0xFF271D1F), surfaceContainerHigh = Color(0xFF322729),
            surfaceContainerHighest = Color(0xFF3D3234),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFC2185B), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD9E4), onPrimaryContainer = Color(0xFF3E001D),
            inversePrimary = Color(0xFFFFB0C8),
            secondary = Color(0xFFC2410C), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFDBC8), onSecondaryContainer = Color(0xFF331100),
            tertiary = Color(0xFF6D28D9), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEADDFF), onTertiaryContainer = Color(0xFF25005A),
            background = Color(0xFFFFF8F6), onBackground = Color(0xFF231918),
            surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231918),
            surfaceVariant = Color(0xFFF4DDE1), onSurfaceVariant = Color(0xFF524346),
            surfaceTint = Color(0xFFC2185B),
            inverseSurface = Color(0xFF392E2F), inverseOnSurface = Color(0xFFFFEDEF),
            error = Color(0xFFBA1A1A), onError = Color.White,
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
            outline = Color(0xFF847376), outlineVariant = Color(0xFFD6C2C5), scrim = Color.Black,
            surfaceBright = Color(0xFFFFF8F6), surfaceDim = Color(0xFFE8D6D9),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0F2),
            surfaceContainer = Color(0xFFFCE8EC), surfaceContainerHigh = Color(0xFFF7E2E6),
            surfaceContainerHighest = Color(0xFFF1DCE0),
        )
    }

    /** Always dark: deep-night surfaces with electric cyan, magenta and lime accents. */
    fun neon(): ColorScheme = darkColorScheme(
        primary = Color(0xFF00E5FF), onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF00363D), onPrimaryContainer = Color(0xFF7DF4FF),
        inversePrimary = Color(0xFF006874),
        secondary = Color(0xFFFF4FD8), onSecondary = Color(0xFF3F0035),
        secondaryContainer = Color(0xFF5C0050), onSecondaryContainer = Color(0xFFFFD7F3),
        tertiary = Color(0xFFC6FF00), onTertiary = Color(0xFF2A3600),
        tertiaryContainer = Color(0xFF3A4A00), onTertiaryContainer = Color(0xFFE4FF8A),
        background = Color(0xFF07040F), onBackground = Color(0xFFEEE8FF),
        surface = Color(0xFF07040F), onSurface = Color(0xFFEEE8FF),
        surfaceVariant = Color(0xFF241C3A), onSurfaceVariant = Color(0xFFC3B9E6),
        surfaceTint = Color(0xFF00E5FF),
        inverseSurface = Color(0xFFEEE8FF), inverseOnSurface = Color(0xFF1B152F),
        error = Color(0xFFFF5C8A), onError = Color(0xFF4A0016),
        errorContainer = Color(0xFF5C0024), onErrorContainer = Color(0xFFFFD9DF),
        outline = Color(0xFF6E5FB5), outlineVariant = Color(0xFF2E2550), scrim = Color.Black,
        surfaceBright = Color(0xFF2B2245), surfaceDim = Color(0xFF07040F),
        surfaceContainerLowest = Color(0xFF030108), surfaceContainerLow = Color(0xFF0E0A1A),
        surfaceContainer = Color(0xFF140F24), surfaceContainerHigh = Color(0xFF1B152F),
        surfaceContainerHighest = Color(0xFF241C3A),
    )

    fun monochrome(dark: Boolean): ColorScheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF111111),
            primaryContainer = Color(0xFF3A3A3A), onPrimaryContainer = Color(0xFFF2F2F2),
            inversePrimary = Color(0xFF3A3A3A),
            secondary = Color(0xFFC7C7C7), onSecondary = Color(0xFF1A1A1A),
            secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color(0xFFEDEDED),
            tertiary = Color(0xFFDADADA), onTertiary = Color(0xFF1A1A1A),
            tertiaryContainer = Color(0xFF2E2E2E), onTertiaryContainer = Color(0xFFEDEDED),
            background = Color(0xFF0F0F0F), onBackground = Color(0xFFEDEDED),
            surface = Color(0xFF0F0F0F), onSurface = Color(0xFFEDEDED),
            surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFBDBDBD),
            surfaceTint = Color(0xFFF2F2F2),
            inverseSurface = Color(0xFFEDEDED), inverseOnSurface = Color(0xFF1F1F1F),
            error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
            outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF3A3A3A), scrim = Color.Black,
            surfaceBright = Color(0xFF363636), surfaceDim = Color(0xFF0F0F0F),
            surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF171717),
            surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF262626),
            surfaceContainerHighest = Color(0xFF313131),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF111111), onPrimary = Color.White,
            primaryContainer = Color(0xFFE4E4E4), onPrimaryContainer = Color(0xFF111111),
            inversePrimary = Color(0xFFDDDDDD),
            secondary = Color(0xFF4A4A4A), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEBEBEB), onSecondaryContainer = Color(0xFF1A1A1A),
            tertiary = Color(0xFF333333), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDDDDDD), onTertiaryContainer = Color(0xFF111111),
            background = Color.White, onBackground = Color(0xFF111111),
            surface = Color.White, onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFEEEEEE), onSurfaceVariant = Color(0xFF4D4D4D),
            surfaceTint = Color(0xFF111111),
            inverseSurface = Color(0xFF1F1F1F), inverseOnSurface = Color(0xFFF2F2F2),
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
            outline = Color(0xFF7A7A7A), outlineVariant = Color(0xFFCCCCCC), scrim = Color.Black,
            surfaceBright = Color.White, surfaceDim = Color(0xFFDEDEDE),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F7),
            surfaceContainer = Color(0xFFF1F1F1), surfaceContainerHigh = Color(0xFFEBEBEB),
            surfaceContainerHighest = Color(0xFFE5E5E5),
        )
    }

    // Ordered like AppCategory: PRODUCTIVITY, COMMUNICATION, SOCIAL, FINANCE, SHOPPING, MEDIA,
    // PHOTOGRAPHY, GAMES, TRAVEL, HEALTH, EDUCATION, NEWS, UTILITIES.
    private val colorfulCategoriesLight = listOf(
        Color(0xFF1D4ED8), Color(0xFF047857), Color(0xFFC2185B), Color(0xFF3F6212), Color(0xFFC2410C),
        Color(0xFFBE123C), Color(0xFFA21CAF), Color(0xFF6D28D9), Color(0xFF0369A1), Color(0xFF0E7490),
        Color(0xFFA34805), Color(0xFF854D0E), Color(0xFF475569),
    )

    private val colorfulCategoriesDark = listOf(
        Color(0xFF93C5FD), Color(0xFF6EE7B7), Color(0xFFF9A8D4), Color(0xFFBEF264), Color(0xFFFDBA74),
        Color(0xFFFDA4AF), Color(0xFFF0ABFC), Color(0xFFC4B5FD), Color(0xFF7DD3FC), Color(0xFF67E8F9),
        Color(0xFFFCD34D), Color(0xFFFEF08A), Color(0xFFCBD5E1),
    )

    private val neonCategories = listOf(
        Color(0xFF4DA3FF), Color(0xFF00FFA3), Color(0xFFFF4FD8), Color(0xFFC6FF00), Color(0xFFFF9E3D),
        Color(0xFFFF3D7F), Color(0xFFE879F9), Color(0xFFB388FF), Color(0xFF00E5FF), Color(0xFF69F0AE),
        Color(0xFFFFE600), Color(0xFFFFD166), Color(0xFFB0BEC5),
    )

    init {
        check(colorfulCategoriesLight.size == AppCategory.entries.size)
        check(colorfulCategoriesDark.size == AppCategory.entries.size)
        check(neonCategories.size == AppCategory.entries.size)
    }
}
