package com.ailauncher.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ModelConfig

// Mapping between DataStore Preferences and the settings models. Pure functions (no Context), so
// defaults, clamping and migrations are covered by plain JVM unit tests.

internal object SettingsKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val MODEL_SETUP_SEEN = booleanPreferencesKey("model_setup_seen")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    /** Legacy (≤ 1.0.0) on/off for wallpaper colours: replaced by APP_THEME, read for migration. */
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val APP_THEME = stringPreferencesKey("app_theme")
    val MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
    val HOME_TEXT_COLOR = stringPreferencesKey("home_text_color")
    val HOME_TEXT_SHADE = booleanPreferencesKey("home_text_shade")
    val ICON_STYLE = stringPreferencesKey("icon_style")
    val DRAWER_LAYOUT = stringPreferencesKey("drawer_layout")
    val DRAWER_TAB = stringPreferencesKey("drawer_tab")
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val CLOCK_FORMAT = intPreferencesKey("clock_format") // 0 = system, 12, 24
    val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
    val SWIPE_DOWN_NOTIFICATIONS = booleanPreferencesKey("swipe_down_notifications")
    val SHOW_AI_BAR = booleanPreferencesKey("show_ai_bar")
    val AUTO_CONFIRM_LAUNCH = booleanPreferencesKey("auto_confirm_launch")
    val REMINDERS_TO_CALENDAR = booleanPreferencesKey("reminders_to_calendar")
    val HAPTICS = booleanPreferencesKey("haptics")

    val CONTEXT_SIZE = intPreferencesKey("model_context_size")
    val THREADS = intPreferencesKey("model_threads")
    val AUTO_UNLOAD_MINUTES = intPreferencesKey("model_auto_unload_minutes")
    val USE_MMAP = booleanPreferencesKey("model_use_mmap")
    val QUANTIZE_KV = booleanPreferencesKey("model_quantize_kv")
    val REASONING_DEFAULT = booleanPreferencesKey("model_reasoning_default")
}

