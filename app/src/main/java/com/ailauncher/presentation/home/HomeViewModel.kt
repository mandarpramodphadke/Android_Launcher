package com.ailauncher.presentation.home

import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.packages.AppLauncher
import com.ailauncher.data.system.LockMethod
import com.ailauncher.data.system.SystemGestureController
import com.ailauncher.data.widgets.LauncherWidgetHost
import com.ailauncher.data.widgets.WidgetProviderEntry
import com.ailauncher.domain.clipboard.ClipInsight
import com.ailauncher.domain.clipboard.ClipboardAnalyzer
import com.ailauncher.domain.home.GridSpec
import com.ailauncher.domain.home.HomeGridPlanner
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.SmartExtraction
import com.ailauncher.domain.model.SmartPill
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.HomeLayoutRepository
import com.ailauncher.domain.repository.NotificationDigestRepository
import com.ailauncher.domain.repository.PlannerRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.ShortcutRepository
import com.ailauncher.domain.repository.TodoRepository
import com.ailauncher.domain.repository.UpcomingEventsProvider
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.presentation.components.HomeGridMetrics
import com.ailauncher.presentation.components.LaunchRequest
import com.ailauncher.presentation.components.LauncherAppsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

/** The home pages' contents, the dock and everything drawn over the wallpaper. */
data class HomeUiState(
    val now: ZonedDateTime = ZonedDateTime.now(),
    val use24Hour: Boolean = false,
    val upcoming: UpcomingEvent? = null,
    val dock: List<DockEntry> = emptyList(),
    /** componentKey → app, for home items. */
    val apps: Map<String, AppInfo> = emptyMap(),
    val homeItems: List<HomeItem> = emptyList(),
    val pageCount: Int = 1,
    val folders: Map<Long, CustomFolder> = emptyMap(),
    val searchResults: List<AppInfo> = emptyList(),
    val pills: List<SmartPill> = emptyList(),
    val badges: Map<String, Int> = emptyMap(),
    val glance: Glance = Glance(),
    val settings: LauncherSettings = LauncherSettings(),
) {
    /** Apps of a folder, in the folder's order. */
    fun appsOf(folder: CustomFolder): List<AppInfo> {
        val byPackage = apps.values.groupBy { it.packageName }
        return folder.packageNames.flatMap { byPackage[it].orEmpty() }.filter { !it.isHidden }
    }

    fun badgeFor(app: AppInfo): Int = badges[app.packageName] ?: 0
}

/** The home "glance" card: open to-dos and the next planner block. */
data class Glance(val openTodos: Int = 0, val urgentTodos: Int = 0, val nextBlock: PlannerBlock? = null)

