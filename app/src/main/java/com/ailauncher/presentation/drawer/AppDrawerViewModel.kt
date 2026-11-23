package com.ailauncher.presentation.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorizationProgress
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.presentation.components.AppActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
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
)

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val categorizationScheduler: CategorizationScheduler,
    modelRepository: ModelRepository,
) : ViewModel(), AppActionHandler {

    private val query = MutableStateFlow("")

    private val visibleApps: Flow<List<AppInfo>> = appRepository.apps.map { apps -> apps.filter { !it.isHidden } }

    val uiState: StateFlow<DrawerUiState> = combine(
        visibleApps,
        settingsRepository.launcherSettings,
        query,
        categorizationScheduler.progress,
        modelRepository.fileState.map { it is ModelFileState.Ready },
    ) { apps, settings, q, progress, modelReady ->
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val sorted = apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
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
            searchResults = if (q.isBlank()) emptyList() else FuzzyMatcher.rank(q, apps) { app ->
                listOf(app.label, app.systemLabel, app.packageName.substringAfterLast('.')) + app.aliases
            }.map { it.first },
            totalApps = apps.size,
            notSortedByAi = apps.distinctBy { it.packageName }
                .count { it.categorySource == null || it.categorySource == CategorySource.HEURISTIC },
            progress = progress,
            modelReady = modelReady,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

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

    fun launch(app: AppInfo) {
        viewModelScope.launch {
            if (!appRepository.launch(app)) _messages.send("${app.label} could not be opened")
        }
    }

    fun organizeWithAi(force: Boolean = false) {
        categorizationScheduler.schedule(force)
    }

    // --- AppActionHandler ------------------------------------------------------------------
    override fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app.packageName)
    override fun uninstall(app: AppInfo) = appRepository.requestUninstall(app.packageName)

    override fun togglePin(app: AppInfo) {
        viewModelScope.launch {
            if (app.isInDock) {
                appRepository.unpinFromDock(app.componentKey)
                _messages.send("Removed ${app.label} from the dock")
            } else if (appRepository.pinToDock(app.componentKey)) {
                _messages.send("Pinned ${app.label} to the dock")
            } else {
                _messages.send("The dock is full (${AppRepository.MAX_DOCK_SIZE} apps)")
            }
        }
    }

    override fun hide(app: AppInfo) {
        viewModelScope.launch {
            appRepository.setHidden(app.componentKey, true)
            _messages.send("${app.label} hidden · manage hidden apps in Settings")
        }
    }

    override fun rename(app: AppInfo, label: String?) {
        viewModelScope.launch { appRepository.setCustomLabel(app.componentKey, label) }
    }

    override fun setCategory(app: AppInfo, category: AppCategory) {
        viewModelScope.launch { appRepository.setCategory(app.packageName, category, CategorySource.USER) }
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
}