internal fun Preferences.toLauncherSettings(): LauncherSettings {
    val defaults = LauncherSettings()
    return LauncherSettings(
        onboardingCompleted = this[SettingsKeys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
        modelSetupSeen = this[SettingsKeys.MODEL_SETUP_SEEN] ?: defaults.modelSetupSeen,
        themeMode = enumOrDefault(this[SettingsKeys.THEME_MODE], defaults.themeMode),
        appTheme = this[SettingsKeys.APP_THEME]?.let { enumOrDefault(it, defaults.appTheme) }
            ?: if (this[SettingsKeys.DYNAMIC_COLOR] == false) AppTheme.CLASSIC else defaults.appTheme,
        minimalMode = this[SettingsKeys.MINIMAL_MODE] ?: defaults.minimalMode,
        homeTextColor = enumOrDefault(this[SettingsKeys.HOME_TEXT_COLOR], defaults.homeTextColor),
        homeTextShade = this[SettingsKeys.HOME_TEXT_SHADE] ?: defaults.homeTextShade,
        iconStyle = enumOrDefault(this[SettingsKeys.ICON_STYLE], defaults.iconStyle),
        drawerLayout = enumOrDefault(this[SettingsKeys.DRAWER_LAYOUT], defaults.drawerLayout),
        drawerTab = enumOrDefault(this[SettingsKeys.DRAWER_TAB], defaults.drawerTab),
        gridColumns = (this[SettingsKeys.GRID_COLUMNS] ?: defaults.gridColumns).coerceIn(3, 6),
        use24HourClock = when (this[SettingsKeys.CLOCK_FORMAT]) {
            12 -> false
            24 -> true
            else -> null
        },
        doubleTapToLock = this[SettingsKeys.DOUBLE_TAP_LOCK] ?: defaults.doubleTapToLock,
        swipeDownForNotifications = this[SettingsKeys.SWIPE_DOWN_NOTIFICATIONS] ?: defaults.swipeDownForNotifications,
        showAiCommandBar = this[SettingsKeys.SHOW_AI_BAR] ?: defaults.showAiCommandBar,
        autoConfirmAppLaunch = this[SettingsKeys.AUTO_CONFIRM_LAUNCH] ?: defaults.autoConfirmAppLaunch,
        saveRemindersToCalendar = this[SettingsKeys.REMINDERS_TO_CALENDAR] ?: defaults.saveRemindersToCalendar,
        hapticFeedback = this[SettingsKeys.HAPTICS] ?: defaults.hapticFeedback,
    )
}

internal fun MutablePreferences.writeLauncherSettings(s: LauncherSettings) {
    this[SettingsKeys.ONBOARDING_COMPLETED] = s.onboardingCompleted
    this[SettingsKeys.MODEL_SETUP_SEEN] = s.modelSetupSeen
    this[SettingsKeys.THEME_MODE] = s.themeMode.name
    remove(SettingsKeys.DYNAMIC_COLOR)
    this[SettingsKeys.APP_THEME] = s.appTheme.name
    this[SettingsKeys.MINIMAL_MODE] = s.minimalMode
    this[SettingsKeys.HOME_TEXT_COLOR] = s.homeTextColor.name
    this[SettingsKeys.HOME_TEXT_SHADE] = s.homeTextShade
    this[SettingsKeys.ICON_STYLE] = s.iconStyle.name
    this[SettingsKeys.DRAWER_LAYOUT] = s.drawerLayout.name
    this[SettingsKeys.DRAWER_TAB] = s.drawerTab.name
    this[SettingsKeys.GRID_COLUMNS] = s.gridColumns.coerceIn(3, 6)
    this[SettingsKeys.CLOCK_FORMAT] = when (s.use24HourClock) {
        true -> 24
        false -> 12
        null -> 0
    }
    this[SettingsKeys.DOUBLE_TAP_LOCK] = s.doubleTapToLock
    this[SettingsKeys.SWIPE_DOWN_NOTIFICATIONS] = s.swipeDownForNotifications
    this[SettingsKeys.SHOW_AI_BAR] = s.showAiCommandBar
    this[SettingsKeys.AUTO_CONFIRM_LAUNCH] = s.autoConfirmAppLaunch
    this[SettingsKeys.REMINDERS_TO_CALENDAR] = s.saveRemindersToCalendar
    this[SettingsKeys.HAPTICS] = s.hapticFeedback
}

internal fun Preferences.toModelConfig(): ModelConfig {
    val defaults = ModelConfig()
    return ModelConfig(
        contextSize = (this[SettingsKeys.CONTEXT_SIZE] ?: defaults.contextSize)
            .takeIf { it in ModelConfig.CONTEXT_OPTIONS } ?: defaults.contextSize,
        threads = (this[SettingsKeys.THREADS] ?: defaults.threads).coerceIn(0, 8),
        autoUnloadMinutes = (this[SettingsKeys.AUTO_UNLOAD_MINUTES] ?: defaults.autoUnloadMinutes).coerceIn(0, 60),
        useMmap = this[SettingsKeys.USE_MMAP] ?: defaults.useMmap,
        quantizeKvCache = this[SettingsKeys.QUANTIZE_KV] ?: defaults.quantizeKvCache,
        reasoningByDefault = this[SettingsKeys.REASONING_DEFAULT] ?: defaults.reasoningByDefault,
    )
}

internal fun MutablePreferences.writeModelConfig(c: ModelConfig) {
    this[SettingsKeys.CONTEXT_SIZE] = c.contextSize
    this[SettingsKeys.THREADS] = c.threads
    this[SettingsKeys.AUTO_UNLOAD_MINUTES] = c.autoUnloadMinutes
    this[SettingsKeys.USE_MMAP] = c.useMmap
    this[SettingsKeys.QUANTIZE_KV] = c.quantizeKvCache
    this[SettingsKeys.REASONING_DEFAULT] = c.reasoningByDefault
}

private inline fun <reified E : Enum<E>> enumOrDefault(value: String?, default: E): E =
    value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
