package com.ailauncher.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class DrawerLayout { GRID, LIST }

@Serializable
enum class DrawerTab { ALPHABETICAL, CATEGORIES }

/** Colour themes. [supportsLight] is false for themes that are always dark. */
@Serializable
enum class AppTheme(val supportsLight: Boolean) {
    /** Colours extracted from the wallpaper (Android 12+), Classic on older versions. */
    MATERIAL_YOU(true),
    CLASSIC(true),
    /** Pure-black surfaces: maximum contrast and battery savings on OLED screens. */
    AMOLED(false),
    /** Vibrant multi-hue palette with per-category colours and a gradient clock. */
    COLORFUL(true),
    /** Deep-night surfaces with glowing cyan / magenta / lime accents. */
    NEON(false),
    /** Black, white and greys only. Pairs well with minimal mode. */
    MONOCHROME(true),
}

/** Colour of text drawn directly on the wallpaper (home screen clock, favourites). */
@Serializable
enum class HomeTextColor { AUTO, LIGHT, DARK }

@Serializable
enum class IconStyle {
    ORIGINAL,
    /** Android 13-style monochrome icons tinted with the theme; desaturated when an app has none. */
    THEMED,
    GRAYSCALE,
}

/** Launcher-wide user preferences (persisted in DataStore, included in backups). */
@Serializable
data class LauncherSettings(
    val onboardingCompleted: Boolean = false,
    val modelSetupSeen: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTheme: AppTheme = AppTheme.MATERIAL_YOU,
    /** Text-only, distraction-free home screen and app drawer. */
    val minimalMode: Boolean = false,
    val homeTextColor: HomeTextColor = HomeTextColor.AUTO,
    /** Soft gradient behind home text so it stays readable on busy wallpapers. */
    val homeTextShade: Boolean = true,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val drawerLayout: DrawerLayout = DrawerLayout.GRID,
    val drawerTab: DrawerTab = DrawerTab.ALPHABETICAL,
    val gridColumns: Int = 4,
    val use24HourClock: Boolean? = null,
    val doubleTapToLock: Boolean = true,
    val swipeDownForNotifications: Boolean = true,
    val showAiCommandBar: Boolean = true,
    val autoConfirmAppLaunch: Boolean = false,
    val saveRemindersToCalendar: Boolean = true,
    val hapticFeedback: Boolean = true,
)

/** On-device model runtime configuration (persisted in DataStore, included in backups). */
@Serializable
data class ModelConfig(
    /** llama.cpp n_ctx. 2048 keeps the KV cache ~120 MB (q8_0); 4096 doubles it. */
    val contextSize: Int = DEFAULT_CONTEXT,
    /** 0 = auto-detect the performance cores (usually 4). */
    val threads: Int = 0,
    /** Free the model from RAM after this many idle minutes. 0 = keep loaded. */
    val autoUnloadMinutes: Int = 3,
    val useMmap: Boolean = true,
    val quantizeKvCache: Boolean = true,
    /** Start chats in Reasoning (thinking) mode instead of Fast Action mode. */
    val reasoningByDefault: Boolean = false,
) {
    companion object {
        const val DEFAULT_CONTEXT = 2048
        val CONTEXT_OPTIONS = listOf(2048, 4096)
        val AUTO_UNLOAD_OPTIONS = listOf(1, 3, 5, 10, 0)
    }
}
