package com.ailauncher.presentation.settings

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ailauncher.BuildConfig
import com.ailauncher.data.packages.IconPackInfo
import com.ailauncher.domain.model.ClockFace
import com.ailauncher.domain.model.ClockFont
import com.ailauncher.domain.model.IconShape
import com.ailauncher.domain.model.LauncherSettings

/**
 * Settings ▸ Appearance additions of 1.7, each only where its feature is on in this build (full
 * version first): icon pack, shape, size and home labels; clock face, font and battery; blur and
 * wallpaper motion.
 */
internal fun LazyListScope.looksSettings(
    settings: LauncherSettings,
    iconPacks: List<IconPackInfo>,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onFindIconPacks: () -> Unit,
) {
    if (BuildConfig.ICON_CUSTOMIZATION && !settings.minimalMode) {
        item {
            val installed = iconPacks.any { it.packageName == settings.iconPackPackage }
            val options = buildList<Pair<String?, String>> {
                add(null to "None (the apps' own icons)")
                iconPacks.forEach { add(it.packageName to it.label) }
                // A pack that was uninstalled stays selectable until changed.
                if (settings.iconPackPackage != null && !installed) add(settings.iconPackPackage to "${settings.iconPackPackage} (not installed)")
            }
            if (iconPacks.isEmpty() && settings.iconPackPackage == null) {
                ActionSetting(
                    "Icon pack",
                    "No icon packs installed yet. Tap to find one in the Play Store; it appears here once installed.",
                    onClick = onFindIconPacks,
                )
            } else {
                ChoiceSetting("Icon pack", options, settings.iconPackPackage) { pack -> onUpdate { it.copy(iconPackPackage = pack) } }
            }
        }
        item {
            ChoiceSetting(
                "Icon shape",
                listOf(
                    IconShape.SYSTEM to "Phone default",
                    IconShape.CIRCLE to "Circle",
                    IconShape.SQUIRCLE to "Squircle",
                    IconShape.ROUNDED_SQUARE to "Rounded square",
                    IconShape.TEARDROP to "Teardrop",
                ),
                settings.iconShape,
            ) { shape -> onUpdate { it.copy(iconShape = shape) } }
        }
        item {
            IconSizeSetting(settings.iconSizePercent) { percent -> onUpdate { it.copy(iconSizePercent = percent) } }
        }
        item {
            SwitchSetting("App names on the home screen", "Off: icons only on the home pages (the drawer keeps its names)", settings.showHomeLabels) { on ->
                onUpdate { it.copy(showHomeLabels = on) }
            }
        }
    }
    if (BuildConfig.CLOCK_STYLES) {
        item {
            ChoiceSetting(
                "Clock style",
                listOf(
                    ClockFace.CLASSIC to "Classic: large time, date below",
                    ClockFace.STACKED to "Stacked: hours above minutes",
                    ClockFace.ANALOG to "Analog: a dial with hands",
                    ClockFace.WORDS to "Words: \"Four thirty-seven\"",
                    ClockFace.SLIM to "Slim: one thin line",
                ),
                settings.clockFace,
            ) { face -> onUpdate { it.copy(clockFace = face) } }
        }
        item {
            ChoiceSetting(
                "Clock font",
                listOf(
                    ClockFont.DEFAULT to "Default",
                    ClockFont.CONDENSED to "Condensed",
                    ClockFont.SERIF to "Serif",
                    ClockFont.MONO to "Monospace",
                    ClockFont.BOLD to "Bold",
                ),
                settings.clockFont,
            ) { font -> onUpdate { it.copy(clockFont = font) } }
        }
        item {
            SwitchSetting("Battery by the date", "Shows the battery level (and charging) under the clock", settings.clockShowsBattery) { on ->
                onUpdate { it.copy(clockShowsBattery = on) }
            }
        }
    }
    if (BuildConfig.BLUR_EFFECTS) {
        item {
            val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SwitchSetting(
                "Blur effects",
                if (supported) "Blurs the home screen behind the app drawer, folders and menus" else "Needs Android 12 or later",
                settings.blurEffects && supported,
            ) { on -> if (supported) onUpdate { it.copy(blurEffects = on) } }
        }
        if (!settings.minimalMode) {
            item {
                SwitchSetting("Wallpaper motion", "The wallpaper moves a little as you swipe between home pages (wide wallpapers only)", settings.wallpaperParallax) { on ->
                    onUpdate { it.copy(wallpaperParallax = on) }
                }
            }
        }
    }
}

/** Settings ▸ Home screen additions of 1.7: music controls and instant search. */
internal fun LazyListScope.homeExtrasSettings(
    settings: LauncherSettings,
    notificationAccess: Boolean,
    contactsAccess: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onAllowNotificationAccess: () -> Unit,
    onAllowContacts: () -> Unit,
) {
    if (BuildConfig.MEDIA_CONTROLS) {
        item {
            SwitchSetting(
                "Music controls",
                when {
                    !settings.mediaControls -> "Show what's playing under the clock, with play / pause and skip"
                    notificationAccess -> "What's playing shows under the clock, with play / pause and skip"
                    else -> "Needs notification access to see what's playing (the digest can stay off)"
                },
                settings.mediaControls,
            ) { on -> onUpdate { it.copy(mediaControls = on) } }
        }
        if (settings.mediaControls && !notificationAccess) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = onAllowNotificationAccess) { Text("Allow notification access") }
                }
            }
        }
    }
    if (BuildConfig.INSTANT_SEARCH) {
        item {
            SwitchSetting(
                "Instant search",
                "As you type in the command bar: contacts, phone settings, to-dos, files, sums (\"15% of 2400\") and conversions (\"5 km in miles\"). All on the phone.",
                settings.instantSearch,
            ) { on -> onUpdate { it.copy(instantSearch = on) } }
        }
        if (settings.instantSearch && !contactsAccess) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = onAllowContacts) { Text("Allow contacts, to find people as you type") }
                }
            }
        }
    }
}

@Composable
private fun IconSizeSetting(percent: Int, onChange: (Int) -> Unit) {
    val sizes = listOf(80, 90, 100, 110, 120)
    SegmentedSetting(
        title = "Icon size",
        options = sizes.map { it to "$it%" },
        // An older value between the steps shows as the nearest one.
        selected = sizes.minBy { kotlin.math.abs(it - percent) },
        onSelect = onChange,
    )
}
