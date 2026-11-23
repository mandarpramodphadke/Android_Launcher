package com.ailauncher.data.packages

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.data.local.entity.AppAliasEntity
import com.ailauncher.data.local.entity.AppCategoryEntity
import com.ailauncher.data.local.entity.AppEntity
import com.ailauncher.data.local.entity.AppPrefsEntity
import com.ailauncher.data.local.entity.AppRow
import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppSyncResult
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.search.FuzzyMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppRepository] backed by PackageManager + Room.
 *
 * Package changes arrive from two sources - the dynamically registered [PackageChangeReceiver]
 * and [LauncherApps.Callback] - and are debounced into a single incremental [sync]. Newly
 * installed packages trigger an incremental AI categorization pass.
 */
@Singleton
class PackageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: AppDao,
    private val categorizationScheduler: CategorizationScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) : AppRepository {

    private val pm: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val internalPrefs = context.getSharedPreferences("launcher_internal", Context.MODE_PRIVATE)
    private val syncMutex = Mutex()
    private val dockMutex = Mutex()
    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val apps: Flow<List<AppInfo>> =
        combine(appDao.observeApps(), appDao.observeAliases()) { rows, aliases ->
            val aliasesByKey = aliases.groupBy({ it.componentKey }, { it.displayAlias })
            rows.map { it.toDomain(aliasesByKey[it.componentKey].orEmpty()) }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = requestSync()
        override fun onPackageAdded(packageName: String, user: UserHandle) = requestSync()
        override fun onPackageChanged(packageName: String, user: UserHandle) = requestSync()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = requestSync()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = requestSync()
    }

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            syncRequests.debounce(SYNC_DEBOUNCE_MS).collect {
                runCatching { sync() }.onFailure { Log.w(TAG, "Package sync failed", it) }
            }
        }
        runCatching { launcherApps?.registerCallback(launcherAppsCallback, Handler(Looper.getMainLooper())) }
    }

    /** Coalesces bursts of package events (an app update fires several) into one sync. */
    fun requestSync() {
        syncRequests.tryEmit(Unit)
    }

    override suspend fun sync(): AppSyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val installed = queryLaunchableApps()
            val cached = appDao.allApps().associateBy { it.componentKey }
            val installedKeys = installed.mapTo(HashSet()) { it.componentKey }

            val added = installed.filter { it.componentKey !in cached }
            val updated = installed.filter { app ->
                val old = cached[app.componentKey] ?: return@filter false
                old.label != app.label || old.updatedAt != app.updatedAt || old.isSystemApp != app.isSystemApp
            }
            val removed = cached.keys.filter { it !in installedKeys }

            appDao.applySync(added + updated, removed)
            val result = AppSyncResult(
                added = added.map { it.packageName }.distinct(),
                removed = removed.map { it.substringBefore('/') }.distinct(),
                updated = updated.map { it.packageName }.distinct(),
            )
            if (result.added.isNotEmpty()) categorizationScheduler.schedule(force = false)
            result
        }
    }

    private fun queryLaunchableApps(): List<AppEntity> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            val packageInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(activity.packageName, 0)
                }
            }.getOrNull()
            val label = runCatching { info.loadLabel(pm).toString().trim() }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: activity.packageName
            AppEntity(
                componentKey = AppInfo.componentKey(activity.packageName, activity.name),
                packageName = activity.packageName,
                activityName = activity.name,
                label = label,
                isSystemApp = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                installedAt = packageInfo?.firstInstallTime ?: 0L,
                updatedAt = packageInfo?.lastUpdateTime ?: 0L,
            )
        }.distinctBy { it.componentKey }
    }

    // ---------------------------------------------------------------------------------------- //
    //  Launching
    // ---------------------------------------------------------------------------------------- //

    override suspend fun launch(app: AppInfo): Boolean {
        val launched = withContext(Dispatchers.Main) {
            val explicit = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.activityName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            tryStart(explicit) || pm.getLaunchIntentForPackage(app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let(::tryStart) == true
        }
        if (launched) {
            recordLaunch(app)
        } else {
            requestSync() // the app is probably gone
        }
        return launched
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start ${intent.component}", e)
        false
    }

    private suspend fun recordLaunch(app: AppInfo) {
        val now = System.currentTimeMillis()
        if (appDao.incrementLaunchCount(app.componentKey, now) == 0) {
            appDao.upsertPrefs(AppPrefsEntity(app.componentKey, app.packageName, launchCount = 1, lastLaunchedAt = now))
        }
    }

    override fun openAppInfo(packageName: String) {
        tryStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun requestUninstall(packageName: String) {
        tryStart(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---------------------------------------------------------------------------------------- //
    //  Customisation
    // ---------------------------------------------------------------------------------------- //

    private suspend fun updatePrefs(componentKey: String, transform: (AppPrefsEntity) -> AppPrefsEntity) {
        val current = appDao.prefs(componentKey)
            ?: AppPrefsEntity(componentKey = componentKey, packageName = componentKey.substringBefore('/'))
        appDao.upsertPrefs(transform(current))
    }

    override suspend fun setHidden(componentKey: String, hidden: Boolean) {
        updatePrefs(componentKey) { it.copy(isHidden = hidden, dockPosition = if (hidden) null else it.dockPosition) }
        if (hidden) compactDock()
    }

    override suspend fun setCustomLabel(componentKey: String, label: String?) {
        updatePrefs(componentKey) { it.copy(customLabel = label?.trim()?.takeIf(String::isNotEmpty)) }
    }

    override suspend fun setCategory(packageName: String, category: AppCategory, source: CategorySource) {
        appDao.upsertCategories(listOf(AppCategoryEntity(packageName, category.name, source.name, System.currentTimeMillis())))
    }

    override suspend fun setCategories(categories: Map<String, AppCategory>, source: CategorySource) {
        if (categories.isEmpty()) return
        // Never overwrite a manual choice with an automatic one.
        val userOwned = if (source == CategorySource.USER) emptySet() else {
            appDao.categoriesFor(categories.keys.toList())
                .filter { it.source == CategorySource.USER.name }
                .mapTo(HashSet()) { it.packageName }
        }
        val now = System.currentTimeMillis()
        appDao.upsertCategories(
            categories.filterKeys { it !in userOwned }
                .map { (pkg, category) -> AppCategoryEntity(pkg, category.name, source.name, now) },
        )
    }

    override suspend fun resetAutomaticCategories() = appDao.deleteAutomaticCategories()

    override suspend fun addAlias(componentKey: String, alias: String) {
        val normalized = FuzzyMatcher.normalize(alias)
        if (normalized.isEmpty()) return
        appDao.upsertAlias(AppAliasEntity(alias = normalized, componentKey = componentKey, displayAlias = alias.trim()))
    }

    override suspend fun removeAlias(alias: String) = appDao.deleteAlias(FuzzyMatcher.normalize(alias))

    override fun systemCategoryOf(packageName: String): AppCategory? {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.MEDIA
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.PHOTOGRAPHY
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.UTILITIES
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------- //
    //  Dock
    // ---------------------------------------------------------------------------------------- //

    override suspend fun pinToDock(componentKey: String): Boolean = dockMutex.withLock {
        val dock = appDao.dock()
        if (dock.any { it.componentKey == componentKey }) return@withLock true
        if (dock.size >= AppRepository.MAX_DOCK_SIZE) return@withLock false
        updatePrefs(componentKey) { it.copy(dockPosition = (dock.maxOfOrNull { d -> d.dockPosition ?: 0 } ?: -1) + 1, isHidden = false) }
        true
    }

    override suspend fun unpinFromDock(componentKey: String) = dockMutex.withLock {
        updatePrefs(componentKey) { it.copy(dockPosition = null) }
        compactDockLocked()
    }

    override suspend fun moveInDock(componentKey: String, offset: Int) = dockMutex.withLock {
        val dock = appDao.dock().toMutableList()
        val index = dock.indexOfFirst { it.componentKey == componentKey }
        if (index < 0) return@withLock
        val target = (index + offset).coerceIn(0, dock.lastIndex)
        if (target == index) return@withLock
        dock.add(target, dock.removeAt(index))
        appDao.upsertPrefs(dock.mapIndexed { i, prefs -> prefs.copy(dockPosition = i) })
    }

    private suspend fun compactDock() = dockMutex.withLock { compactDockLocked() }

    private suspend fun compactDockLocked() {
        appDao.upsertPrefs(appDao.dock().mapIndexed { i, prefs -> prefs.copy(dockPosition = i) })
    }

    override suspend fun ensureDefaultDock() {
        if (internalPrefs.getBoolean(KEY_DOCK_SEEDED, false)) return
        val installed = withContext(Dispatchers.IO) { appDao.allApps() }
        if (installed.isEmpty()) return
        val defaults = withContext(Dispatchers.IO) {
            listOf(
                Intent(Intent.ACTION_DIAL),
                Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
                Intent(Intent.ACTION_VIEW, "https://www.example.com".toUri()),
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            ).mapNotNull(::defaultHandlerPackage).distinct()
        }
        for (pkg in defaults) {
            val entry = installed.firstOrNull { it.packageName == pkg } ?: continue
            pinToDock(entry.componentKey)
        }
        internalPrefs.edit { putBoolean(KEY_DOCK_SEEDED, true) }
    }

    private fun defaultHandlerPackage(intent: Intent): String? {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val pkg = resolved?.activityInfo?.packageName
        // "android" is the disambiguation dialog shown when no default is set.
        if (pkg != null && pkg != "android") return pkg
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return candidates.firstOrNull()?.activityInfo?.packageName
    }

    private fun AppRow.toDomain(aliases: List<String>) = AppInfo(
        componentKey = componentKey,
        packageName = packageName,
        activityName = activityName,
        systemLabel = label,
        customLabel = customLabel,
        category = category?.let { name -> AppCategory.entries.firstOrNull { it.name == name } },
        categorySource = categorySource?.let { name -> CategorySource.entries.firstOrNull { it.name == name } },
        isHidden = isHidden ?: false,
        dockPosition = dockPosition,
        launchCount = launchCount ?: 0,
        lastLaunchedAt = lastLaunchedAt ?: 0L,
        isSystemApp = isSystemApp,
        installedAt = installedAt,
        updatedAt = updatedAt,
        aliases = aliases,
    )

    private companion object {
        const val TAG = "PackageRepository"
        const val SYNC_DEBOUNCE_MS = 400L
        const val KEY_DOCK_SEEDED = "dock_seeded"
    }
}
