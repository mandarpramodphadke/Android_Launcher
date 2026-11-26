package com.ailauncher.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.GestureAction
import com.ailauncher.domain.model.HomeGesture
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.actionFor
import com.ailauncher.domain.model.appFor
import com.ailauncher.domain.model.withGesture

/**
 * CUSTOM_GESTURES (1.8, full version first): what swipe down, double-tap and a two-finger swipe down
 * do on the home screen. Swipe up always opens the app drawer.
 */
internal fun LazyListScope.gestureSettings(
    settings: LauncherSettings,
    apps: List<AppInfo>,
    systemInfoAvailable: Boolean,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onPickApp: (HomeGesture) -> Unit,
) {
    HomeGesture.entries.forEach { gesture ->
        item(key = "gesture-$gesture") {
            val appLabel = settings.appFor(gesture)?.let { key -> apps.firstOrNull { it.componentKey == key }?.label }
            ChoiceSetting(
                title = gestureTitle(gesture),
                options = GestureAction.entries
                    .filter { it != GestureAction.SYSTEM_INFO || systemInfoAvailable }
                    .map { it to actionLabel(it, appLabel) },
                selected = settings.actionFor(gesture),
                onSelect = { action ->
                    if (action == GestureAction.OPEN_APP) onPickApp(gesture) else onUpdate { it.withGesture(gesture, action) }
                },
            )
        }
    }
}

internal fun gestureTitle(gesture: HomeGesture) = when (gesture) {
    HomeGesture.SWIPE_DOWN -> "Swipe down"
    HomeGesture.DOUBLE_TAP -> "Double-tap"
    HomeGesture.TWO_FINGER_SWIPE_DOWN -> "Swipe down with two fingers"
}

private fun actionLabel(action: GestureAction, appLabel: String?) = when (action) {
    GestureAction.NOTHING -> "Nothing"
    GestureAction.NOTIFICATIONS -> "Notifications"
    GestureAction.QUICK_SETTINGS -> "Quick Settings"
    GestureAction.LOCK -> "Lock the screen"
    GestureAction.ASSISTANT -> "Open the assistant"
    GestureAction.SEARCH -> "Search apps"
    GestureAction.APP_DRAWER -> "App drawer"
    GestureAction.OPEN_APP -> appLabel?.let { "Open $it" } ?: "Open an app…"
    GestureAction.FLASHLIGHT -> "Flashlight on or off"
    GestureAction.SYSTEM_INFO -> "System info"
    GestureAction.PLANNER -> "To-dos and planner"
}

/** Picks the app a gesture opens. */
@Composable
internal fun GestureAppPicker(gesture: HomeGesture, apps: List<AppInfo>, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${gestureTitle(gesture)} opens") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.componentKey }) { app ->
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
