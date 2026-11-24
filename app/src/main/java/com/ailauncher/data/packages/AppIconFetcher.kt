package com.ailauncher.data.packages

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.ailauncher.domain.model.IconStyle

/**
 * Coil model for an app icon. [version] (the package's lastUpdateTime) is part of the cache key so
 * icons refresh automatically when an app updates; the style and theme colours are too, so changing
 * theme re-renders themed icons instead of serving stale bitmaps.
 */
data class AppIconData(
    val packageName: String,
    val activityName: String,
    val version: Long,
    val style: IconStyle = IconStyle.ORIGINAL,
    /** ARGB colours of themed icons: the tile and the glyph drawn on it. */
    val themedBackground: Int = 0,
    val themedForeground: Int = 0,
)

/**
 * Loads launcher icons from PackageManager off the main thread and rasterises them (including
 * adaptive icons) at the exact requested size, so Coil's memory cache holds right-sized bitmaps.
 *
 * Themed icons use the app's Android 13 monochrome layer, tinted with the theme and placed on a
 * theme-coloured tile; apps that ship no monochrome layer (and every app before Android 13) are
 * shown desaturated so the grid still reads as one palette.
 */
class AppIconFetcher(
    private val data: AppIconData,
    private val options: Options,
    private val packageManager: PackageManager,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val drawable = try {
            packageManager.getActivityIcon(ComponentName(data.packageName, data.activityName))
        } catch (_: PackageManager.NameNotFoundException) {
            try {
                packageManager.getApplicationIcon(data.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                packageManager.defaultActivityIcon
            }
        }
        val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }.coerceIn(16, MAX_SIZE_PX)
        val height = options.size.height.pxOrElse { width }.coerceIn(16, MAX_SIZE_PX)
        val bitmap = when (data.style) {
            IconStyle.ORIGINAL -> drawable.toBitmap(width, height)
            IconStyle.GRAYSCALE -> drawable.toBitmap(width, height).desaturated()
            IconStyle.THEMED -> {
                val themed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) themed(drawable) else null
                themed?.toBitmap(width, height) ?: drawable.toBitmap(width, height).desaturated()
            }
        }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun themed(drawable: Drawable): Drawable? {
        val monochrome = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null
        // The monochrome layer shares the foreground's geometry, so it slots in as a foreground.
        val glyph = monochrome.mutate().apply { setTint(data.themedForeground) }
        return AdaptiveIconDrawable(data.themedBackground.toDrawable(), glyph)
    }

    /** A greyscale copy; never mutates [this], which may be a bitmap shared with the system. */
    private fun Bitmap.desaturated(): Bitmap {
        val out = createBitmap(width, height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(this, 0f, 0f, paint)
        return out
    }

    class Factory(private val packageManager: PackageManager) : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, options, packageManager)
    }

    class Key : Keyer<AppIconData> {
        override fun key(data: AppIconData, options: Options): String = buildString {
            append("app-icon:").append(data.packageName).append('/').append(data.activityName).append('@').append(data.version)
            when (data.style) {
                IconStyle.ORIGINAL -> Unit
                IconStyle.GRAYSCALE -> append("#gray")
                IconStyle.THEMED -> append("#themed:")
                    .append(Integer.toHexString(data.themedBackground)).append(':')
                    .append(Integer.toHexString(data.themedForeground))
            }
        }
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 192
        const val MAX_SIZE_PX = 512
    }
}
