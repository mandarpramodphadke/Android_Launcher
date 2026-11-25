package com.ailauncher.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.AssistantProfile
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
    val HOME_GRID_COLUMNS = intPreferencesKey("home_grid_columns")
    val UNUSED_APP_DAYS = intPreferencesKey("unused_app_threshold_days")
    val STALE_FILE_DAYS = intPreferencesKey("stale_file_threshold_days")
    val NOTIFICATION_DIGEST = booleanPreferencesKey("notification_digest")
    val NOTIFICATION_BADGES = booleanPreferencesKey("notification_badges")
    val SUGGESTED_APPS = booleanPreferencesKey("suggested_apps")
    val CLOCK_FORMAT = intPreferencesKey("clock_format") // 0 = system, 12, 24
    val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
    val SWIPE_DOWN_NOTIFICATIONS = booleanPreferencesKey("swipe_down_notifications")
    val SHOW_AI_BAR = booleanPreferencesKey("show_ai_bar")
    val AUTO_CONFIRM_LAUNCH = booleanPreferencesKey("auto_confirm_launch")
    val REMINDERS_TO_CALENDAR = booleanPreferencesKey("reminders_to_calendar")
    val HAPTICS = booleanPreferencesKey("haptics")
    val CHAT_BACKGROUND = stringPreferencesKey("chat_background")
    val CHAT_BACKGROUND_DIM = floatPreferencesKey("chat_background_dim")
    val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
    val ASSISTANT_LENGTH = stringPreferencesKey("assistant_length")
    val ASSISTANT_TONE = stringPreferencesKey("assistant_tone")
    val ASSISTANT_INSTRUCTIONS = stringPreferencesKey("assistant_instructions")
    val HIDDEN_CALENDARS = stringSetPreferencesKey("hidden_calendar_ids")
    val REMINDER_CALENDAR = longPreferencesKey("reminder_calendar_id")

    val CONTEXT_SIZE = intPreferencesKey("model_context_size")
    val THREADS = intPreferencesKey("model_threads")
    val AUTO_UNLOAD_MINUTES = intPreferencesKey("model_auto_unload_minutes")
    val USE_MMAP = booleanPreferencesKey("model_use_mmap")
    val QUANTIZE_KV = booleanPreferencesKey("model_quantize_kv")
    val REASONING_DEFAULT = booleanPreferencesKey("model_reasoning_default")
    val ACTIVE_MODEL = stringPreferencesKey("model_active_id")
    val GPU_OFFLOAD = booleanPreferencesKey("model_gpu_offload")
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
        gridColumns = (this[SettingsKeys.GRID_COLUMNS] ?: defaults.gridColumns).coerceIn(4, 5),
        homeGridColumns = (this[SettingsKeys.HOME_GRID_COLUMNS] ?: defaults.homeGridColumns).coerceIn(4, 5),
        unusedAppThresholdDays = (this[SettingsKeys.UNUSED_APP_DAYS] ?: defaults.unusedAppThresholdDays).coerceIn(1, 365),
        staleFileThresholdDays = (this[SettingsKeys.STALE_FILE_DAYS] ?: defaults.staleFileThresholdDays).coerceIn(1, 3650),
        notificationDigestEnabled = this[SettingsKeys.NOTIFICATION_DIGEST] ?: defaults.notificationDigestEnabled,
        notificationBadges = this[SettingsKeys.NOTIFICATION_BADGES] ?: defaults.notificationBadges,
        showSuggestedApps = this[SettingsKeys.SUGGESTED_APPS] ?: defaults.showSuggestedApps,
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
        chatBackground = enumOrDefault(this[SettingsKeys.CHAT_BACKGROUND], defaults.chatBackground),
        chatBackgroundDim = (this[SettingsKeys.CHAT_BACKGROUND_DIM] ?: defaults.chatBackgroundDim).coerceIn(0f, 0.9f),
        assistant = AssistantProfile(
            name = this[SettingsKeys.ASSISTANT_NAME]?.take(AssistantProfile.MAX_NAME) ?: defaults.assistant.name,
            length = enumOrDefault(this[SettingsKeys.ASSISTANT_LENGTH], defaults.assistant.length),
            tone = enumOrDefault(this[SettingsKeys.ASSISTANT_TONE], defaults.assistant.tone),
            instructions = this[SettingsKeys.ASSISTANT_INSTRUCTIONS]?.take(AssistantProfile.MAX_INSTRUCTIONS) ?: defaults.assistant.instructions,
        ),
        hiddenCalendarIds = this[SettingsKeys.HIDDEN_CALENDARS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        reminderCalendarId = this[SettingsKeys.REMINDER_CALENDAR]?.takeIf { it > 0 },
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
    this[SettingsKeys.GRID_COLUMNS] = s.gridColumns.coerceIn(4, 5)
    this[SettingsKeys.HOME_GRID_COLUMNS] = s.homeGridColumns.coerceIn(4, 5)
    this[SettingsKeys.UNUSED_APP_DAYS] = s.unusedAppThresholdDays.coerceIn(1, 365)
    this[SettingsKeys.STALE_FILE_DAYS] = s.staleFileThresholdDays.coerceIn(1, 3650)
    this[SettingsKeys.NOTIFICATION_DIGEST] = s.notificationDigestEnabled
    this[SettingsKeys.NOTIFICATION_BADGES] = s.notificationBadges
    this[SettingsKeys.SUGGESTED_APPS] = s.showSuggestedApps
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
    this[SettingsKeys.CHAT_BACKGROUND] = s.chatBackground.name
    this[SettingsKeys.CHAT_BACKGROUND_DIM] = s.chatBackgroundDim.coerceIn(0f, 0.9f)
    this[SettingsKeys.ASSISTANT_NAME] = s.assistant.name.take(AssistantProfile.MAX_NAME)
    this[SettingsKeys.ASSISTANT_LENGTH] = s.assistant.length.name
    this[SettingsKeys.ASSISTANT_TONE] = s.assistant.tone.name
    this[SettingsKeys.ASSISTANT_INSTRUCTIONS] = s.assistant.instructions.take(AssistantProfile.MAX_INSTRUCTIONS)
    this[SettingsKeys.HIDDEN_CALENDARS] = s.hiddenCalendarIds.map { it.toString() }.toSet()
    if (s.reminderCalendarId != null) this[SettingsKeys.REMINDER_CALENDAR] = s.reminderCalendarId else remove(SettingsKeys.REMINDER_CALENDAR)
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
        activeModelId = this[SettingsKeys.ACTIVE_MODEL]?.takeIf { it.isNotBlank() } ?: defaults.activeModelId,
        gpuOffload = this[SettingsKeys.GPU_OFFLOAD] ?: defaults.gpuOffload,
    )
}

internal fun MutablePreferences.writeModelConfig(c: ModelConfig) {
    this[SettingsKeys.CONTEXT_SIZE] = c.contextSize
    this[SettingsKeys.THREADS] = c.threads
    this[SettingsKeys.AUTO_UNLOAD_MINUTES] = c.autoUnloadMinutes
    this[SettingsKeys.USE_MMAP] = c.useMmap
    this[SettingsKeys.QUANTIZE_KV] = c.quantizeKvCache
    this[SettingsKeys.REASONING_DEFAULT] = c.reasoningByDefault
    this[SettingsKeys.ACTIVE_MODEL] = c.activeModelId
    this[SettingsKeys.GPU_OFFLOAD] = c.gpuOffload
}

private inline fun <reified E : Enum<E>> enumOrDefault(value: String?, default: E): E =
    value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
