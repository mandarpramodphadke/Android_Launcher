package com.ailauncher.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.HomeTextColor
import com.ailauncher.domain.model.IconStyle
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ThemeMode

/** Launcher-specific styling that Material's ColorScheme has no slot for. */
@Immutable
data class LauncherStyle(
    val theme: AppTheme = AppTheme.MATERIAL_YOU,
    val dark: Boolean = false,
    /** Text-only home screen and drawer. */
    val minimal: Boolean = false,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val homeTextColor: HomeTextColor = HomeTextColor.AUTO,
    val homeTextShade: Boolean = true,
    /** Accent per [AppCategory] ordinal for themes that colour-code categories, else null. */
    val categoryColors: List<Color>? = null,
) {
    fun categoryColor(category: AppCategory?): Color? = category?.let { categoryColors?.getOrNull(it.ordinal) }
}

val LocalLauncherStyle = staticCompositionLocalOf { LauncherStyle() }

/** Dark-only themes (AMOLED, Neon) ignore the light/dark setting. */
fun resolveDarkTheme(theme: AppTheme, mode: ThemeMode, systemDark: Boolean): Boolean = when {
    !theme.supportsLight -> true
    mode == ThemeMode.LIGHT -> false
    mode == ThemeMode.DARK -> true
    else -> systemDark
}

@Composable
fun AiLauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = resolveDarkTheme(settings.appTheme, settings.themeMode, isSystemInDarkTheme())
    val context = LocalContext.current
    val dynamic = if (LauncherColorSchemes.usesWallpaperColors(settings.appTheme) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val scheme = LauncherColorSchemes.forTheme(settings.appTheme, dark, dynamic)
    val style = LauncherStyle(
        theme = settings.appTheme,
        dark = dark,
        minimal = settings.minimalMode,
        iconStyle = settings.iconStyle,
        homeTextColor = settings.homeTextColor,
        homeTextShade = settings.homeTextShade,
        categoryColors = LauncherColorSchemes.categoryColors(settings.appTheme, dark),
    )
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        // Material infers content colour only for exact scheme colours; anything drawn on a
        // translucent surface would otherwise fall back to black, even in dark themes.
        CompositionLocalProvider(
            LocalLauncherStyle provides style,
            LocalContentColor provides scheme.onSurface,
            content = content,
        )
    }
}
