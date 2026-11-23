package com.ailauncher.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class DrawerLayout { GRID, LIST }

@Serializable
enum class DrawerTab { ALPHABETICAL, CATEGORIES }

/** Launcher-wide user preferences (persisted in DataStore, included in backups). */
@Serializable
data class LauncherSettings(
    val onboardingCompleted: Boolean = false,
    val modelSetupSeen: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
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
