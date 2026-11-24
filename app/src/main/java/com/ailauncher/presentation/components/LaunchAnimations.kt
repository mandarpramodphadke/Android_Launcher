package com.ailauncher.presentation.components

import android.app.ActivityOptions
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import com.ailauncher.domain.model.AppInfo
import kotlin.math.roundToInt

/**
 * Turns a tapped icon's bounds (in root coordinates, from onGloballyPositioned) into a
 * [LaunchRequest] whose ActivityOptions reveal the app from the icon (clip reveal, falling back to
 * a scale-up), plus the screen-space source bounds LauncherApps expects.
 */
@Composable
fun rememberLaunchRequestFactory(): (AppInfo, Rect) -> LaunchRequest {
    val view = LocalView.current
    return remember(view) {
        { app, bounds ->
            val left = bounds.left.roundToInt()
            val top = bounds.top.roundToInt()
            val width = bounds.width.roundToInt().coerceAtLeast(1)
            val height = bounds.height.roundToInt().coerceAtLeast(1)
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            val screenBounds = android.graphics.Rect(location[0] + left, location[1] + top, location[0] + left + width, location[1] + top + height)
            val options = runCatching { ActivityOptions.makeClipRevealAnimation(view, left, top, width, height) }
                .recoverCatching { ActivityOptions.makeScaleUpAnimation(view, left, top, width, height) }
                .getOrNull()?.toBundle()
            LaunchRequest(app, if (bounds.isEmpty) null else screenBounds, if (bounds.isEmpty) null else options)
        }
    }
}

/** Provided by the nav host so the home command bar can morph into the chat input bar. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

const val COMMAND_BAR_SHARED_KEY = "ai-command-bar"

/** Shared bounds between the home AI command bar and the chat input bar (no-op without a nav host). */
@Composable
fun Modifier.sharedCommandBar(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedCommandBar.sharedBounds(
            sharedContentState = rememberSharedContentState(COMMAND_BAR_SHARED_KEY),
            animatedVisibilityScope = visibility,
            boundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
