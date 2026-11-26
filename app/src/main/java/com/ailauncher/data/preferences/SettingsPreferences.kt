package com.ailauncher.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.GestureAction
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
    val ICON_PACK = stringPreferencesKey("icon_pack")
    val ICON_SHAPE = stringPreferencesKey("icon_shape")
    val ICON_SIZE = intPreferencesKey("icon_size_percent")
    val HOME_LABELS = booleanPreferencesKey("home_labels")
    val CLOCK_FACE = stringPreferencesKey("clock_face")
    val CLOCK_FONT = stringPreferencesKey("clock_font")
    val CLOCK_BATTERY = booleanPreferencesKey("clock_battery")
    val BLUR_EFFECTS = booleanPreferencesKey("blur_effects")
    val WALLPAPER_PARALLAX = booleanPreferencesKey("wallpaper_parallax")
    val MEDIA_CONTROLS = booleanPreferencesKey("media_controls")
    val INSTANT_SEARCH = booleanPreferencesKey("instant_search")
    val ASSISTANT_MEMORY = booleanPreferencesKey("assistant_memory")
    val SPOKEN_REPLIES = stringPreferencesKey("spoken_replies")
    val SWIPE_DOWN_ACTION = stringPreferencesKey("swipe_down_action")
    val DOUBLE_TAP_ACTION = stringPreferencesKey("double_tap_action")
    val TWO_FINGER_ACTION = stringPreferencesKey("two_finger_action")
    val SWIPE_DOWN_APP = stringPreferencesKey("swipe_down_app")
    val DOUBLE_TAP_APP = stringPreferencesKey("double_tap_app")
    val TWO_FINGER_APP = stringPreferencesKey("two_finger_app")
    val DOCK_STYLE = stringPreferencesKey("dock_style")
    val COMMAND_BAR_IN_DOCK = booleanPreferencesKey("command_bar_in_dock")
    val HOME_ROWS = intPreferencesKey("home_rows")
    val LABEL_SIZE = stringPreferencesKey("label_size")
    val LABEL_FONT = stringPreferencesKey("label_font")

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
        iconPackPackage = this[SettingsKeys.ICON_PACK]?.takeIf { it.isNotBlank() },
        iconShape = enumOrDefault(this[SettingsKeys.ICON_SHAPE], defaults.iconShape),
        iconSizePercent = (this[SettingsKeys.ICON_SIZE] ?: defaults.iconSizePercent).coerceIn(LauncherSettings.MIN_ICON_SIZE, LauncherSettings.MAX_ICON_SIZE),
        showHomeLabels = this[SettingsKeys.HOME_LABELS] ?: defaults.showHomeLabels,
        clockFace = enumOrDefault(this[SettingsKeys.CLOCK_FACE], defaults.clockFace),
        clockFont = enumOrDefault(this[SettingsKeys.CLOCK_FONT], defaults.clockFont),
        clockShowsBattery = this[SettingsKeys.CLOCK_BATTERY] ?: defaults.clockShowsBattery,
        blurEffects = this[SettingsKeys.BLUR_EFFECTS] ?: defaults.blurEffects,
        wallpaperParallax = this[SettingsKeys.WALLPAPER_PARALLAX] ?: defaults.wallpaperParallax,
        mediaControls = this[SettingsKeys.MEDIA_CONTROLS] ?: defaults.mediaControls,
        instantSearch = this[SettingsKeys.INSTANT_SEARCH] ?: defaults.instantSearch,
        assistantMemory = this[SettingsKeys.ASSISTANT_MEMORY] ?: defaults.assistantMemory,
        spokenReplies = enumOrDefault(this[SettingsKeys.SPOKEN_REPLIES], defaults.spokenReplies),
        swipeDownAction = this[SettingsKeys.SWIPE_DOWN_ACTION]?.let { name -> GestureAction.entries.firstOrNull { it.name == name } },
        doubleTapAction = this[SettingsKeys.DOUBLE_TAP_ACTION]?.let { name -> GestureAction.entries.firstOrNull { it.name == name } },
        twoFingerSwipeAction = enumOrDefault(this[SettingsKeys.TWO_FINGER_ACTION], defaults.twoFingerSwipeAction),
        swipeDownApp = this[SettingsKeys.SWIPE_DOWN_APP],
        doubleTapApp = this[SettingsKeys.DOUBLE_TAP_APP],
        twoFingerApp = this[SettingsKeys.TWO_FINGER_APP],
        dockStyle = enumOrDefault(this[SettingsKeys.DOCK_STYLE], defaults.dockStyle),
        commandBarInDock = this[SettingsKeys.COMMAND_BAR_IN_DOCK] ?: defaults.commandBarInDock,
        homeRows = this[SettingsKeys.HOME_ROWS]?.takeIf { it in LauncherSettings.HOME_ROW_CHOICES } ?: LauncherSettings.AUTO_ROWS,
        labelSize = enumOrDefault(this[SettingsKeys.LABEL_SIZE], defaults.labelSize),
        labelFont = enumOrDefault(this[SettingsKeys.LABEL_FONT], defaults.labelFont),
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
    if (s.iconPackPackage.isNullOrBlank()) remove(SettingsKeys.ICON_PACK) else this[SettingsKeys.ICON_PACK] = s.iconPackPackage
    this[SettingsKeys.ICON_SHAPE] = s.iconShape.name
    this[SettingsKeys.ICON_SIZE] = s.iconSizePercent.coerceIn(LauncherSettings.MIN_ICON_SIZE, LauncherSettings.MAX_ICON_SIZE)
    this[SettingsKeys.HOME_LABELS] = s.showHomeLabels
    this[SettingsKeys.CLOCK_FACE] = s.clockFace.name
    this[SettingsKeys.CLOCK_FONT] = s.clockFont.name
    this[SettingsKeys.CLOCK_BATTERY] = s.clockShowsBattery
    this[SettingsKeys.BLUR_EFFECTS] = s.blurEffects
    this[SettingsKeys.WALLPAPER_PARALLAX] = s.wallpaperParallax
    this[SettingsKeys.MEDIA_CONTROLS] = s.mediaControls
    this[SettingsKeys.INSTANT_SEARCH] = s.instantSearch
    this[SettingsKeys.ASSISTANT_MEMORY] = s.assistantMemory
    this[SettingsKeys.SPOKEN_REPLIES] = s.spokenReplies.name
    setOrRemove(SettingsKeys.SWIPE_DOWN_ACTION, s.swipeDownAction?.name)
    setOrRemove(SettingsKeys.DOUBLE_TAP_ACTION, s.doubleTapAction?.name)
    this[SettingsKeys.TWO_FINGER_ACTION] = s.twoFingerSwipeAction.name
    setOrRemove(SettingsKeys.SWIPE_DOWN_APP, s.swipeDownApp)
    setOrRemove(SettingsKeys.DOUBLE_TAP_APP, s.doubleTapApp)
    setOrRemove(SettingsKeys.TWO_FINGER_APP, s.twoFingerApp)
    this[SettingsKeys.DOCK_STYLE] = s.dockStyle.name
    this[SettingsKeys.COMMAND_BAR_IN_DOCK] = s.commandBarInDock
    this[SettingsKeys.HOME_ROWS] = s.homeRows
    this[SettingsKeys.LABEL_SIZE] = s.labelSize.name
    this[SettingsKeys.LABEL_FONT] = s.labelFont.name
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

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value != null) this[key] = value else remove(key)
}

private inline fun <reified E : Enum<E>> enumOrDefault(value: String?, default: E): E =
    value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
