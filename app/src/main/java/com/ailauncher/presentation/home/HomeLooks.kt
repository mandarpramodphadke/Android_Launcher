package com.ailauncher.presentation.home

import android.app.WallpaperManager
import android.os.Build
import com.ailauncher.BuildConfig
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/** How far the home screen is blurred behind the open drawer, folders and menus. */
internal val HOME_BLUR_RADIUS = 18.dp

/** A blur of [radiusPx] (Android 12+; null when there is nothing to blur, or before Android 12). */
internal fun homeBlur(radiusPx: Float): RenderEffect? =
    if (radiusPx < 0.5f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else BlurEffect(radiusPx, radiusPx, TileMode.Clamp)

/**
 * Moves the wallpaper a little as the home pages scroll, like most launchers do. Wallpapers that
 * are no wider than the screen don't move; live wallpapers decide for themselves.
 */
@Composable
internal fun WallpaperParallax(enabled: Boolean, pagerState: PagerState) {
    val view = LocalView.current
    LaunchedEffect(enabled, pagerState, view) {
        val manager = WallpaperManager.getInstance(view.context)
        if (!enabled) {
            // Back to the centre after the setting is turned off (builds without the feature
            // never touch the wallpaper offsets).
            if (BuildConfig.BLUR_EFFECTS) view.windowToken?.let { token -> runCatching { manager.setWallpaperOffsets(token, 0.5f, 0.5f) } }
            return@LaunchedEffect
        }
        snapshotFlow { (pagerState.currentPage + pagerState.currentPageOffsetFraction) to pagerState.pageCount }
            .distinctUntilChanged()
            .collect { (position, pages) ->
                val token = view.windowToken ?: return@collect
                val steps = (pages - 1).coerceAtLeast(1)
                val x = if (pages <= 1) 0.5f else (position / steps).coerceIn(0f, 1f)
                runCatching {
                    manager.setWallpaperOffsetSteps(1f / steps, 1f)
                    manager.setWallpaperOffsets(token, x, 0.5f)
                }
            }
    }
}
