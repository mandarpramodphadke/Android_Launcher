package com.ailauncher.data.preferences

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.AssistantTone
import com.ailauncher.domain.model.ChatBackground
import com.ailauncher.domain.model.ResponseLength
import com.ailauncher.domain.model.HomeTextColor
import com.ailauncher.domain.model.IconStyle
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsPreferencesTest {

    @Test
    fun emptyStoreGivesDefaults() {
        assertThat(emptyPreferences().toLauncherSettings()).isEqualTo(LauncherSettings())
    }

    @Test
    fun roundTripsEveryAppearanceSetting() {
        val settings = LauncherSettings(
            themeMode = ThemeMode.DARK,
            appTheme = AppTheme.NEON,
            minimalMode = true,
            homeTextColor = HomeTextColor.DARK,
            homeTextShade = false,
            iconStyle = IconStyle.THEMED,
            use24HourClock = true,
        )
        val prefs = mutablePreferencesOf().apply { writeLauncherSettings(settings) }
        assertThat(prefs.toLauncherSettings()).isEqualTo(settings)
    }

    @Test
    fun roundTripsChatAssistantAndCalendarSettings() {
        val settings = LauncherSettings(
            chatBackground = ChatBackground.PHOTO,
            chatBackgroundDim = 0.7f,
            assistant = AssistantProfile("Nova", ResponseLength.SHORT, AssistantTone.PLAYFUL, "Use metric units."),
            hiddenCalendarIds = setOf(3L, 12L),
            reminderCalendarId = 7L,
        )
        val prefs = mutablePreferencesOf().apply { writeLauncherSettings(settings) }
        assertThat(prefs.toLauncherSettings()).isEqualTo(settings)
        // Back to "automatic" removes the stored calendar.
        prefs.writeLauncherSettings(settings.copy(reminderCalendarId = null))
        assertThat(prefs.toLauncherSettings().reminderCalendarId).isNull()
    }

    @Test
    fun migratesTheOldWallpaperColoursSwitch() {
        // 1.0 had a single "Wallpaper colours" switch; turning it off meant the built-in palette.
        assertThat(preferencesOf(SettingsKeys.DYNAMIC_COLOR to false).toLauncherSettings().appTheme).isEqualTo(AppTheme.CLASSIC)
        assertThat(preferencesOf(SettingsKeys.DYNAMIC_COLOR to true).toLauncherSettings().appTheme).isEqualTo(AppTheme.MATERIAL_YOU)
        // Once a theme has been chosen the legacy flag is ignored, and the next write drops it.
        val chosen = mutablePreferencesOf(SettingsKeys.DYNAMIC_COLOR to false, SettingsKeys.APP_THEME to "COLORFUL")
        assertThat(chosen.toLauncherSettings().appTheme).isEqualTo(AppTheme.COLORFUL)
        chosen.writeLauncherSettings(chosen.toLauncherSettings())
        assertThat(chosen[SettingsKeys.DYNAMIC_COLOR]).isNull()
    }

    @Test
    fun unknownValuesFallBackToDefaults() {
        val prefs = preferencesOf(
            SettingsKeys.APP_THEME to "VAPORWAVE",
            SettingsKeys.ICON_STYLE to "",
            SettingsKeys.GRID_COLUMNS to 42,
        )
        val settings = prefs.toLauncherSettings()
        assertThat(settings.appTheme).isEqualTo(AppTheme.MATERIAL_YOU)
        assertThat(settings.iconStyle).isEqualTo(IconStyle.ORIGINAL)
        assertThat(settings.gridColumns).isEqualTo(5)
    }
}
