package com.ailauncher.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Sets the status/navigation bar icon colour while the calling screen is on top.
 *
 * Applied each time the screen's lifecycle (its NavBackStackEntry) resumes and never "restored" on
 * dispose. During a navigation transition the incoming screen composes before the outgoing one is
 * disposed, so restoring a remembered value handed the bars a stale colour from an older screen
 * (for example dark icons over the dark settings page after visiting a light-wallpaper home
 * screen). Only the destination on top reaches RESUMED once a transition settles, so it wins.
 */
@Composable
fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val owner = LocalLifecycleOwner.current
    DisposableEffect(view, owner, darkIcons) {
        val window = view.context.findActivity()?.window
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = darkIcons
                    isAppearanceLightNavigationBars = darkIcons
                }
            }
        }
        // A lifecycle that is already resumed replays ON_RESUME to a new observer straight away,
        // so a change of darkIcons on the visible screen applies immediately.
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Bar icons that contrast with the current Material surface colour. */
@Composable
fun ThemedSystemBarIcons() {
    SystemBarIcons(darkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
}
