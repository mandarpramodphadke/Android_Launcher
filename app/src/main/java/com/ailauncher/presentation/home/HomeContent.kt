package com.ailauncher.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailauncher.data.widgets.LauncherWidgetHost
import com.ailauncher.domain.clipboard.ClipAction
import com.ailauncher.domain.clipboard.ClipInsight
import com.ailauncher.domain.home.GridSpec
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.SmartExtraction
import com.ailauncher.domain.model.SmartPill
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.FolderTile
import com.ailauncher.presentation.components.IconBounds
import com.ailauncher.presentation.components.trackBounds
import com.ailauncher.presentation.components.LauncherCommandBar
import com.ailauncher.presentation.components.brushTint
import com.ailauncher.presentation.components.sharedCommandBar
import com.ailauncher.presentation.theme.LauncherColorSchemes
import com.ailauncher.presentation.theme.LauncherStyle
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Everything the home content can ask for (wired to the ViewModel by [LauncherHomeScreen]). */
@Stable
class HomeCallbacks(
    val onLaunch: (AppInfo, Rect) -> Unit = { _, _ -> },
    val onAppLongPress: (AppInfo, HomeItem?) -> Unit = { _, _ -> },
    val onOpenFolder: (CustomFolder) -> Unit = {},
    val onFolderLongPress: (CustomFolder, HomeItem?) -> Unit = { _, _ -> },
    val onWidgetLongPress: (HomeItem) -> Unit = {},
    val onResizeWidget: (HomeItem, Int, Int) -> Unit = { _, _, _ -> },
    val onRemoveItem: (HomeItem) -> Unit = {},
    val onMoveItem: (HomeItem, Int) -> Unit = { _, _ -> },
    val onDoneEditing: () -> Unit = {},
    val onOpenClock: () -> Unit = {},
    val onSubmitQuery: (String) -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onVoice: (() -> Unit)? = null,
    val onOpenDrawer: () -> Unit = {},
    val onOpenPlanner: () -> Unit = {},
    val onPillAction: (SmartPill) -> Unit = {},
    val onPillDismiss: (SmartPill) -> Unit = {},
    val onClipAction: (ClipInsight, ClipAction) -> Unit = { _, _ -> },
    val onClipDismiss: () -> Unit = {},
    val onCommandBarFocus: (Boolean) -> Unit = {},
    val onGridMeasured: (GridSpec, Float, Float) -> Unit = { _, _, _ -> },
    val onPageChanged: (Int) -> Unit = {},
    /** Swipe left past the last page: open the assistant. */
    val onSwipePastLastPage: () -> Unit = {},
)

/** Per-element progress of the unlock entrance (1 = settled). */
data class HomeEntrance(val clock: Float = 1f, val pages: Float = 1f, val dock: Float = 1f, val bar: Float = 1f)

private fun Modifier.entrance(progress: Float): Modifier = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 48f
    val scale = 0.96f + 0.04f * progress
    scaleX = scale
    scaleY = scale
}

