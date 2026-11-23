package com.ailauncher.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ThemeMode
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/**
 * Jetpack DataStore (Preferences) backing for launcher settings and the model configuration.
 * Corrupt or unreadable files degrade to defaults instead of crashing the home screen.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val store = context.launcherDataStore

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val MODEL_SETUP_SEEN = booleanPreferencesKey("model_setup_seen")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
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

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val launcherSettings: Flow<LauncherSettings> =
        data.map { it.toLauncherSettings() }.distinctUntilChanged()

    override val modelConfig: Flow<ModelConfig> =
        data.map { it.toModelConfig() }.distinctUntilChanged()

    override suspend fun updateLauncherSettings(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toLauncherSettings())) }
    }

    override suspend fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) {
        store.edit { prefs -> prefs.write(transform(prefs.toModelConfig())) }
    }

    private fun Preferences.toLauncherSettings(): LauncherSettings {
        val defaults = LauncherSettings()
        return LauncherSettings(
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            modelSetupSeen = this[Keys.MODEL_SETUP_SEEN] ?: defaults.modelSetupSeen,
            themeMode = enumOrDefault(this[Keys.THEME_MODE], defaults.themeMode),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            drawerLayout = enumOrDefault(this[Keys.DRAWER_LAYOUT], defaults.drawerLayout),
            drawerTab = enumOrDefault(this[Keys.DRAWER_TAB], defaults.drawerTab),
            gridColumns = (this[Keys.GRID_COLUMNS] ?: defaults.gridColumns).coerceIn(3, 6),
            use24HourClock = when (this[Keys.CLOCK_FORMAT]) {
                12 -> false
                24 -> true
                else -> null
            },
            doubleTapToLock = this[Keys.DOUBLE_TAP_LOCK] ?: defaults.doubleTapToLock,
            swipeDownForNotifications = this[Keys.SWIPE_DOWN_NOTIFICATIONS] ?: defaults.swipeDownForNotifications,
            showAiCommandBar = this[Keys.SHOW_AI_BAR] ?: defaults.showAiCommandBar,
            autoConfirmAppLaunch = this[Keys.AUTO_CONFIRM_LAUNCH] ?: defaults.autoConfirmAppLaunch,
            saveRemindersToCalendar = this[Keys.REMINDERS_TO_CALENDAR] ?: defaults.saveRemindersToCalendar,
            hapticFeedback = this[Keys.HAPTICS] ?: defaults.hapticFeedback,
        )
    }

    private fun MutablePreferences.write(s: LauncherSettings) {
        this[Keys.ONBOARDING_COMPLETED] = s.onboardingCompleted
        this[Keys.MODEL_SETUP_SEEN] = s.modelSetupSeen
        this[Keys.THEME_MODE] = s.themeMode.name
        this[Keys.DYNAMIC_COLOR] = s.dynamicColor
        this[Keys.DRAWER_LAYOUT] = s.drawerLayout.name
        this[Keys.DRAWER_TAB] = s.drawerTab.name
        this[Keys.GRID_COLUMNS] = s.gridColumns.coerceIn(3, 6)
        this[Keys.CLOCK_FORMAT] = when (s.use24HourClock) {
            true -> 24
            false -> 12
            null -> 0
        }
        this[Keys.DOUBLE_TAP_LOCK] = s.doubleTapToLock
        this[Keys.SWIPE_DOWN_NOTIFICATIONS] = s.swipeDownForNotifications
        this[Keys.SHOW_AI_BAR] = s.showAiCommandBar
        this[Keys.AUTO_CONFIRM_LAUNCH] = s.autoConfirmAppLaunch
        this[Keys.REMINDERS_TO_CALENDAR] = s.saveRemindersToCalendar
        this[Keys.HAPTICS] = s.hapticFeedback
    }

    private fun Preferences.toModelConfig(): ModelConfig {
        val defaults = ModelConfig()
        return ModelConfig(
            contextSize = (this[Keys.CONTEXT_SIZE] ?: defaults.contextSize)
                .takeIf { it in ModelConfig.CONTEXT_OPTIONS } ?: defaults.contextSize,
            threads = (this[Keys.THREADS] ?: defaults.threads).coerceIn(0, 8),
            autoUnloadMinutes = (this[Keys.AUTO_UNLOAD_MINUTES] ?: defaults.autoUnloadMinutes).coerceIn(0, 60),
            useMmap = this[Keys.USE_MMAP] ?: defaults.useMmap,
            quantizeKvCache = this[Keys.QUANTIZE_KV] ?: defaults.quantizeKvCache,
            reasoningByDefault = this[Keys.REASONING_DEFAULT] ?: defaults.reasoningByDefault,
        )
    }

    private fun MutablePreferences.write(c: ModelConfig) {
        this[Keys.CONTEXT_SIZE] = c.contextSize
        this[Keys.THREADS] = c.threads
        this[Keys.AUTO_UNLOAD_MINUTES] = c.autoUnloadMinutes
        this[Keys.USE_MMAP] = c.useMmap
        this[Keys.QUANTIZE_KV] = c.quantizeKvCache
        this[Keys.REASONING_DEFAULT] = c.reasoningByDefault
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
}
