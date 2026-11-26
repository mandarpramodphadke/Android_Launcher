package com.ailauncher.presentation.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.ailauncher.BuildConfig
import com.ailauncher.domain.model.ClockFont
import com.ailauncher.domain.model.DockStyle
import com.ailauncher.domain.model.LabelSize
import com.ailauncher.domain.model.LauncherSettings

/**
 * DOCK_AND_GRID (1.8, full version first): the dock's look, the command bar inside it, the rows
 * on the home pages, and the size and font of app names.
 */
internal fun LazyListScope.dockAndGridSettings(settings: LauncherSettings, onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    if (!BuildConfig.DOCK_AND_GRID || settings.minimalMode) return
    item {
        SegmentedSetting(
            title = "Dock",
            options = listOf(DockStyle.PILL to "Pill", DockStyle.GLASS to "Glass", DockStyle.NONE to "None"),
            selected = settings.dockStyle,
            onSelect = { style -> onUpdate { it.copy(dockStyle = style) } },
        )
    }
    item {
        SwitchSetting(
            "Command bar in the dock",
            "One card at the bottom of the home screen, with your pinned apps above the AI command bar",
            settings.commandBarInDock,
        ) { on -> onUpdate { it.copy(commandBarInDock = on) } }
    }
    item {
        ChoiceSetting(
            "Home screen rows",
            // Rows with apps in them are always kept, so fewer rows never hide anything.
            listOf(LauncherSettings.AUTO_ROWS to "Automatic (as many as fit)") + LauncherSettings.HOME_ROW_CHOICES.map { it to "$it rows" },
            settings.homeRows,
        ) { rows -> onUpdate { it.copy(homeRows = rows) } }
    }
    item {
        SegmentedSetting(
            title = "App name size",
            options = listOf(LabelSize.SMALL to "Small", LabelSize.MEDIUM to "Medium", LabelSize.LARGE to "Large"),
            selected = settings.labelSize,
            onSelect = { size -> onUpdate { it.copy(labelSize = size) } },
        )
    }
    item {
        ChoiceSetting(
            "App name font",
            listOf(
                ClockFont.DEFAULT to "Default",
                ClockFont.CONDENSED to "Condensed",
                ClockFont.SERIF to "Serif",
                ClockFont.MONO to "Monospace",
                ClockFont.BOLD to "Bold",
            ),
            settings.labelFont,
        ) { font -> onUpdate { it.copy(labelFont = font) } }
    }
}
