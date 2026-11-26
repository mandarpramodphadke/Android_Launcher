package com.ailauncher.presentation.home

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.BuildConfig
import com.ailauncher.domain.clipboard.ClipAction
import com.ailauncher.domain.clipboard.ClipboardAnalyzer
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.GestureAction
import com.ailauncher.domain.model.HomeGesture
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.LauncherTool
import com.ailauncher.domain.model.LauncherWidgetKind
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.actionFor
import com.ailauncher.domain.model.appFor
import com.ailauncher.domain.usecase.TodoWidgets
import com.ailauncher.presentation.HomePress
import com.ailauncher.presentation.chat.AiChatScreen
import com.ailauncher.presentation.components.AppActionsSheet
import com.ailauncher.presentation.components.HomeGridMetrics
import com.ailauncher.presentation.components.LocalNavAnimatedVisibilityScope
import com.ailauncher.presentation.components.SpecialAccess
import com.ailauncher.presentation.components.SystemBarIcons
import com.ailauncher.presentation.components.findActivity
import com.ailauncher.presentation.components.rememberLaunchRequestFactory
import com.ailauncher.presentation.drawer.AppDrawerViewModel
import com.ailauncher.presentation.drawer.DrawerCallbacks
import com.ailauncher.presentation.drawer.DrawerContent
import com.ailauncher.presentation.drawer.PrivateSpaceAuth
import com.ailauncher.presentation.drawer.PrivateSpaceSheet
import com.ailauncher.presentation.drawer.authenticatePrivateSpace
import com.ailauncher.presentation.folders.FolderEditorSheet
import com.ailauncher.presentation.folders.FolderSheet
import com.ailauncher.presentation.folders.FoldersViewModel
import com.ailauncher.presentation.planner.PlannerTab
import com.ailauncher.presentation.planner.TodoEditorSheet
import com.ailauncher.presentation.system.SystemInfoScreen
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.rememberWallpaperTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class DrawerValue { Closed, Open }

/** The drawer follows the finger and settles on a slightly bouncy spring. */
private val DrawerSpring = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

private const val WIDGET_CONFIGURE_REQUEST = 0x5747 // "WG"

/** Long-pressed app, and the home item it sits in (null in the dock, drawer and search). */
private data class AppSheetTarget(val app: AppInfo, val item: HomeItem?)

private data class FolderMenuTarget(val folder: CustomFolder, val item: HomeItem?)

/** A request to show the assistant panel, optionally sending [prompt] ("open chat" from elsewhere). */
data class ChatRequest(val prompt: String?, val id: Long = System.nanoTime())

/** The to-do editor opened from a widget: a new to-do (in [list]) or an existing one. */
private data class TodoEdit(val item: TodoItem?, val list: String?)

/** The folder editor: a new folder (optionally pre-filled and placed on home) or an existing one. */
private data class FolderEdit(
    val folder: CustomFolder? = null,
    val initialSelection: List<String> = folder?.packageNames.orEmpty(),
    val placeOnHome: Boolean = false,
)

/**
 * The launcher's main surface: the home pages, dock and AI command bar, with the app drawer as a
 * drag-coupled overlay (swipe up anywhere, pull down to close) and every sheet the home screen
 * owns (app and folder menus, folders, widgets, pages, Private Space).
 */
