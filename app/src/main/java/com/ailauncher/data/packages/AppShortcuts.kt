package com.ailauncher.data.packages

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.util.Log
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
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppShortcut
import com.ailauncher.domain.repository.ShortcutRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Launches apps and app shortcuts with an activity-options bundle (e.g. a clip-reveal animation). */
interface AppLauncher {
    /** [sourceBounds] in screen pixels; [options] from ActivityOptions.toBundle(). */
    suspend fun launch(app: AppInfo, sourceBounds: Rect?, options: Bundle?): Boolean
    fun startShortcut(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle?): Boolean
}

/**
 * App shortcuts ("New message", "Navigate home", …) through LauncherApps. Android only exposes
 * them to the default home app, so the list is empty until AI Launcher is the launcher.
 */
@Singleton
class ShortcutRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShortcutRepository {

    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)

    private fun hasHostPermission(): Boolean = runCatching { launcherApps?.hasShortcutHostPermission() == true }.getOrDefault(false)

    override suspend fun shortcuts(packageName: String): List<AppShortcut> = withContext(Dispatchers.IO) {
        query(packageName, null)
            .filter { it.isEnabled }
            .sortedWith(compareBy<ShortcutInfo>({ !it.isDeclaredInManifest }, { it.rank }))
            .take(MAX_SHORTCUTS)
            .map { AppShortcut(it.id, it.`package`, (it.shortLabel ?: it.longLabel ?: it.id).toString(), it.longLabel?.toString()) }
    }

    fun query(packageName: String, ids: List<String>?): List<ShortcutInfo> {
        val apps = launcherApps ?: return emptyList()
        if (!hasHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        if (ids != null) query.setShortcutIds(ids)
        return runCatching { apps.getShortcuts(query, Process.myUserHandle()).orEmpty() }
            .onFailure { Log.w(TAG, "Shortcut query failed for $packageName", it) }
            .getOrDefault(emptyList())
    }

    fun start(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle?): Boolean = runCatching {
        launcherApps?.startShortcut(shortcut.packageName, shortcut.id, sourceBounds, options, Process.myUserHandle())
        launcherApps != null
    }.getOrDefault(false)

    fun iconFor(packageName: String, id: String) = query(packageName, listOf(id)).firstOrNull()?.let { info ->
        runCatching { launcherApps?.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()
    }

    private companion object {
        const val TAG = "Shortcuts"
        const val MAX_SHORTCUTS = 5
    }
}

/** Coil model for a shortcut's icon. */
data class ShortcutIconData(val packageName: String, val shortcutId: String)

class ShortcutIconFetcher(
    private val data: ShortcutIconData,
    private val options: Options,
    private val repository: ShortcutRepositoryImpl,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val drawable = repository.iconFor(data.packageName, data.shortcutId) ?: return null
        val size = options.size.width.pxOrElse { 96 }.coerceIn(16, 256)
        return ImageFetchResult(drawable.toBitmap(size, size).asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val repository: ShortcutRepositoryImpl) : Fetcher.Factory<ShortcutIconData> {
        override fun create(data: ShortcutIconData, options: Options, imageLoader: ImageLoader): Fetcher =
            ShortcutIconFetcher(data, options, repository)
    }

    class Key : Keyer<ShortcutIconData> {
        override fun key(data: ShortcutIconData, options: Options) = "shortcut:${data.packageName}/${data.shortcutId}"
    }
}
