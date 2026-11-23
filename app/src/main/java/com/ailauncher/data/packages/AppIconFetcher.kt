package com.ailauncher.data.packages

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * Coil model for an app icon. [version] (the package's lastUpdateTime) is part of the cache key so
 * icons refresh automatically when an app updates.
 */
data class AppIconData(
    val packageName: String,
    val activityName: String,
    val version: Long,
)

/**
 * Loads launcher icons from PackageManager off the main thread and rasterises them (including
 * adaptive icons) at the exact requested size, so Coil's memory cache holds right-sized bitmaps.
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
        val bitmap = drawable.toBitmap(width, height)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val packageManager: PackageManager) : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, options, packageManager)
    }

    class Key : Keyer<AppIconData> {
        override fun key(data: AppIconData, options: Options): String =
            "app-icon:${data.packageName}/${data.activityName}@${data.version}"
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 192
        const val MAX_SIZE_PX = 512
    }
}
