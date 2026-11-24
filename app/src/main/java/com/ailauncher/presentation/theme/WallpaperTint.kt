package com.ailauncher.presentation.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import com.ailauncher.domain.model.HomeTextColor

/**
 * Colours for content drawn straight on the wallpaper (home screen clock, chips, favourites).
 *
 * The system only reports whether the wallpaper *as a whole* suits dark text, but the area behind
 * the clock or dock can still be dark (or light). So the text always carries a contrasting halo,
 * chips get a backing of the opposite polarity, and optional gradients ([scrim]) shade the regions
 * that hold text. Together these keep text legible no matter which part of the image it sits on.
 */
@Immutable
data class WallpaperTint(
    val darkText: Boolean,
    val content: Color,
    val secondary: Color,
    /** Light halo around dark text, soft drop shadow under light text. */
    val shadow: Shadow,
    /** Backing for chips on the wallpaper: light behind dark text, dark behind light text. */
    val chip: Color,
    /** Base colour of the readability gradients at the top and bottom of the home screen. */
    val scrim: Color,
) {
    companion object {
        val Light = WallpaperTint(
            darkText = false,
            content = Color.White,
            secondary = Color(0xF2FFFFFF),
            shadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 2f), blurRadius = 14f),
            chip = Color(0x73000000),
            scrim = Color.Black,
        )
        val Dark = WallpaperTint(
            darkText = true,
            content = Color(0xFF111111),
            secondary = Color(0xFF2E2E2E),
            shadow = Shadow(color = Color(0xD9FFFFFF), offset = Offset.Zero, blurRadius = 16f),
            chip = Color(0x9EFFFFFF),
            scrim = Color.White,
        )
    }
}

object WallpaperPolarity {
    private const val LIGHT_PRIMARY = 0.6f
    private const val LIGHT_OTHERS = 0.45f

    /**
     * Whether text on the wallpaper should be dark.
     *
     * @param supportsDarkTextHint `HINT_SUPPORTS_DARK_TEXT` (Android 12+), null where the platform has
     *   no public hint. The system sets it only when the image is bright with almost no dark areas.
     * @param colorLuminances relative luminance of the wallpaper's primary, secondary and tertiary
     *   colours, most dominant first. Used without a hint: dark text only when every extracted colour
     *   is light, so a bright wallpaper with a dark band keeps light, shadowed text.
     */
    fun darkText(override: HomeTextColor, supportsDarkTextHint: Boolean?, colorLuminances: List<Float>): Boolean = when (override) {
        HomeTextColor.LIGHT -> false
        HomeTextColor.DARK -> true
        HomeTextColor.AUTO -> supportsDarkTextHint ?: (
            colorLuminances.isNotEmpty() &&
                colorLuminances.first() > LIGHT_PRIMARY &&
                colorLuminances.all { it > LIGHT_OTHERS }
            )
    }
}

/** Tracks the wallpaper (live, including wallpaper changes) and picks text colours for it. */
@Composable
fun rememberWallpaperTint(override: HomeTextColor): WallpaperTint {
    val context = LocalContext.current
    var hint by remember { mutableStateOf<Boolean?>(false) }
    var luminances by remember { mutableStateOf(emptyList<Float>()) }
    DisposableEffect(context) {
        val manager = WallpaperManager.getInstance(context)
        fun update(colors: WallpaperColors?) {
            hint = when {
                colors == null -> false // unknown (e.g. some live wallpapers): light text + shadow
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                else -> null
            }
            luminances = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor).map { it.luminance() }
        }
        update(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull())
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) update(colors)
        }
        runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
    }
    return if (WallpaperPolarity.darkText(override, hint, luminances)) WallpaperTint.Dark else WallpaperTint.Light
}