/**
 * The home screen's visible content, drawn over the wallpaper: a pager of home pages (page 0 has
 * the clock), the page indicator, the dock and the AI command bar. Stateless (gestures, sheets and
 * dialogs live in [LauncherHomeScreen]), so screenshot tests render it directly.
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    tint: WallpaperTint,
    style: LauncherStyle,
    pagerState: PagerState,
    queryState: TextFieldState,
    clip: ClipInsight?,
    widgetHost: LauncherWidgetHost?,
    editingWidgetId: Long?,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    entrance: HomeEntrance = HomeEntrance(),
) {
    Box(modifier.fillMaxSize()) {
        if (style.homeTextShade) ReadabilityScrims(tint, style.minimal)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = if (style.minimal) 28.dp else 12.dp),
        ) {
            if (style.minimal) {
                MinimalHome(state, tint, style, queryState, clip, callbacks, entrance)
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().entrance(entrance.pages).swipePastLastPage(pagerState, callbacks.onSwipePastLastPage)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        HomePage(page, state, tint, style, widgetHost, editingWidgetId, callbacks, entrance)
                    }
                }
                LaunchedEffect(pagerState.currentPage) { callbacks.onPageChanged(pagerState.currentPage) }
                PageIndicator(state.pageCount, pagerState.currentPage, tint, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp))
                AnimatedVisibility(visible = state.searchResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    SearchResultsRow(state.searchResults, callbacks)
                }
                Spacer(Modifier.height(8.dp))
                DockBar(state, callbacks, Modifier.entrance(entrance.dock))
                Spacer(Modifier.height(10.dp))
                if (state.settings.showAiCommandBar) {
                    CommandBarArea(queryState, clip, callbacks, Modifier.entrance(entrance.bar))
                }
                SwipeUpHint(tint, onClick = callbacks.onOpenDrawer)
            }
        }
    }
}

/** Swipe left past the last page opens the assistant (horizontal swipes otherwise belong to the pager). */
private fun Modifier.swipePastLastPage(pagerState: PagerState, onSwipe: () -> Unit): Modifier = pointerInput(pagerState) {
    val threshold = 96.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startedOnLast = pagerState.currentPage == pagerState.pageCount - 1 && abs(pagerState.currentPageOffsetFraction) < 0.01f
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            dx = change.position.x - down.position.x
            dy = change.position.y - down.position.y
            if (!change.pressed) break
        }
        if (startedOnLast && dx < -threshold && abs(dx) > abs(dy) * 1.5f) onSwipe()
    }
}

@Composable
private fun HomePage(
    page: Int,
    state: HomeUiState,
    tint: WallpaperTint,
    style: LauncherStyle,
    widgetHost: LauncherWidgetHost?,
    editingWidgetId: Long?,
    callbacks: HomeCallbacks,
    entrance: HomeEntrance,
) {
    MeasuredPage(state.settings.homeGridColumns) { metrics ->
        LaunchedEffect(metrics) {
            callbacks.onGridMeasured(GridSpec(metrics.columns, metrics.rows, HEADER_ROWS), metrics.cellWidth.value, metrics.cellHeight.value)
        }
        val items = state.homeItems.filter { it.position.page == page }
        Box(Modifier.fillMaxSize()) {
            if (page == 0) {
                Column(Modifier.fillMaxWidth().height(metrics.cellHeight * HEADER_ROWS).padding(horizontal = 8.dp).entrance(entrance.clock)) {
                    Spacer(Modifier.height(12.dp))
                    ClockWidget(state.now, state.use24Hour, tint, style, onClick = callbacks.onOpenClock)
                    Spacer(Modifier.height(10.dp))
                    HeaderChips(state, tint, callbacks)
                }
            } else if (items.isEmpty()) {
                EmptyPageHint(tint)
            }
            HomeGrid(
                items = items,
                metrics = metrics,
                firstRow = 0,
                apps = state.apps,
                folders = state.folders,
                appsOfFolder = state::appsOf,
                badges = state::badgeFor,
                tint = tint,
                widgetHost = widgetHost,
                editingWidgetId = editingWidgetId,
                actions = HomeGridActions(
                    onLaunch = callbacks.onLaunch,
                    onAppLongPress = { item, app -> callbacks.onAppLongPress(app, item) },
                    onOpenFolder = callbacks.onOpenFolder,
                    onFolderLongPress = { item, folder -> callbacks.onFolderLongPress(folder, item) },
                    onWidgetLongPress = callbacks.onWidgetLongPress,
                ),
                onResizeWidget = callbacks.onResizeWidget,
                onRemoveItem = callbacks.onRemoveItem,
                onMoveItem = callbacks.onMoveItem,
                onDoneEditing = callbacks.onDoneEditing,
                pageCount = state.pageCount,
            )
        }
    }
}

/** Alarm / reminder, smart pills (OTP, meeting link, tracking number) and the planner glance. */
@Composable
private fun HeaderChips(state: HomeUiState, tint: WallpaperTint, callbacks: HomeCallbacks) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.upcoming?.let { UpcomingPill(it, state.now, state.use24Hour, tint, plain = false, onClick = callbacks.onOpenClock) }
        state.pills.forEach { pill -> SmartPillChip(pill, tint, callbacks) }
        val glance = state.glance
        if (glance.openTodos > 0 || glance.nextBlock != null) {
            val text = buildList {
                if (glance.openTodos > 0) add("${glance.openTodos} to-do${if (glance.openTodos == 1) "" else "s"}")
                glance.nextBlock?.let { add("${it.title} ${it.startTime.format(DateTimeFormatter.ofPattern(if (state.use24Hour) "HH:mm" else "h:mm a"))}") }
            }.joinToString(" · ")
            WallpaperChip(icon = { Icon(Icons.Rounded.Checklist, null, Modifier.size(18.dp)) }, text = text, tint = tint, onClick = callbacks.onOpenPlanner)
        }
    }
}

