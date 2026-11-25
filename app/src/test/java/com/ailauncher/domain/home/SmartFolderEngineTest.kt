package com.ailauncher.domain.home

import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.LauncherTool
import com.ailauncher.domain.model.LauncherWidgetConfig
import com.ailauncher.domain.model.LauncherWidgetKind
import com.ailauncher.domain.model.UsageSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartFolderEngineTest {

    private val day = SmartFolderEngine.DAY_MILLIS
    private val now = 1_000 * day

    private fun app(
        pkg: String,
        label: String = pkg,
        category: AppCategory? = null,
        installedDaysAgo: Int = 400,
        system: Boolean = false,
        hidden: Boolean = false,
        launches: Int = 0,
        lastLaunchedDaysAgo: Int? = null,
    ) = AppInfo(
        componentKey = "$pkg/.Main",
        packageName = pkg,
        activityName = ".Main",
        systemLabel = label,
        customLabel = null,
        category = category,
        categorySource = null,
        isHidden = hidden,
        dockPosition = null,
        launchCount = launches,
        lastLaunchedAt = lastLaunchedDaysAgo?.let { now - it * day } ?: 0L,
        isSystemApp = system,
        installedAt = now - installedDaysAgo * day,
        updatedAt = 0L,
    )

    private fun packages(rule: FolderRule, apps: List<AppInfo>, usage: UsageSnapshot = UsageSnapshot.EMPTY) =
        SmartFolderEngine.packages(rule, apps, usage, now)

    @Test
    fun categoryFolderHoldsThatCategoryAToZWithoutPrivateSpaceApps() {
        val apps = listOf(
            app("zz.chess", "Chess", AppCategory.GAMES),
            app("aa.sudoku", "sudoku", AppCategory.GAMES),
            app("hidden.poker", "Poker", AppCategory.GAMES, hidden = true),
            app("mail", "Mail", AppCategory.PRODUCTIVITY),
        )
        assertThat(packages(FolderRule.Category(AppCategory.GAMES), apps)).containsExactly("zz.chess", "aa.sudoku").inOrder()
    }

    @Test
    fun newAppsAreRecentUserInstallsNewestFirst() {
        val apps = listOf(
            app("old", installedDaysAgo = 40),
            app("week", installedDaysAgo = 7),
            app("today", installedDaysAgo = 0),
            app("system.update", installedDaysAgo = 1, system = true),
        )
        assertThat(packages(FolderRule.RecentlyInstalled(14), apps)).containsExactly("today", "week").inOrder()
        assertThat(packages(FolderRule.RecentlyInstalled(7), apps)).containsExactly("today", "week").inOrder()
        assertThat(packages(FolderRule.RecentlyInstalled(3), apps)).containsExactly("today")
    }

    @Test
    fun notUsedUsesLastUseFromUsageAccessOrTheLaunchersLogOrTheInstallDate() {
        val apps = listOf(
            app("used.elsewhere", lastLaunchedDaysAgo = 100), // usage access says: yesterday
            app("launcher.only", lastLaunchedDaysAgo = 45),
            app("never.opened.old", installedDaysAgo = 200),
            app("never.opened.new", installedDaysAgo = 5),
            app("system.app", system = true),
        )
        val usage = UsageSnapshot(hasUsageAccess = true, lastUsed = mapOf("used.elsewhere" to now - day))
        // Longest unused first; a new app isn't "unused" yet.
        assertThat(packages(FolderRule.NotUsedFor(30), apps, usage)).containsExactly("never.opened.old", "launcher.only").inOrder()
        assertThat(packages(FolderRule.NotUsedFor(60), apps, usage)).containsExactly("never.opened.old")
    }

    @Test
    fun mostUsedPrefersRealLaunchCountsAndFallsBackToTheLauncherCounter() {
        val apps = listOf(
            app("chat", launches = 3),
            app("maps", launches = 50),
            app("music", launches = 10),
            app("unused", launches = 0),
        )
        assertThat(packages(FolderRule.MostUsed(2), apps)).containsExactly("maps", "music").inOrder()
        val usage = UsageSnapshot(hasUsageAccess = true, launchCounts = mapOf("chat" to 90, "music" to 12))
        assertThat(packages(FolderRule.MostUsed(5), apps, usage)).containsExactly("chat", "music").inOrder()
    }

    @Test
    fun launcherToolsFolderHasToolsNotApps() {
        assertThat(packages(FolderRule.LauncherTools, listOf(app("a")))).isEmpty()
        val folder = CustomFolder(1, "AI Launcher", null, FolderAccent.INDIGO, 0, emptyList(), null, rule = FolderRule.LauncherTools)
        assertThat(folder.isSmart).isTrue()
        assertThat(folder.tools).containsExactlyElementsIn(LauncherTool.entries).inOrder()
        assertThat(folder.copy(rule = FolderRule.MostUsed()).tools).isEmpty()
    }

    @Test
    fun rulesSurviveStorageAndBadValuesFallBackToARegularFolder() {
        val rules = listOf(
            FolderRule.Category(AppCategory.SOCIAL),
            FolderRule.RecentlyInstalled(7),
            FolderRule.NotUsedFor(90),
            FolderRule.MostUsed(16),
            FolderRule.LauncherTools,
        )
        rules.forEach { assertThat(FolderRule.decode(it.encode())).isEqualTo(it) }
        assertThat(FolderRule.decode("category:Games")).isEqualTo(FolderRule.Category(AppCategory.GAMES))
        assertThat(FolderRule.decode("installed:9999")).isEqualTo(FolderRule.RecentlyInstalled(FolderRule.MAX_DAYS))
        assertThat(FolderRule.decode("most_used:500")).isEqualTo(FolderRule.MostUsed(FolderRule.MAX_COUNT))
        listOf(null, "", "unused:-3", "installed:x", "category:Nope", "future_rule:1").forEach {
            assertThat(FolderRule.decode(it)).isNull()
        }
        assertThat(FolderRule.RecentlyInstalled(14).summary).isEqualTo("Apps installed in the last 14 days")
        assertThat(FolderRule.Category(AppCategory.GAMES).defaultName).isEqualTo("Games")
    }

    @Test
    fun droppedAppsMakeAFolderNamedAfterTheirSharedCategory() {
        assertThat(FolderNaming.suggest(listOf(app("a", category = AppCategory.SOCIAL), app("b", category = AppCategory.SOCIAL)))).isEqualTo("Social")
        assertThat(FolderNaming.suggest(listOf(app("a", category = AppCategory.SOCIAL), app("b", category = AppCategory.GAMES)))).isEqualTo(FolderNaming.FALLBACK)
        assertThat(FolderNaming.suggest(listOf(app("a"), app("b")))).isEqualTo(FolderNaming.FALLBACK)
    }

    @Test
    fun launcherWidgetConfigRoundTrips() {
        val plain = LauncherWidgetConfig(LauncherWidgetKind.TODAY)
        val withList = LauncherWidgetConfig(LauncherWidgetKind.TODO_LIST, "Work: Q4 launch")
        assertThat(plain.encode()).isEqualTo("today")
        assertThat(LauncherWidgetConfig.decode(plain.encode())).isEqualTo(plain)
        assertThat(LauncherWidgetConfig.decode(withList.encode())).isEqualTo(withList)
        assertThat(LauncherWidgetConfig.decode("todo_list:")).isEqualTo(LauncherWidgetConfig(LauncherWidgetKind.TODO_LIST))
        assertThat(LauncherWidgetConfig.decode("clock")).isNull()
        assertThat(LauncherWidgetConfig.decode(null)).isNull()
    }
}
