package com.ailauncher.presentation.drawer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.packages.AppLauncher
import com.ailauncher.data.usage.AudioRouteDetector
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorizationProgress
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.AppUsageRepository
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.HomeLayoutRepository
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.NotificationDigestRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.ShortcutRepository
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.domain.suggestions.AppSuggestionRanker
import com.ailauncher.domain.suggestions.SuggestionContext
import com.ailauncher.presentation.components.LauncherAppsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

data class AlphaSection(val key: String, val apps: List<AppInfo>)

/** [category] is null for apps that have not been categorized yet. */
data class CategorySection(val category: AppCategory?, val apps: List<AppInfo>)

data class DrawerUiState(
    val loading: Boolean = true,
    val query: String = "",
    val tab: DrawerTab = DrawerTab.ALPHABETICAL,
    val layout: DrawerLayout = DrawerLayout.GRID,
    val columns: Int = 4,
    val alphabetical: List<AlphaSection> = emptyList(),
    val categories: List<CategorySection> = emptyList(),
    val searchResults: List<AppInfo> = emptyList(),
    val totalApps: Int = 0,
    val notSortedByAi: Int = 0,
    val progress: CategorizationProgress = CategorizationProgress(running = false, categorized = 0, total = 0),
    val modelReady: Boolean = false,
    val folders: List<CustomFolder> = emptyList(),
    /** packageName → launchable entries, for folder contents. */
    val appsByPackage: Map<String, List<AppInfo>> = emptyMap(),
    val suggestions: List<AppInfo> = emptyList(),
    val hiddenApps: List<AppInfo> = emptyList(),
    val badges: Map<String, Int> = emptyMap(),
) {
    fun appsOf(folder: CustomFolder): List<AppInfo> = folder.packageNames.flatMap { appsByPackage[it].orEmpty() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    appRepository: AppRepository,
    appLauncher: AppLauncher,
    dockRepository: DockRepository,
    homeLayout: HomeLayoutRepository,
    folderRepository: FolderRepository,
    shortcutRepository: ShortcutRepository,
    private val settingsRepository: SettingsRepository,
    private val categorizationScheduler: CategorizationScheduler,
    private val usage: AppUsageRepository,
    private val audioRoutes: AudioRouteDetector,
    notifications: NotificationDigestRepository,
    modelRepository: ModelRepository,
) : LauncherAppsViewModel(appRepository, appLauncher, dockRepository, homeLayout, folderRepository, shortcutRepository) {

    /** Search text, edited synchronously by the field (see LauncherTextField). */
    val queryState = TextFieldState()
    private val query: Flow<String> = snapshotFlow { queryState.text.toString() }.distinctUntilChanged()

    private val allApps: Flow<List<AppInfo>> = appRepository.apps
    private val visibleApps: Flow<List<AppInfo>> = allApps.map { apps -> apps.filter { !it.isHidden } }

    /** Bumped when the drawer opens, so suggestions follow the time of day and connected audio. */
    private val suggestionRefresh = MutableStateFlow(0)

    private val suggestions: Flow<List<AppInfo>> = combine(visibleApps, suggestionRefresh, settingsRepository.launcherSettings) { apps, _, settings ->
        Triple(apps, settings.showSuggestedApps, ZonedDateTime.now())
    }.mapLatest { (apps, enabled, now) ->
        if (!enabled || apps.isEmpty()) return@mapLatest emptyList()
        val history = usage.launchHistory(now.minusDays(HISTORY_DAYS).toInstant().toEpochMilli())
        AppSuggestionRanker.rank(apps, history, SuggestionContext(now, audioRoutes.current()), SUGGESTIONS)
    }.flowOn(Dispatchers.IO)

    private data class Extras(
        val folders: List<CustomFolder>,
        val suggestions: List<AppInfo>,
        val badges: Map<String, Int>,
        val progress: CategorizationProgress,
        val modelReady: Boolean,
    )

    private val extras: Flow<Extras> = combine(
        folderRepository.folders,
        suggestions,
        notifications.badgeCounts,
        categorizationScheduler.progress,
        modelRepository.fileState.map { it is ModelFileState.Ready },
    ) { folders, suggested, badges, progress, ready -> Extras(folders, suggested, badges, progress, ready) }

    val uiState: StateFlow<DrawerUiState> = combine(
        allApps,
        settingsRepository.launcherSettings,
        query,
        extras,
    ) { apps, settings, q, extras ->
        val visible = apps.filter { !it.isHidden }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val sorted = visible.sortedWith { a, b -> collator.compare(a.label, b.label) }
        DrawerUiState(
            loading = false,
            query = q,
            tab = settings.drawerTab,
            layout = settings.drawerLayout,
            columns = settings.gridColumns,
            alphabetical = sorted.groupBy { sectionKey(it.label) }
                .map { (key, list) -> AlphaSection(key, list) }
                .sortedWith { a, b -> if (a.key == "#") 1 else if (b.key == "#") -1 else collator.compare(a.key, b.key) },
            categories = buildCategories(sorted),
            searchResults = if (q.isBlank()) emptyList() else FuzzyMatcher.rank(q, visible) { app ->
                listOf(app.label, app.systemLabel, app.packageName.substringAfterLast('.')) + app.aliases
            }.map { it.first },
            totalApps = visible.size,
            notSortedByAi = visible.distinctBy { it.packageName }
                .count { it.categorySource == null || it.categorySource == CategorySource.HEURISTIC },
            progress = extras.progress,
            modelReady = extras.modelReady,
            folders = extras.folders,
            appsByPackage = visible.groupBy { it.packageName },
            suggestions = if (q.isBlank()) extras.suggestions else emptyList(),
            hiddenApps = apps.filter { it.isHidden }.sortedWith { a, b -> collator.compare(a.label, b.label) },
            badges = if (settings.notificationDigestEnabled && settings.notificationBadges) extras.badges else emptyMap(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun onDrawerOpened() {
        suggestionRefresh.value++
    }

    fun clearQuery() = queryState.clearText()

    fun setTab(tab: DrawerTab) {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(drawerTab = tab) } }
    }

    fun toggleLayout() {
        viewModelScope.launch {
            settingsRepository.updateLauncherSettings {
                it.copy(drawerLayout = if (it.drawerLayout == DrawerLayout.GRID) DrawerLayout.LIST else DrawerLayout.GRID)
            }
        }
    }

    fun organizeWithAi(force: Boolean = false) {
        categorizationScheduler.schedule(force)
    }

    fun unhide(app: AppInfo) {
        viewModelScope.launch { appRepository.setHidden(app.componentKey, false) }
    }

    private fun buildCategories(sortedApps: List<AppInfo>): List<CategorySection> {
        val grouped = sortedApps.groupBy { it.category }
        val known = AppCategory.entries.mapNotNull { category ->
            grouped[category]?.let { apps ->
                CategorySection(category, apps.sortedWith(compareByDescending<AppInfo> { it.launchCount }))
            }
        }
        val other = grouped[null]?.let { listOf(CategorySection(null, it)) }.orEmpty()
        return known + other
    }

    private fun sectionKey(label: String): String {
        val first = FuzzyMatcher.normalize(label).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    private companion object {
        const val HISTORY_DAYS = 28L
        const val SUGGESTIONS = 5
    }
}