@Composable
private fun SmartPillChip(pill: SmartPill, tint: WallpaperTint, callbacks: HomeCallbacks) {
    val (icon, text) = when (val e = pill.extraction) {
        is SmartExtraction.Otp -> Icons.Rounded.ContentCopy to "Code ${e.code} · ${pill.appLabel}"
        is SmartExtraction.MeetingLink -> Icons.Rounded.VideoCall to "Join ${e.provider}"
        is SmartExtraction.TrackingNumber -> Icons.Rounded.LocalShipping to "Track ${e.carrier} parcel"
    }
    WallpaperChip(
        icon = { Icon(icon, null, Modifier.size(18.dp)) },
        text = text,
        tint = tint,
        onClick = { callbacks.onPillAction(pill) },
        onDismiss = { callbacks.onPillDismiss(pill) },
    )
}

/** A chip drawn straight on the wallpaper, with a backing of the opposite polarity. */
@Composable
private fun WallpaperChip(icon: @Composable () -> Unit, text: String, tint: WallpaperTint, onClick: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = tint.chip, contentColor = tint.content) {
        Row(Modifier.padding(start = 12.dp, end = if (onDismiss != null) 2.dp else 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(16.dp)) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------- minimal

@Composable
private fun MinimalHome(
    state: HomeUiState,
    tint: WallpaperTint,
    style: LauncherStyle,
    queryState: TextFieldState,
    clip: ClipInsight?,
    callbacks: HomeCallbacks,
    entrance: HomeEntrance,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(72.dp))
        Box(Modifier.entrance(entrance.clock)) { ClockWidget(state.now, state.use24Hour, tint, style, onClick = callbacks.onOpenClock) }
        state.upcoming?.let { event ->
            Spacer(Modifier.height(12.dp))
            UpcomingPill(event, state.now, state.use24Hour, tint, plain = true, onClick = callbacks.onOpenClock)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.entrance(entrance.dock)) {
            MinimalAppList(
                entries = if (queryState.text.isBlank()) state.dock else state.searchResults.map { DockEntry.App(it) },
                searching = queryState.text.isNotBlank(),
                state = state,
                tint = tint,
                callbacks = callbacks,
            )
        }
        Spacer(Modifier.height(20.dp))
        if (state.settings.showAiCommandBar) CommandBarArea(queryState, clip, callbacks, Modifier.entrance(entrance.bar))
        Spacer(Modifier.height(16.dp))
    }
}

/** Minimal mode's favourites (or live search results): large words straight on the wallpaper. */
@Composable
private fun MinimalAppList(entries: List<DockEntry>, searching: Boolean, state: HomeUiState, tint: WallpaperTint, callbacks: HomeCallbacks) {
    val haptics = LocalHapticFeedback.current
    val labelStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, shadow = tint.shadow)
    Column(Modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            Text(
                if (searching) "No matching apps · press enter to ask the assistant"
                else "Long-press an app in the drawer and add it to the dock to list it here",
                style = MaterialTheme.typography.bodyMedium.copy(shadow = tint.shadow),
                color = tint.secondary,
            )
        }
        entries.forEach { entry ->
            val label = when (entry) {
                is DockEntry.App -> entry.app.label
                is DockEntry.Folder -> entry.folder.name
            }
            val bounds = remember { IconBounds() }
            Text(
                label,
                style = labelStyle,
                color = tint.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .trackBounds(bounds)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Open $label",
                        onLongClickLabel = "Options",
                        onClick = {
                            when (entry) {
                                is DockEntry.App -> callbacks.onLaunch(entry.app, bounds.rect)
                                is DockEntry.Folder -> callbacks.onOpenFolder(entry.folder)
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (entry) {
                                is DockEntry.App -> callbacks.onAppLongPress(entry.app, null)
                                is DockEntry.Folder -> callbacks.onFolderLongPress(entry.folder, null)
                            }
                        },
                    )
                    .padding(vertical = 8.dp),
            )
        }
    }
}

