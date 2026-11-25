package com.ailauncher.domain.home

import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.UsageSnapshot

/**
 * Works out which apps a smart folder holds. Pure, so the same answer comes out in the folder,
 * its home-screen icon, the drawer and the editor's preview. Private Space (hidden) apps never
 * appear in a smart folder.
 */
object SmartFolderEngine {

    const val DAY_MILLIS = 86_400_000L

    /** Package names for [rule], in display order. The launcher tools rule holds no apps. */
    fun packages(rule: FolderRule, apps: List<AppInfo>, usage: UsageSnapshot, nowMillis: Long, ownPackage: String? = null): List<String> {
        val candidates = apps.asSequence()
            .filter { !it.isHidden && it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .toList()
        val byLabel = compareBy(String.CASE_INSENSITIVE_ORDER) { app: AppInfo -> app.label }
        return when (rule) {
            is FolderRule.Category -> candidates.filter { it.category == rule.category }.sortedWith(byLabel)

            is FolderRule.RecentlyInstalled -> {
                val since = nowMillis - rule.days * DAY_MILLIS
                candidates
                    .filter { !it.isSystemApp && it.installedAt in since..nowMillis }
                    .sortedWith(compareByDescending<AppInfo> { it.installedAt }.then(byLabel))
            }

            is FolderRule.NotUsedFor -> {
                val cutoff = nowMillis - rule.days * DAY_MILLIS
                candidates
                    .filter { !it.isSystemApp }
                    .map { it to lastActivity(it, usage) }
                    // Never-opened apps count from their install date, so a new app isn't "unused".
                    .filter { (_, last) -> last in 1 until cutoff }
                    .sortedWith(compareBy<Pair<AppInfo, Long>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.label })
                    .map { it.first }
            }

            is FolderRule.MostUsed -> {
                // Usage access gives real launch counts for the last 4 weeks; without it, the
                // launcher's own counter (apps opened from AI Launcher) is the best signal.
                val counts: (AppInfo) -> Int = if (usage.launchCounts.isNotEmpty()) {
                    { usage.launchCounts[it.packageName] ?: 0 }
                } else {
                    { it.launchCount }
                }
                candidates
                    .map { it to counts(it) }
                    .filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenByDescending { lastActivity(it.first, usage) })
                    .take(rule.count)
                    .map { it.first }
            }

            FolderRule.LauncherTools -> emptyList()
        }.map { it.packageName }
    }

    /** Last time the app was opened (from anywhere when known), or its install time. */
    fun lastActivity(app: AppInfo, usage: UsageSnapshot): Long =
        maxOf(usage.lastUsed[app.packageName] ?: 0L, app.lastLaunchedAt).takeIf { it > 0 } ?: app.installedAt
}

/** Names for folders made by dropping one app onto another. */
object FolderNaming {
    const val FALLBACK = "Folder"

    /** The apps' shared category ("Social"), or [FALLBACK] when they differ. */
    fun suggest(apps: List<AppInfo>): String {
        val categories = apps.map { it.category }.distinct()
        return categories.singleOrNull()?.displayName ?: FALLBACK
    }
}

/** Last-used times (past year) and launch counts (past 4 weeks) for smart folders. */
suspend fun com.ailauncher.domain.repository.AppUsageRepository.smartFolderSnapshot(nowMillis: Long): UsageSnapshot {
    val access = hasUsageAccess()
    val lastUsed = lastUsedTimes(nowMillis - 365 * SmartFolderEngine.DAY_MILLIS)
    val counts = if (access) {
        launchHistory(nowMillis - 28 * SmartFolderEngine.DAY_MILLIS).groupingBy { it.packageName }.eachCount()
    } else {
        emptyMap()
    }
    return UsageSnapshot(access, lastUsed, counts)
}