sealed interface HomeEvent {
    data object LockSetupNeeded : HomeEvent
    data class Message(val text: String) : HomeEvent
    /** Ask the system to let us bind this widget (ACTION_APPWIDGET_BIND). */
    data class BindWidget(val appWidgetId: Int, val entry: WidgetProviderEntry) : HomeEvent
    /** The widget needs its configuration activity. */
    data class ConfigureWidget(val appWidgetId: Int) : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    appRepository: AppRepository,
    appLauncher: AppLauncher,
    private val dockRepository: DockRepository,
    private val homeLayout: HomeLayoutRepository,
    private val folderRepository: FolderRepository,
    shortcutRepository: ShortcutRepository,
    private val settingsRepository: SettingsRepository,
    private val notifications: NotificationDigestRepository,
    todos: TodoRepository,
    planner: PlannerRepository,
    upcomingEventsProvider: UpcomingEventsProvider,
    private val gestures: SystemGestureController,
    val widgetHost: LauncherWidgetHost,
) : LauncherAppsViewModel(appRepository, appLauncher, dockRepository, homeLayout, folderRepository, shortcutRepository) {

    /** Emits on every minute boundary so the clock flips exactly on time. */
    private val clock: Flow<ZonedDateTime> = flow {
        while (true) {
            val now = ZonedDateTime.now()
            emit(now)
            delay(60_000L - (now.second * 1000L + now.nano / 1_000_000L))
        }
    }

    /**
     * The command bar's text. Owned here and edited synchronously by the text field; search reads
     * it through snapshotFlow. (Feeding the field from a flow made the cursor lag behind typing.)
     */
    val queryState = TextFieldState()
    private val query: Flow<String> = snapshotFlow { queryState.text.toString() }.distinctUntilChanged()

    private data class Layout(val items: List<HomeItem>, val pages: Int, val folders: Map<Long, CustomFolder>, val dock: List<DockEntry>)
    private data class Ambient(val pills: List<SmartPill>, val badges: Map<String, Int>, val glance: Glance, val upcoming: UpcomingEvent?)

    private val layout: Flow<Layout> = combine(homeLayout.items, homeLayout.pageCount, folderRepository.folders, dockRepository.entries) { items, pages, folders, dock ->
        Layout(items, pages, folders.associateBy { it.id }, dock)
    }

    private val today: Flow<LocalDate> = clock.map { it.toLocalDate() }.distinctUntilChanged()

    private val glance: Flow<Glance> = combine(todos.todos, today.flatMapLatest { planner.observeDay(it) }, clock) { list, blocks, now ->
        val open = list.filter { !it.completed }
        val minute = now.hour * 60 + now.minute
        Glance(
            openTodos = open.size,
            urgentTodos = open.count { it.priority == TodoPriority.HIGH },
            nextBlock = blocks.filter { !it.completed && it.endMinutes > minute }.minByOrNull { it.startMinutes },
        )
    }

    private val ambient: Flow<Ambient> = combine(notifications.smartPills, notifications.badgeCounts, glance, upcomingEventsProvider.nextEvent) { pills, badges, g, up ->
        Ambient(pills, badges, g, up)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        clock,
        settingsRepository.launcherSettings,
        appRepository.apps,
        layout,
        combine(ambient, query) { a, q -> a to q },
    ) { now, settings, apps, layout, (ctx, q) ->
        val visible = apps.filter { !it.isHidden }
        HomeUiState(
            now = now,
            use24Hour = settings.use24HourClock ?: DateFormat.is24HourFormat(context),
            upcoming = ctx.upcoming,
            dock = layout.dock,
            apps = apps.associateBy { it.componentKey },
            homeItems = layout.items,
            pageCount = layout.pages,
            folders = layout.folders,
            searchResults = if (q.isBlank()) emptyList() else FuzzyMatcher.rank(q, visible, ::searchNames).take(MAX_RESULTS).map { it.first },
            pills = if (settings.notificationDigestEnabled) ctx.pills else emptyList(),
            badges = if (settings.notificationDigestEnabled && settings.notificationBadges) ctx.badges else emptyMap(),
            glance = ctx.glance,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    /** Clipboard suggestion for the command bar, read only when the user focuses it. */
    private val _clip = MutableStateFlow<ClipInsight?>(null)
    val clip: StateFlow<ClipInsight?> = _clip.asStateFlow()

    init {
        viewModelScope.launch { messages.collect { _events.send(HomeEvent.Message(it)) } }
    }

    override val gridSpec: GridSpec get() = HomeGridMetrics.spec

    fun onGridMeasured(spec: GridSpec) {
        HomeGridMetrics.spec = spec
    }

    fun onPageChanged(page: Int) {
        HomeGridMetrics.currentPage = page
    }

    fun clearQuery() = queryState.clearText()

    override fun launch(request: LaunchRequest) {
        clearQuery()
        super.launch(request)
    }

    // --------------------------------------------------------------------------- gestures

    fun canLock(): Boolean = uiState.value.settings.doubleTapToLock && gestures.lockMethod != LockMethod.NONE

    fun onDoubleTapWithoutLock() {
        if (uiState.value.settings.doubleTapToLock) _events.trySend(HomeEvent.LockSetupNeeded)
    }

    /** Called after the pre-lock dim/scale-out animation. */
    fun lockNow(): Boolean {
        val locked = gestures.lockScreen()
        if (!locked) _events.trySend(HomeEvent.LockSetupNeeded)
        return locked
    }

    fun onSwipeDown() {
        if (uiState.value.settings.swipeDownForNotifications) gestures.expandNotifications()
    }

    fun openClockApp() {
        runCatching {
            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun accessibilitySettingsIntent(): Intent = gestures.accessibilitySettingsIntent()
    fun deviceAdminIntent(): Intent = gestures.deviceAdminIntent()

    fun disableDoubleTapLock() {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(doubleTapToLock = false) } }
    }

    // ------------------------------------------------------------------------------ dock

    fun moveDockEntry(entry: DockEntry, offset: Int) {
        viewModelScope.launch { dockRepository.move(entry, offset) }
    }

    fun unpinFolderFromDock(folder: CustomFolder) {
        viewModelScope.launch { dockRepository.unpinFolder(folder.id) }
    }

    // ------------------------------------------------------------------------- home items

    fun removeItem(item: HomeItem) {
        viewModelScope.launch {
            homeLayout.remove(item.id)
            (item.content as? HomeItemContent.Widget)?.let { widgetHost.deleteId(it.appWidgetId) }
        }
    }

    fun resizeItem(item: HomeItem, spanX: Int, spanY: Int) {
        viewModelScope.launch { homeLayout.resize(item.id, spanX, spanY, gridSpec) }
    }

    fun moveItemToPage(item: HomeItem, page: Int) {
        viewModelScope.launch {
            val others = uiState.value.homeItems
            val target = HomeGridPlanner.findFreeCell(others, page, item.position.spanX, item.position.spanY, gridSpec)
            if (target == null || !homeLayout.move(item.id, target, gridSpec)) message("Page ${page + 1} has no room for that")
        }
    }

    fun addPage() {
        viewModelScope.launch { homeLayout.addPage() }
    }

    fun removePage(page: Int) {
        viewModelScope.launch {
            homeLayout.removePage(page).forEach { removed ->
                (removed.content as? HomeItemContent.Widget)?.let { widgetHost.deleteId(it.appWidgetId) }
            }
        }
    }

    fun movePage(from: Int, to: Int) {
        viewModelScope.launch { homeLayout.movePage(from, to) }
    }

    fun addFolderToHome(folder: CustomFolder) {
        viewModelScope.launch {
            val position = homeLayout.addFolder(folder.id, HomeGridMetrics.currentPage, gridSpec)
            message(if (position != null) "Added ${folder.name} to the home screen" else "The home screen is full")
        }
    }

    // ---------------------------------------------------------------------------- widgets

    fun widgetProviders(): List<WidgetProviderEntry> = widgetHost.providers()

    private var pendingWidget: Pair<Int, WidgetProviderEntry>? = null

    /** Step 1: allocate an id and bind (silently if allowed, otherwise via the system dialog). */
    fun addWidget(entry: WidgetProviderEntry) {
        val id = widgetHost.allocateId()
        pendingWidget = id to entry
        if (widgetHost.bindIfAllowed(id, entry)) onWidgetBound(id) else _events.trySend(HomeEvent.BindWidget(id, entry))
    }

    /** Step 2 (after binding): configure if the widget asks for it, otherwise place it. */
    fun onWidgetBound(appWidgetId: Int) {
        if (widgetHost.needsConfiguration(appWidgetId)) _events.trySend(HomeEvent.ConfigureWidget(appWidgetId)) else placeWidget(appWidgetId)
    }

    fun onWidgetBindResult(appWidgetId: Int, granted: Boolean) {
        if (granted) onWidgetBound(appWidgetId) else cancelWidget(appWidgetId)
    }

    fun onWidgetConfigured(appWidgetId: Int, success: Boolean) {
        if (success) placeWidget(appWidgetId) else cancelWidget(appWidgetId)
    }

    private fun cancelWidget(appWidgetId: Int) {
        widgetHost.deleteId(appWidgetId)
        pendingWidget = null
    }

    /** Step 3: find room for the widget's minimum size and save it. */
    private fun placeWidget(appWidgetId: Int) {
        val entry = pendingWidget?.takeIf { it.first == appWidgetId }?.second
        val info: AppWidgetProviderInfo = widgetHost.info(appWidgetId) ?: entry?.info ?: return cancelWidget(appWidgetId)
        val spec = gridSpec
        val metrics = HomeGridMetrics
        val density = context.resources.displayMetrics.density
        val (spanX, spanY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            info.targetCellWidth.coerceIn(1, spec.columns) to info.targetCellHeight.coerceIn(1, spec.rows - spec.reservedTopRowsOnFirstPage)
        } else {
            HomeGridPlanner.widgetSpan(info.minWidth / density, info.minHeight / density, metrics.cellWidthDp, metrics.cellHeightDp, spec)
        }
        viewModelScope.launch {
            val position = homeLayout.addWidget(appWidgetId, info.provider.flattenToString(), spanX, spanY, metrics.currentPage, spec)
            if (position == null) {
                cancelWidget(appWidgetId)
                message("No room for that widget. Free up space or add a page")
            } else {
                message("Widget added to page ${position.page + 1}")
            }
            pendingWidget = null
        }
    }

    // ------------------------------------------------------------------------- pills, clips

    fun onPillAction(pill: SmartPill) {
        when (val e = pill.extraction) {
            is SmartExtraction.Otp -> copyToClipboard("Code", e.code)
            is SmartExtraction.MeetingLink -> openUrl(e.url)
            is SmartExtraction.TrackingNumber -> e.trackingUrl?.let(::openUrl) ?: copyToClipboard("Tracking number", e.number)
        }
    }

    fun dismissPill(pill: SmartPill) {
        viewModelScope.launch { notifications.dismissPill(pill.notificationKey) }
    }

    /** Only on explicit focus of the command bar (Android shows a toast when the clipboard is read). */
    fun onCommandBarFocused(focused: Boolean) {
        if (!focused) {
            _clip.value = null
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        if (sensitive) return
        val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        _clip.value = ClipboardAnalyzer.analyze(text)
    }

    fun dismissClip() {
        _clip.value = null
    }

    fun copyToClipboard(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        message("$label copied")
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message("No app can open that link") }
    }

    fun dial(number: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun email(address: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private companion object {
        const val MAX_RESULTS = 5

        fun searchNames(app: AppInfo): List<String> =
            listOf(app.label, app.systemLabel, app.packageName.substringAfterLast('.')) + app.aliases
    }
}