// ----------------------------------------------------------------------------- bottom area

@Composable
private fun CommandBarArea(queryState: TextFieldState, clip: ClipInsight?, callbacks: HomeCallbacks, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val onFocus by rememberUpdatedState(callbacks.onCommandBarFocus)
    LaunchedEffect(focused) { onFocus(focused) }
    Column(modifier) {
        AnimatedVisibility(visible = focused && clip != null, enter = fadeIn(), exit = fadeOut()) {
            clip?.let { ClipboardChips(it, callbacks) }
        }
        val style = LocalLauncherStyle.current
        LauncherCommandBar(
            state = queryState,
            onSubmit = callbacks.onSubmitQuery,
            onOpenChat = callbacks.onOpenChat,
            onVoice = callbacks.onVoice,
            interactionSource = interactionSource,
            sparkleModifier = accentBrush()?.let { Modifier.brushTint(it) } ?: Modifier,
            unfocusedBorder = if (style.theme == AppTheme.NEON) neonBrush() else null,
            modifier = Modifier.fillMaxWidth().sharedCommandBar(),
        )
    }
}

/** Contextual actions for what's on the clipboard (read only when the bar is focused). */
@Composable
private fun ClipboardChips(clip: ClipInsight, callbacks: HomeCallbacks) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    clip.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = callbacks.onClipDismiss, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, "Dismiss", Modifier.size(16.dp)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                clip.actions.forEach { action -> AssistChip(onClick = { callbacks.onClipAction(clip, action) }, label = { Text(action.label) }) }
            }
        }
    }
}

