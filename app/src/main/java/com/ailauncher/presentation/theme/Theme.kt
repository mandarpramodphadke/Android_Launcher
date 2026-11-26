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
import com.ailauncher.BuildConfig
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.ClockFace
import com.ailauncher.domain.model.ClockFont
import com.ailauncher.domain.model.DockStyle
import com.ailauncher.domain.model.HomeTextColor
import com.ailauncher.domain.model.IconShape
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
    /** Icon pack package (full version), else null. */
    val iconPack: String? = null,
    val iconShape: IconShape = IconShape.SYSTEM,
    /** Multiplies every app icon's size (1 = default). */
    val iconScale: Float = 1f,
    /** Names under icons on the home pages. */
    val homeLabels: Boolean = true,
    val clockFace: ClockFace = ClockFace.CLASSIC,
    val clockFont: ClockFont = ClockFont.DEFAULT,
    val clockBattery: Boolean = false,
    /** Blur behind the drawer, folders and menus. */
    val blur: Boolean = false,
    val wallpaperParallax: Boolean = false,
    /** DOCK_AND_GRID: the dock's look and the command bar inside it. */
    val dockStyle: DockStyle = DockStyle.PILL,
    val commandBarInDock: Boolean = false,
    /** Home rows, [LauncherSettings.AUTO_ROWS] to fit the screen. */
    val homeRows: Int = LauncherSettings.AUTO_ROWS,
    /** Multiplies the size of app and folder names (1 = default). */
    val labelScale: Float = 1f,
    val labelFont: ClockFont = ClockFont.DEFAULT,
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
    ).withLooks(settings)
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

/**
 * The 1.7 and 1.8 look settings, each only where its feature is on in this build (full version first);
 * elsewhere the launcher looks as before, even if a backup from the full version set them.
 */
fun LauncherStyle.withLooks(settings: LauncherSettings): LauncherStyle {
    val icons = BuildConfig.ICON_CUSTOMIZATION
    val clock = BuildConfig.CLOCK_STYLES
    val effects = BuildConfig.BLUR_EFFECTS
    val dock = BuildConfig.DOCK_AND_GRID
    return copy(
        iconPack = if (icons) settings.iconPackPackage else null,
        iconShape = if (icons) settings.iconShape else IconShape.SYSTEM,
        iconScale = if (icons) settings.iconSizePercent.coerceIn(LauncherSettings.MIN_ICON_SIZE, LauncherSettings.MAX_ICON_SIZE) / 100f else 1f,
        homeLabels = !icons || settings.showHomeLabels,
        clockFace = if (clock) settings.clockFace else ClockFace.CLASSIC,
        clockFont = if (clock) settings.clockFont else ClockFont.DEFAULT,
        clockBattery = clock && settings.clockShowsBattery,
        blur = effects && settings.blurEffects && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        wallpaperParallax = effects && settings.wallpaperParallax,
        dockStyle = if (dock) settings.dockStyle else DockStyle.PILL,
        commandBarInDock = dock && settings.commandBarInDock,
        homeRows = if (dock) settings.homeRows else LauncherSettings.AUTO_ROWS,
        labelScale = if (dock) settings.labelSize.scale else 1f,
        labelFont = if (dock) settings.labelFont else ClockFont.DEFAULT,
    )
}