@Composable
fun LauncherHomeScreen(
    homePresses: Flow<HomePress>,
    /** A folder the assistant asked to open ("open my Work folder"); cleared with [onFolderRequestHandled]. */
    folderRequest: Long?,
    onFolderRequestHandled: () -> Unit,
    onOpenChat: (prompt: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenTodos: () -> Unit = onOpenPlanner,
    /** The System info page (null when this build doesn't have it). */
    onOpenSystemInfo: (() -> Unit)? = null,
    /** Panels build: show the assistant (and send a prompt) from outside, e.g. a share or the planner. */
    chatRequest: ChatRequest? = null,
    onChatRequestHandled: () -> Unit = {},
    onOpenModelSetup: () -> Unit = {},
    onOpenPlannerTab: (PlannerTab) -> Unit = { onOpenPlanner() },
    viewModel: HomeViewModel = hiltViewModel(),
    drawerViewModel: AppDrawerViewModel = hiltViewModel(),
    foldersViewModel: FoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerUi by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val folderList by foldersViewModel.folders.collectAsStateWithLifecycle()
    val editorApps by foldersViewModel.apps.collectAsStateWithLifecycle()
    val clip by viewModel.clip.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val instant by viewModel.instantResults.collectAsStateWithLifecycle()
    val style = LocalLauncherStyle.current
    val tint = rememberWallpaperTint(style.homeTextColor)
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // ------------------------------------------------------------------------ panels
    // The assistant and System info slide in beside the home pages and follow the finger.
    val panelsEnabled = BuildConfig.CHAT_PANELS
    val panels = remember { HomePanelState(systemPanel = BuildConfig.SYSTEM_INFO) }
    var pendingPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    val panelDrag = rememberDraggableState { delta -> panels.dispatch(delta) }

    /** Opens the assistant: the panel in this build, otherwise the chat screen. */
    val openChat: (String?) -> Unit = { prompt ->
        if (panelsEnabled) {
            focusManager.clearFocus()
            pendingPrompt = prompt
            scope.launch { panels.animateTo(HomePanel.CHAT) }
        } else {
            onOpenChat(prompt)
        }
    }
    val openSystemInfo: (() -> Unit)? = when {
        !BuildConfig.SYSTEM_INFO -> null
        panelsEnabled -> ({ scope.launch { panels.animateTo(HomePanel.SYSTEM) } })
        else -> onOpenSystemInfo
    }
    val snackbar = remember { SnackbarHostState() }
    val launchFactory = rememberLaunchRequestFactory()

    var sheetApp by remember { mutableStateOf<AppSheetTarget?>(null) }
    var folderMenu by remember { mutableStateOf<FolderMenuTarget?>(null) }
    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var folderEdit by remember { mutableStateOf<FolderEdit?>(null) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showLockSetup by remember { mutableStateOf(false) }
    var editingWidgetId by remember { mutableStateOf<Long?>(null) }
    var todoEdit by remember { mutableStateOf<TodoEdit?>(null) }
    var widgetListTarget by remember { mutableStateOf<HomeItem?>(null) }
    /** A folder just made by dropping one app onto another: its editor opens once it has loaded. */
    var namingFolderId by remember { mutableStateOf<Long?>(null) }
    val hasUsageAccess by foldersViewModel.hasUsageAccess.collectAsStateWithLifecycle()
    var privateSpace by remember { mutableStateOf<PrivateSpaceAuth?>(null) }
    var pendingBindId by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingConfigureId by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }

    fun showMessage(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    // ------------------------------------------------------------------------- pager
    val pagerState = rememberPagerState { state.pageCount }
    LaunchedEffect(state.pageCount) {
        // A page was removed while it was showing.
        if (pagerState.currentPage >= state.pageCount) pagerState.scrollToPage(state.pageCount - 1)
    }

    // ------------------------------------------------------------------------ drawer
    val drawer = remember { AnchoredDraggableState(DrawerValue.Closed) }
    var drawerHeight by remember { mutableFloatStateOf(0f) }
    val drawerProgress by remember {
        derivedStateOf {
            val offset = drawer.offset
            if (drawerHeight <= 0f || offset.isNaN()) 0f else (1f - offset / drawerHeight).coerceIn(0f, 1f)
        }
    }
    val drawerVisible by remember { derivedStateOf { drawerProgress > 0.002f } }
    val drawerMostlyOpen by remember { derivedStateOf { drawerProgress > 0.5f } }
    val flingVelocity = with(density) { 600.dp.toPx() }

    fun currentDrawerOffset(): Float = drawer.offset.takeUnless { it.isNaN() } ?: drawerHeight

    /** Settles the drawer after a drag, carrying the finger's velocity into the spring. */
    suspend fun settleDrawer(velocity: Float) {
        val height = drawerHeight
        if (height <= 0f) return
        val openFraction = 1f - currentDrawerOffset() / height
        val target = when {
            velocity <= -flingVelocity -> DrawerValue.Open
            velocity >= flingVelocity -> DrawerValue.Closed
            drawer.settledValue == DrawerValue.Closed -> if (openFraction > 0.25f) DrawerValue.Open else DrawerValue.Closed
            else -> if (openFraction < 0.75f) DrawerValue.Closed else DrawerValue.Open
        }
        drawer.anchoredDrag(target) { anchors, latestTarget ->
            val to = anchors.positionOf(latestTarget)
            if (!to.isNaN()) {
                animate(currentDrawerOffset(), to, velocity, DrawerSpring) { value, speed -> dragTo(value, speed) }
            }
        }
    }

    fun openDrawer() {
        focusManager.clearFocus()
        scope.launch { drawer.animateTo(DrawerValue.Open, DrawerSpring) }
    }

    fun closeDrawer(animated: Boolean = true) {
        scope.launch { if (animated) drawer.animateTo(DrawerValue.Closed, DrawerSpring) else drawer.snapTo(DrawerValue.Closed) }
    }

    // Swipe up anywhere on home drags the drawer; a downward swipe opens the notification shade.
    val homePull = remember { floatArrayOf(0f) }
    val homeDrag = rememberDraggableState { delta ->
        homePull[0] += delta
        drawer.dispatchRawDelta(delta)
    }
    val drawerDrag = rememberDraggableState { delta -> drawer.dispatchRawDelta(delta) }
    val swipeDownDistance = with(density) { 72.dp.toPx() }

    // Pull down at the top of the drawer's list to close it; push up to re-open it mid-pull.
    val drawerScroll = remember(drawer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f && source == NestedScrollSource.UserInput && currentDrawerOffset() > 0f) {
                    Offset(0f, drawer.dispatchRawDelta(available.y))
                } else {
                    Offset.Zero
                }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, drawer.dispatchRawDelta(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentDrawerOffset() <= 0.5f) return Velocity.Zero
                settleDrawer(available.y)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (currentDrawerOffset() <= 0.5f) return Velocity.Zero
                settleDrawer(available.y)
                return available
            }
        }
    }

    LaunchedEffect(drawerVisible) {
        if (drawerVisible) {
            drawerViewModel.onDrawerOpened()
        } else {
            focusManager.clearFocus()
            drawerViewModel.clearQuery()
        }
    }
    BackHandler(enabled = drawerVisible) { closeDrawer() }
    // The launcher never "goes back" from home; Back only clears the command bar or ends widget editing.
    BackHandler(enabled = !drawerVisible) {
        focusManager.clearFocus()
        viewModel.clearQuery()
        editingWidgetId = null
    }

    // A panel in view: Back slides one panel towards home (registered last, so it wins).
    BackHandler(enabled = panelsEnabled && panels.showingPanel) {
        scope.launch { panels.animateTo(if (panels.current == HomePanel.SYSTEM) HomePanel.CHAT else HomePanel.HOME) }
    }

    val surfaceIsLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val panelMostlyOpen by remember { derivedStateOf { panels.chatProgress > 0.5f } }
    SystemBarIcons(darkIcons = if (drawerMostlyOpen || (panelsEnabled && panelMostlyOpen)) surfaceIsLight else tint.darkText)

    // -------------------------------------------------------------- lock & unlock motion
    val lockFade = remember { Animatable(0f) }
    val entrance = rememberHomeEntrance(onScreenOff = {
        privateSpace = null // Private Space locks with the screen.
        scope.launch { lockFade.snapTo(0f) }
    })
    /** Dims and scales home out, then locks; [onUnavailable] when no lock method is set up yet. */
    val lockWithAnimation: (onUnavailable: () -> Unit) -> Unit = { onUnavailable ->
        if (viewModel.canLock()) {
            scope.launch {
                lockFade.animateTo(1f, tween(PRE_LOCK_MILLIS, easing = FastOutLinearInEasing))
                if (viewModel.lockNow()) {
                    delay(600)
                    lockFade.snapTo(0f)
                } else {
                    lockFade.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        } else {
            onUnavailable()
        }
    }
    // CUSTOM_GESTURES: each gesture runs the action chosen in Settings.
    val drawerSearchFocus = remember { FocusRequester() }
    val multiTouch = remember { MultiTouchTracker() }
    val currentSettings by rememberUpdatedState(state.settings)
    val runGesture: (HomeGesture) -> Unit = { gesture ->
        when (currentSettings.actionFor(gesture)) {
            GestureAction.NOTHING -> Unit
            GestureAction.NOTIFICATIONS -> viewModel.expandNotifications()
            GestureAction.QUICK_SETTINGS -> viewModel.expandQuickSettings()
            GestureAction.LOCK -> lockWithAnimation(viewModel::lockUnavailable)
            GestureAction.ASSISTANT -> openChat(null)
            GestureAction.SEARCH -> {
                openDrawer()
                scope.launch {
                    delay(SEARCH_FOCUS_DELAY_MILLIS)
                    runCatching { drawerSearchFocus.requestFocus() }
                }
            }
            GestureAction.APP_DRAWER -> openDrawer()
            GestureAction.OPEN_APP -> viewModel.launchGestureApp(currentSettings.appFor(gesture))
            GestureAction.FLASHLIGHT -> viewModel.toggleFlashlight()
            GestureAction.SYSTEM_INFO -> openSystemInfo?.invoke() ?: openChat(null)
            GestureAction.PLANNER -> onOpenPlanner()
        }
    }
    val currentRunGesture by rememberUpdatedState(runGesture)
    val doubleTap: () -> Unit = if (BuildConfig.CUSTOM_GESTURES) {
        { runGesture(HomeGesture.DOUBLE_TAP) }
    } else {
        { lockWithAnimation { viewModel.onDoubleTapWithoutLock() } }
    }
    val currentDoubleTap by rememberUpdatedState(doubleTap)
    val currentEditing by rememberUpdatedState(editingWidgetId)

    // ------------------------------------------------------------------ voice & events
    val currentOpenChat by rememberUpdatedState(openChat)
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
            if (spoken.isNotBlank()) {
                viewModel.markSpoken(spoken)
                currentOpenChat(spoken)
            }
        }
    }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask your assistant")
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID } ?: pendingBindId
        viewModel.onWidgetBindResult(id, result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.LockSetupNeeded -> showLockSetup = true
                is HomeEvent.Message -> showMessage(event.text)
                is HomeEvent.BindWidget -> {
                    pendingBindId = event.appWidgetId
                    runCatching { bindLauncher.launch(viewModel.widgetHost.bindIntent(event.appWidgetId, event.entry)) }
                        .onFailure { viewModel.onWidgetBindResult(event.appWidgetId, granted = false) }
                }
                is HomeEvent.FolderCreated -> namingFolderId = event.folderId
                is HomeEvent.ConfigureWidget -> {
                    pendingConfigureId = event.appWidgetId
                    val activity = context.findActivity()
                    val started = activity != null && viewModel.widgetHost.startConfigure(activity, event.appWidgetId, WIDGET_CONFIGURE_REQUEST)
                    if (!started) {
                        viewModel.onWidgetConfigured(event.appWidgetId, success = false)
                        showMessage("That widget can't be set up here")
                    }
                }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.widgetHost.configureResults.collect { result ->
            if (result.requestCode == WIDGET_CONFIGURE_REQUEST) {
                val id = result.appWidgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID && it != -1 } ?: pendingConfigureId
                viewModel.onWidgetConfigured(id, result.success)
            }
        }
    }
    LaunchedEffect(drawerViewModel) { drawerViewModel.messages.collect { showMessage(it) } }
    LaunchedEffect(foldersViewModel) { foldersViewModel.messages.collect { showMessage(it) } }

    // Home pressed while the launcher is showing: close everything, then return to page 1.
    LaunchedEffect(homePresses) {
        homePresses.collect { press ->
            sheetApp = null
            folderMenu = null
            openFolderId = null
            folderEdit = null
            todoEdit = null
            widgetListTarget = null
            showHomeMenu = false
            showWidgetPicker = false
            showPages = false
            privateSpace = null
            editingWidgetId = null
            focusManager.clearFocus()
            viewModel.clearQuery()
            when {
                panelsEnabled && panels.showingPanel -> if (press.alreadyOnHome) panels.animateTo(HomePanel.HOME) else panels.snapTo(HomePanel.HOME)
                drawerVisible -> if (press.alreadyOnHome) drawer.animateTo(DrawerValue.Closed, DrawerSpring) else drawer.snapTo(DrawerValue.Closed)
                press.alreadyOnHome && pagerState.currentPage != 0 -> pagerState.animateScrollToPage(0)
            }
        }
    }

    LaunchedEffect(namingFolderId, folderList) {
        val id = namingFolderId ?: return@LaunchedEffect
        val folder = folderList.firstOrNull { it.id == id } ?: return@LaunchedEffect
        namingFolderId = null
        folderEdit = FolderEdit(folder)
    }

    // "Open my Work folder" from the assistant.
    LaunchedEffect(chatRequest) {
        val request = chatRequest ?: return@LaunchedEffect
        onChatRequestHandled()
        openChat(request.prompt)
    }

    LaunchedEffect(folderRequest) {
        val id = folderRequest ?: return@LaunchedEffect
        if (drawerVisible) drawer.snapTo(DrawerValue.Closed)
        if (panelsEnabled && panels.showingPanel) panels.snapTo(HomePanel.HOME)
        openFolderId = id
        onFolderRequestHandled()
    }

    val submitQuery: (String) -> Unit = { text ->
        focusManager.clearFocus()
        viewModel.clearQuery()
        openChat(text)
    }

    val extras = HomeExtrasCallbacks(
        onMediaPlayPause = viewModel::mediaPlayPause,
        onMediaNext = viewModel::mediaNext,
        onMediaPrevious = viewModel::mediaPrevious,
        onMediaOpen = viewModel::openMediaApp,
        onCopyAnswer = viewModel::copyAnswer,
        onSettingsPage = { page ->
            focusManager.clearFocus()
            viewModel.openSettingsPage(page)
        },
        onCallContact = { contact ->
            focusManager.clearFocus()
            viewModel.callContact(contact)
        },
        onMessageContact = { contact ->
            focusManager.clearFocus()
            viewModel.messageContact(contact)
        },
        onTodo = {
            focusManager.clearFocus()
            viewModel.clearQuery()
            onOpenTodos()
        },
        onFile = { file ->
            focusManager.clearFocus()
            viewModel.openFile(file)
        },
    )

    // Blur the home screen behind sheets and menus (full version: BLUR_EFFECTS, Android 12+).
    val sheetOpen = sheetApp != null || folderMenu != null || openFolderId != null || folderEdit != null || showHomeMenu || showWidgetPicker || showPages
    val sheetBlur by animateFloatAsState(if (style.blur && sheetOpen) 1f else 0f, label = "sheet-blur")
    WallpaperParallax(enabled = style.wallpaperParallax && !style.minimal, pagerState = pagerState)

    val callbacks = HomeCallbacks(
        onLaunch = { app, bounds -> viewModel.launch(launchFactory(app, bounds)) },
        onAppLongPress = { app, item -> sheetApp = AppSheetTarget(app, item) },
        onOpenFolder = { folder -> openFolderId = folder.id },
        onFolderLongPress = { folder, item -> folderMenu = FolderMenuTarget(folder, item) },
        onWidgetLongPress = { item -> editingWidgetId = item.id },
        onResizeWidget = viewModel::resizeItem,
        onRemoveItem = { item ->
            if (item.id == editingWidgetId) editingWidgetId = null
            viewModel.removeItem(item)
        },
        onMoveItem = { item, page ->
            viewModel.moveItemToPage(item, page)
            scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, state.pageCount - 1)) }
        },
        onDoneEditing = { editingWidgetId = null },
        onOpenClock = viewModel::openClockApp,
        onSubmitQuery = submitQuery,
        onOpenChat = { openChat(null) },
        onVoice = if (voiceAvailable) ({ voiceLauncher.launch(voiceIntent) }) else null,
        onOpenDrawer = ::openDrawer,
        onOpenPlanner = onOpenPlanner,
        onPillAction = viewModel::onPillAction,
        onPillDismiss = viewModel::dismissPill,
        onClipAction = { insight, action ->
            focusManager.clearFocus()
            viewModel.dismissClip()
            when (action) {
                ClipAction.OPEN_LINK -> viewModel.openUrl(insight.text.trim())
                ClipAction.CALL -> viewModel.dial(insight.text.trim())
                ClipAction.EMAIL -> viewModel.email(insight.text.trim())
                ClipAction.COPY_CODE -> viewModel.copyToClipboard("Code", insight.text.trim())
                else -> ClipboardAnalyzer.promptFor(action, insight.text)?.let { openChat(it) }
            }
        },
        onClipDismiss = viewModel::dismissClip,
        onCommandBarFocus = viewModel::onCommandBarFocused,
        onGridMeasured = { spec, cellWidth, cellHeight ->
            viewModel.onGridMeasured(spec)
            HomeGridMetrics.cellWidthDp = cellWidth
            HomeGridMetrics.cellHeightDp = cellHeight
        },
        onPageChanged = viewModel::onPageChanged,
        // With panels, the pager's own overscroll pulls the assistant in (see pagerNestedScroll).
        onSwipePastLastPage = { if (!panelsEnabled) openChat(null) },
        dragEnabled = BuildConfig.HOME_DRAG_AND_DROP,
        onDropItem = viewModel::drop,
        onAddPage = viewModel::addPage,
        onConfigureWidget = { item -> widgetListTarget = item },
        widgets = LauncherWidgetCallbacks(
            onToggleTodo = viewModel::toggleTodo,
            onAddTodo = { list -> todoEdit = TodoEdit(null, list) },
            onEditTodo = { item -> todoEdit = TodoEdit(item, item.listName) },
            onOpenTodos = onOpenTodos,
            onOpenPlanner = onOpenPlanner,
        ),
    )

    fun openPrivateSpace() {
        focusManager.clearFocus()
        authenticatePrivateSpace(context.findActivity() ?: context) { result ->
            when (result) {
                is PrivateSpaceAuth.Failed -> showMessage(result.message)
                else -> privateSpace = result
            }
        }
    }

    /** A launcher feature from the "AI Launcher" tools folder. */
    fun openTool(tool: LauncherTool) {
        when (tool) {
            LauncherTool.ASSISTANT -> openChat(null)
            LauncherTool.MY_DAY -> openChat("Summarize my day")
            LauncherTool.PLANNER -> onOpenPlanner()
            LauncherTool.TODOS -> onOpenTodos()
            LauncherTool.CATCH_UP -> openChat("What did I miss?")
            LauncherTool.PRIVATE_SPACE -> openPrivateSpace()
            LauncherTool.WIDGETS -> showWidgetPicker = true
            LauncherTool.SETTINGS -> onOpenSettings()
            LauncherTool.SYSTEM_INFO -> openSystemInfo?.invoke()
        }
    }

    val drawerCallbacks = DrawerCallbacks(
        onLaunch = { app, bounds -> viewModel.launch(launchFactory(app, bounds)) },
        onLongPress = { app -> sheetApp = AppSheetTarget(app, null) },
        onToggleLayout = drawerViewModel::toggleLayout,
        onOpenSettings = onOpenSettings,
        onSelectTab = drawerViewModel::setTab,
        onOrganize = { drawerViewModel.organizeWithAi() },
        onOpenFolder = { folder -> openFolderId = folder.id },
        onCreateFolder = { folderEdit = FolderEdit() },
        onOpenPlanner = onOpenPlanner,
        onOpenPrivateSpace = ::openPrivateSpace,
    )

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                panels.updateWidth(size.width.toFloat())
                val height = size.height.toFloat()
                if (height > 0f && height != drawerHeight) {
                    drawerHeight = height
                    drawer.updateAnchors(
                        DraggableAnchors {
                            DrawerValue.Closed at height
                            DrawerValue.Open at 0f
                        },
                        drawer.targetValue,
                    )
                }
            },
    ) {
        // ------------------------------------------------------------------ home layer
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Recede behind the drawer, and dim/scale out just before the screen locks.
                    val lock = lockFade.value
                    val scale = (1f - 0.05f * drawerProgress) * (1f - 0.06f * lock)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - 0.6f * drawerProgress) * (1f - 0.85f * lock)
                    if (style.blur) {
                        val amount = maxOf(drawerProgress, sheetBlur)
                        renderEffect = homeBlur(amount * HOME_BLUR_RADIUS.toPx())
                    }
                    // A panel sliding in pushes home a little to the side (parallax) and fades it,
                    // so a see-through chat (wallpaper background) shows only the wallpaper.
                    if (panelsEnabled) {
                        translationX = panels.offset.coerceAtLeast(-panels.width) * PANEL_PARALLAX
                        alpha *= 1f - panels.chatProgress
                    }
                }
                // Attached for the whole session in builds with the feature (see twoFingerSwipeDown).
                .then(
                    if (BuildConfig.CUSTOM_GESTURES) {
                        Modifier.twoFingerSwipeDown(
                            enabled = { currentEditing == null && !(panelsEnabled && panels.showingPanel) },
                            tracker = multiTouch,
                        ) { currentRunGesture(HomeGesture.TWO_FINGER_SWIPE_DOWN) }
                    } else {
                        Modifier
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusManager.clearFocus()
                            if (currentEditing != null) editingWidgetId = null
                        },
                        onDoubleTap = { currentDoubleTap() },
                        onLongPress = { if (currentEditing == null) showHomeMenu = true },
                    )
                }
                .draggable(
                    state = homeDrag,
                    orientation = Orientation.Vertical,
                    enabled = editingWidgetId == null && !(panelsEnabled && panels.showingPanel),
                    onDragStarted = {
                        homePull[0] = 0f
                        // Grabbing the drawer mid-animation takes over from the spring.
                        drawer.anchoredDrag(MutatePriority.UserInput) { }
                    },
                    onDragStopped = { velocity ->
                        val pulledDown = homePull[0] > 0f && (homePull[0] > swipeDownDistance || velocity > flingVelocity)
                        when {
                            !pulledDown || currentDrawerOffset() < drawerHeight - 0.5f -> settleDrawer(velocity)
                            !BuildConfig.CUSTOM_GESTURES -> viewModel.onSwipeDown()
                            // A two-finger swipe has its own action (below), not swipe down's.
                            !multiTouch.active -> currentRunGesture(HomeGesture.SWIPE_DOWN)
                        }
                    },
                ),
        ) {
            HomeContent(
                state = state,
                tint = tint,
                style = style,
                pagerState = pagerState,
                queryState = viewModel.queryState,
                clip = clip,
                widgetHost = viewModel.widgetHost,
                editingWidgetId = editingWidgetId,
                callbacks = callbacks,
                entrance = entrance,
                pagerNestedScroll = if (panelsEnabled) panels.pagerConnection else null,
                nowPlaying = nowPlaying,
                instant = instant,
                extras = extras,
                // Minimal mode has no pager: a sideways drag anywhere pulls the assistant in.
                panelDrag = if (panelsEnabled && style.minimal) {
                    Modifier.draggable(
                        state = panelDrag,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { panels.grab() },
                        onDragStopped = { velocity -> panels.settle(velocity) },
                    )
                } else {
                    Modifier
                },
            )
        }

        // ---------------------------------------------------------------- drawer layer
        if (drawerVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = drawerProgress }
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        // Until the anchors exist (first frame) the drawer waits below the screen.
                        val offset = drawer.offset.takeUnless { it.isNaN() || drawerHeight <= 0f } ?: placeable.height.toFloat()
                        placeable.place(0, offset.roundToInt())
                    }
                },
        ) {
            // A strip of drawer colour below the bottom edge, revealed when the spring overshoots.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 120.dp)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
            )
            DrawerContent(
                state = drawerUi,
                queryState = drawerViewModel.queryState,
                searchFocus = drawerSearchFocus,
                minimal = style.minimal,
                callbacks = drawerCallbacks,
                modifier = Modifier.draggable(
                    state = drawerDrag,
                    orientation = Orientation.Vertical,
                    onDragStarted = { drawer.anchoredDrag(MutatePriority.UserInput) { } },
                    onDragStopped = { velocity -> settleDrawer(velocity) },
                ),
                handleModifier = Modifier.clickable(onClickLabel = "Close app drawer") { closeDrawer() },
                listModifier = Modifier.nestedScroll(drawerScroll),
            )
        }

        // -------------------------------------------------------------- panel layer
        if (panelsEnabled && panels.showingPanel) {
            // A shadow that deepens while the panel moves and is gone once it has settled.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = panels.chatProgress
                        alpha = 4f * p * (1f - p)
                    }
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
            val panelGesture = Modifier.draggable(
                state = panelDrag,
                orientation = Orientation.Horizontal,
                onDragStarted = { panels.grab() },
                onDragStopped = { velocity -> panels.settle(velocity) },
            )
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = panels.width + panels.offset }
                        .then(panelGesture),
                ) {
                    AiChatScreen(
                        onBack = { scope.launch { panels.animateTo(HomePanel.HOME) } },
                        onOpenModelSetup = onOpenModelSetup,
                        onOpenPlanner = onOpenPlannerTab,
                        onOpenSystemInfo = openSystemInfo,
                        pendingPrompt = pendingPrompt,
                        onPromptConsumed = { pendingPrompt = null },
                        asPanel = true,
                    )
                }
                if (panels.systemVisible) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = 2 * panels.width + panels.offset }
                            .then(panelGesture),
                    ) {
                        SystemInfoScreen(onBack = { scope.launch { panels.animateTo(HomePanel.CHAT) } }, asPanel = true)
                    }
                }
            }
        }

        // Pre-lock dim.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = lockFade.value }
                .background(Color.Black.copy(alpha = 0.6f)),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }

    // ------------------------------------------------------------------------ sheets
    sheetApp?.let { target ->
        AppActionsSheet(
            app = target.app,
            handler = viewModel,
            onDismiss = { sheetApp = null },
            showDockReorder = true,
            folders = folderList,
            onCreateFolder = { folderEdit = FolderEdit(initialSelection = listOf(target.app.packageName)) },
            onRemoveFromHome = target.item?.let { item -> { viewModel.removeItem(item) } },
        )
    }

    folderMenu?.let { target ->
        FolderActionsSheet(
            folder = target.folder,
            apps = state.appsOf(target.folder),
            onOpen = { openFolderId = target.folder.id },
            onEdit = { folderEdit = FolderEdit(target.folder) },
            onToggleDock = { foldersViewModel.toggleDock(target.folder) },
            onRemoveFromHome = target.item?.let { item -> { viewModel.removeItem(item) } },
            onDelete = { foldersViewModel.delete(target.folder) },
            onDismiss = { folderMenu = null },
        )
    }

    openFolderId?.let { id ->
        // A folder that was just deleted (or hasn't loaded yet) simply shows nothing.
        val folder = state.folders[id] ?: folderList.firstOrNull { it.id == id }
        if (folder != null) {
            // Also on returning from the Usage access screen, so the hint and the apps update.
            LifecycleResumeEffect(folder.id) {
                foldersViewModel.onFolderOpened(folder)
                onPauseOrDispose { }
            }
            FolderSheet(
                folder = folder,
                apps = state.appsOf(folder),
                columns = state.settings.homeGridColumns,
                onLaunch = { request ->
                    openFolderId = null
                    viewModel.launch(request)
                },
                onEditDetails = { folderEdit = FolderEdit(folder) },
                onEditApps = { folderEdit = FolderEdit(folder) },
                onRemoveApp = { app -> foldersViewModel.removeApp(folder, app.packageName) },
                onAddToHome = { foldersViewModel.addToHome(folder) },
                onToggleDock = { foldersViewModel.toggleDock(folder) },
                onDelete = {
                    openFolderId = null
                    foldersViewModel.delete(folder)
                },
                onDismiss = { openFolderId = null },
                onOpenTool = ::openTool,
                onSmartAppLongPress = { app ->
                    openFolderId = null
                    sheetApp = AppSheetTarget(app, null)
                },
                onMakeEditable = { foldersViewModel.makeEditable(folder) },
                usageAccessMissing = folder.rule?.usesAppUsage == true && !hasUsageAccess,
                onAllowUsageAccess = { SpecialAccess.open(context, SpecialAccess.usageAccessIntent(context), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        }
    }

    folderEdit?.let { edit ->
        LaunchedEffect(edit) { if (BuildConfig.SMART_FOLDERS) foldersViewModel.loadPreviewUsage() }
        FolderEditorSheet(
            existing = edit.folder,
            allApps = editorApps,
            initialSelection = edit.initialSelection,
            onSave = { name, description, accent, packages, rule ->
                foldersViewModel.save(edit.folder, name, description, accent, packages, rule, placeOnHome = edit.placeOnHome)
                folderEdit = null
            },
            onDismiss = { folderEdit = null },
            smartFolders = BuildConfig.SMART_FOLDERS,
            previewCount = foldersViewModel::previewCount,
        )
    }

    todoEdit?.let { edit ->
        TodoEditorSheet(
            existing = edit.item,
            lists = TodoWidgets.lists(state.widgetData.todos),
            initialList = edit.list,
            onSave = { title, notes, list, priority, due ->
                viewModel.saveTodo(edit.item, title, notes, list, priority, due)
                todoEdit = null
            },
            onDelete = edit.item?.let { item ->
                {
                    viewModel.deleteTodo(item)
                    todoEdit = null
                }
            },
            onDismiss = { todoEdit = null },
        )
    }

    widgetListTarget?.let { item ->
        val config = (item.content as? HomeItemContent.LauncherWidget)?.config
        if (config == null) {
            widgetListTarget = null
        } else {
            WidgetListDialog(
                lists = TodoWidgets.lists(state.widgetData.todos),
                selected = config.listName,
                onPick = { list ->
                    viewModel.setLauncherWidgetList(item, list)
                    widgetListTarget = null
                },
                onDismiss = { widgetListTarget = null },
            )
        }
    }

    if (showHomeMenu) {
        HomeMenuSheet(
            onWallpaper = {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onWidgets = { showWidgetPicker = true },
            onPages = { showPages = true },
            onNewFolder = { folderEdit = FolderEdit(placeOnHome = true) },
            onPlanner = onOpenPlanner,
            onSettings = onOpenSettings,
            onAssistant = { openChat(null) },
            onDismiss = { showHomeMenu = false },
        )
    }

    if (showWidgetPicker) {
        val providers by produceState<List<com.ailauncher.data.widgets.WidgetProviderEntry>?>(null) {
            value = withContext(Dispatchers.IO) { runCatching { viewModel.widgetProviders() }.getOrDefault(emptyList()) }
        }
        WidgetPickerSheet(
            providers = providers,
            preview = { entry -> viewModel.widgetHost.previewImage(entry) },
            onPick = viewModel::addWidget,
            onDismiss = { showWidgetPicker = false },
            launcherWidgets = if (BuildConfig.TODO_WIDGETS) LauncherWidgetKind.entries else emptyList(),
            onPickLauncherWidget = viewModel::addLauncherWidget,
        )
    }

    if (showPages) {
        PagesSheet(
            pageCount = state.pageCount,
            items = state.homeItems,
            onAdd = viewModel::addPage,
            onRemove = viewModel::removePage,
            onMove = viewModel::movePage,
            onDismiss = { showPages = false },
        )
    }

    privateSpace?.let { auth ->
        PrivateSpaceSheet(
            apps = drawerUi.hiddenApps,
            columns = drawerUi.columns,
            unprotected = auth == PrivateSpaceAuth.NoScreenLock,
            onLaunch = { request ->
                privateSpace = null
                viewModel.launch(request)
            },
            onUnhide = drawerViewModel::unhide,
            onDismiss = { privateSpace = null },
        )
    }

    if (showLockSetup) {
        AlertDialog(
            onDismissRequest = { showLockSetup = false },
            title = { Text("Double-tap to lock") },
            text = {
                Text(
                    "To turn the screen off with a double tap, enable \"AI Launcher gestures\" in Accessibility settings. " +
                        "It is used only to lock the screen and open notifications - it never reads what is on your screen. " +
                        "Alternatively, allow screen-lock as a device admin (this may require your PIN instead of fingerprint on next unlock).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLockSetup = false
                    runCatching { context.startActivity(viewModel.accessibilitySettingsIntent()) }
                }) { Text("Accessibility settings") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showLockSetup = false
                        runCatching { context.startActivity(viewModel.deviceAdminIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Device admin") }
                    TextButton(onClick = {
                        showLockSetup = false
                        viewModel.disableDoubleTapLock()
                    }) { Text("Turn off") }
                }
            },
        )
    }
}

/** Which to-do list a widget shows: all lists, or one. */
@Composable
private fun WidgetListDialog(lists: List<String>, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show to-dos from") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<String?>(null) + lists).forEach { list ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(selected = selected.equals(list, ignoreCase = true), role = Role.RadioButton) { onPick(list) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected.equals(list, ignoreCase = true), onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(list ?: "All lists", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** How far home moves for each pixel a panel slides in. */
private const val PANEL_PARALLAX = 0.25f

/** CUSTOM_GESTURES: the drawer opens before its search field can take the keyboard. */
private const val SEARCH_FOCUS_DELAY_MILLIS = 250L