@Composable
private fun DockBar(state: HomeUiState, callbacks: HomeCallbacks, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = themedOutline(),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (state.dock.isEmpty()) {
            Text(
                "Long-press any app in the drawer to pin it here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.dock.forEach { entry ->
                    when (entry) {
                        is DockEntry.App -> AppGridTile(
                            app = entry.app,
                            onClick = { bounds -> callbacks.onLaunch(entry.app, bounds) },
                            onLongClick = { callbacks.onAppLongPress(entry.app, null) },
                            showLabel = false,
                            iconSize = 52.dp,
                            badgeCount = state.badgeFor(entry.app),
                            modifier = Modifier.weight(1f),
                        )
                        is DockEntry.Folder -> FolderTile(
                            folder = entry.folder,
                            apps = state.appsOf(entry.folder),
                            onClick = { callbacks.onOpenFolder(entry.folder) },
                            onLongClick = { callbacks.onFolderLongPress(entry.folder, null) },
                            showLabel = false,
                            iconSize = 52.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsRow(apps: List<AppInfo>, callbacks: HomeCallbacks) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = themedOutline(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            apps.forEach { app ->
                AppGridTile(
                    app = app,
                    onClick = { bounds -> callbacks.onLaunch(app, bounds) },
                    onLongClick = { callbacks.onAppLongPress(app, null) },
                    iconSize = 44.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(5 - apps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SwipeUpHint(tint: WallpaperTint, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "All apps", tint = tint.secondary)
        }
    }
}

// ------------------------------------------------------------------------------ wallpaper text

/**
 * Gradients at the top and bottom of the home screen in the text's opposite polarity (dark behind
 * light text, light behind dark text), so the clock and labels stay readable when the part of the
 * wallpaper behind them differs from the image as a whole.
 */
@Composable
private fun ReadabilityScrims(tint: WallpaperTint, minimal: Boolean) {
    // Same polarity as the text's backing, so on a typical wallpaper (which already suits the
    // text) the shade is barely visible; it only does real work over a mismatched region.
    val edge = tint.scrim.copy(alpha = 0.50f)
    val mid = tint.scrim.copy(alpha = 0.24f)
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(Brush.verticalGradient(0f to edge, 0.55f to mid, 1f to Color.Transparent)),
        )
        Spacer(Modifier.weight(if (minimal) 0.10f else 0.27f))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(if (minimal) 0.52f else 0.35f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to mid, 1f to edge)),
        )
    }
}

@Composable
internal fun ClockWidget(now: ZonedDateTime, use24Hour: Boolean, tint: WallpaperTint, style: LauncherStyle, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val time = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", locale))
    val meridiem = if (use24Hour) null else now.format(DateTimeFormatter.ofPattern("a", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    Column(Modifier.clickable(onClick = onClick, onClickLabel = "Open clock")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = clockTextStyle(tint, style))
            meridiem?.let {
                Spacer(Modifier.width(6.dp))
                Text(
                    it,
                    style = TextStyle(fontSize = 20.sp, color = tint.secondary, shadow = tint.shadow),
                    modifier = Modifier.padding(bottom = if (style.minimal) 11.dp else 14.dp),
                )
            }
        }
        Text(date, style = MaterialTheme.typography.titleMedium.copy(shadow = tint.shadow, fontWeight = FontWeight.Medium), color = tint.secondary)
    }
}

private val NeonCyan = Color(0xFF00E5FF)

/** The big clock, styled per theme: gradient (Colorful), glow (Neon), hairline (Monochrome, minimal). */
private fun clockTextStyle(tint: WallpaperTint, style: LauncherStyle): TextStyle {
    val base = TextStyle(
        fontSize = if (style.minimal) 60.sp else 72.sp,
        fontWeight = if (style.minimal || style.theme == AppTheme.MONOCHROME) FontWeight.Thin else FontWeight.Light,
        letterSpacing = (-2).sp,
        color = tint.content,
        shadow = tint.shadow,
    )
    return when (style.theme) {
        AppTheme.COLORFUL -> {
            // Accents of the variant that suits the wallpaper: deep tones on light, pastels on dark.
            val accents = LauncherColorSchemes.colorful(dark = !tint.darkText)
            base.copy(brush = Brush.linearGradient(listOf(accents.primary, accents.secondary, accents.tertiary)))
        }
        AppTheme.NEON -> if (tint.darkText) {
            base.copy(shadow = Shadow(NeonCyan, blurRadius = 22f))
        } else {
            base.copy(color = Color(0xFFE6FDFF), shadow = Shadow(NeonCyan, blurRadius = 26f))
        }
        else -> base
    }
}

@Composable
internal fun UpcomingPill(event: UpcomingEvent, now: ZonedDateTime, use24Hour: Boolean, tint: WallpaperTint, plain: Boolean, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val at = Instant.ofEpochMilli(event.triggerAtMillis).atZone(now.zone)
    val time = at.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> ""
        now.toLocalDate().plusDays(1) -> "Tomorrow "
        else -> at.format(DateTimeFormatter.ofPattern("EEE ", locale))
    }
    val label: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = if (plain) 0.dp else 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (event.kind == UpcomingEvent.Kind.ALARM) Icons.Rounded.Alarm else Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (plain) tint.secondary else tint.content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$day$time · ${event.title}",
                style = MaterialTheme.typography.labelLarge.copy(shadow = if (plain) tint.shadow else null),
                color = if (plain) tint.secondary else tint.content,
                maxLines = 1,
            )
        }
    }
    if (plain) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) { label() }
    } else {
        // The chip's backing has the opposite polarity of its text, so it reads on any wallpaper.
        Surface(onClick = onClick, shape = RoundedCornerShape(50), color = tint.chip, contentColor = tint.content) { label() }
    }
}

/** Glowing outline that the Neon theme gives its floating surfaces. */
@Composable
private fun themedOutline(): BorderStroke? {
    if (LocalLauncherStyle.current.theme != AppTheme.NEON) return null
    return BorderStroke(1.dp, neonBrush())
}

@Composable
private fun neonBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.horizontalGradient(listOf(scheme.primary.copy(alpha = 0.85f), scheme.secondary.copy(alpha = 0.85f)))
}

/** Multi-hue brush for accent icons in the Colorful and Neon themes. */
@Composable
private fun accentBrush(): Brush? {
    val theme = LocalLauncherStyle.current.theme
    if (theme != AppTheme.COLORFUL && theme != AppTheme.NEON) return null
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(scheme.primary, scheme.secondary, scheme.tertiary))
}
