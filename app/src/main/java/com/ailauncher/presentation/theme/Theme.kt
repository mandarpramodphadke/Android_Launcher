package com.ailauncher.presentation.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ailauncher.domain.model.ThemeMode

private const val DARK_TEXT_LUMINANCE = 0.6f

private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF9333EA)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Violet,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = Color(0xFF0D9488),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFD8B4FE),
    secondaryContainer = Color(0xFF6B21A8),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = Color(0xFF5EEAD4),
)

@Composable
fun AiLauncherTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

/** Text colours for content drawn straight on the wallpaper (home screen). */
data class WallpaperTint(val content: Color, val secondary: Color, val shadow: Shadow?) {
    val clockStyle: TextStyle
        get() = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.Light, color = content, shadow = shadow, letterSpacing = (-2).sp)
}

/**
 * Picks dark or light text from the wallpaper's own colour hints (WallpaperColors), updating live
 * when the wallpaper changes, so the clock stays legible on any background.
 */
@Composable
fun rememberWallpaperTint(): WallpaperTint {
    val context = LocalContext.current
    var darkText by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val manager = WallpaperManager.getInstance(context)
        fun update(colors: WallpaperColors?) {
            darkText = when {
                colors == null -> false
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                // Android 10/11 have no colour hints: fall back to the primary colour's luminance.
                else -> colors.primaryColor.luminance() > DARK_TEXT_LUMINANCE
            }
        }
        update(runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull())
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) update(colors)
        }
        runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { manager.removeOnColorsChangedListener(listener) } }
    }
    return if (darkText) {
        WallpaperTint(content = Color(0xFF111111), secondary = Color(0xCC111111), shadow = null)
    } else {
        WallpaperTint(
            content = Color.White,
            secondary = Color(0xE6FFFFFF),
            shadow = Shadow(color = Color(0x66000000), blurRadius = 12f),
        )
    }
}
