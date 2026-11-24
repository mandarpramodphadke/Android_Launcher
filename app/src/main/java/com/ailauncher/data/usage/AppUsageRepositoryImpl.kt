package com.ailauncher.data.usage

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import com.ailauncher.data.local.dao.AppDao
import com.ailauncher.domain.repository.AppUsageRepository
import com.ailauncher.domain.suggestions.LaunchSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App usage, read on-device only. With "Usage access" granted it uses UsageStatsManager (covers
 * apps opened from anywhere); without it, the launcher's own launch log still gives a useful
 * answer for apps opened from the home screen.
 */
@Singleton
class AppUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: AppDao,
) : AppUsageRepository {

    private val usageStats: UsageStatsManager? = context.getSystemService(UsageStatsManager::class.java)
    private val storageStats: StorageStatsManager? = context.getSystemService(StorageStatsManager::class.java)
    private val appOps: AppOpsManager? = context.getSystemService(AppOpsManager::class.java)

    override fun hasUsageAccess(): Boolean {
        val ops = appOps ?: return false
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    override suspend fun lastUsedTimes(sinceMillis: Long): Map<String, Long> = withContext(Dispatchers.IO) {
        val result = HashMap<String, Long>()
        if (hasUsageAccess()) {
            runCatching {
                usageStats?.queryAndAggregateUsageStats(sinceMillis, System.currentTimeMillis())?.forEach { (pkg, stats) ->
                    val last = maxOf(stats.lastTimeVisible, stats.lastTimeUsed)
                    if (last > 0) result[pkg] = last
                }
            }
        }
        appDao.allPrefs().forEach { prefs ->
            if (prefs.lastLaunchedAt > 0) result.merge(prefs.packageName, prefs.lastLaunchedAt, ::maxOf)
        }
        result
    }

    override suspend fun launchHistory(sinceMillis: Long): List<LaunchSignal> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyList()
        val manager = usageStats ?: return@withContext emptyList()
        val events = runCatching { manager.queryEvents(sinceMillis, System.currentTimeMillis()) }.getOrNull()
            ?: return@withContext emptyList()
        val signals = ArrayList<LaunchSignal>()
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        var lastTime = 0L
        while (events.hasNextEvent() && signals.size < MAX_EVENTS) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            // Activity hops inside one app (or a quick re-resume) count as one launch.
            if (pkg == lastPackage && event.timeStamp - lastTime < SAME_SESSION_MILLIS) {
                lastTime = event.timeStamp
                continue
            }
            signals += LaunchSignal(pkg, event.timeStamp)
            lastPackage = pkg
            lastTime = event.timeStamp
        }
        signals
    }

    override suspend fun appSizeBytes(packageName: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val stats = storageStats?.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
                ?: return@runCatching null
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        }.getOrNull()
    }

    private companion object {
        const val MAX_EVENTS = 20_000
        const val SAME_SESSION_MILLIS = 5 * 60_000L
    }
}
