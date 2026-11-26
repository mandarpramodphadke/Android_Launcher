package com.ailauncher.presentation.home

import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ailauncher.data.widgets.LauncherAppWidgetHostView
import com.ailauncher.data.widgets.LauncherWidgetHost
import com.ailauncher.domain.home.DropAction
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.LauncherWidgetConfig
import com.ailauncher.domain.model.LauncherWidgetKind
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.FolderTile
import com.ailauncher.presentation.components.TileDragCallbacks
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import kotlin.math.roundToInt

/** Cell geometry of one home page, measured from the space the pager gives it. */
data class HomeCellMetrics(val columns: Int, val rows: Int, val cellWidth: Dp, val cellHeight: Dp) {
    companion object
}

/** Callbacks for items on a home page. */
class HomeGridActions(
    val onLaunch: (AppInfo, Rect) -> Unit,
    val onAppLongPress: (HomeItem, AppInfo) -> Unit,
    val onOpenFolder: (CustomFolder) -> Unit,
    val onFolderLongPress: (HomeItem, CustomFolder) -> Unit,
    val onWidgetLongPress: (HomeItem) -> Unit,
    /** Options of a launcher widget (which to-do list it shows). */
    val onConfigureWidget: (HomeItem) -> Unit = {},
)

/** Dragging apps and folders on a page (null when drag and drop is off). */
class HomeGridDrag(
    val state: HomeDragState,
    val onStart: (item: HomeItem, grab: Offset, tileSize: IntSize, pointer: Offset) -> Unit,
    val onMove: (pointer: Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
) {
    fun callbacksFor(item: HomeItem) = TileDragCallbacks(
        onDragStart = { grab, size, pointer -> onStart(item, grab, size, pointer) },
        onDrag = onMove,
        onDragEnd = onEnd,
        onDragCancel = onCancel,
    )
}

/**
 * One page's items on an absolute cell grid: apps and folders take one cell; widgets span several.
 * Rows that don't fit (e.g. a layout made on a taller phone) are simply not drawn.
 */
@Composable
fun HomeGrid(
    items: List<HomeItem>,
    metrics: HomeCellMetrics,
    firstRow: Int,
    apps: Map<String, AppInfo>,
    folders: Map<Long, CustomFolder>,
    appsOfFolder: (CustomFolder) -> List<AppInfo>,
    badges: (AppInfo) -> Int,
    tint: WallpaperTint,
    widgetHost: LauncherWidgetHost?,
    editingWidgetId: Long?,
    actions: HomeGridActions,
    onResizeWidget: (HomeItem, Int, Int) -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    onMoveItem: (HomeItem, Int) -> Unit,
    onDoneEditing: () -> Unit,
    pageCount: Int,
    modifier: Modifier = Modifier,
    page: Int = 0,
    drag: HomeGridDrag? = null,
    launcherWidget: @Composable (HomeItem, LauncherWidgetConfig) -> Unit = { _, _ -> },
) {
    val dragState = drag?.state
    Box(
        modifier
            .fillMaxSize()
            .then(if (dragState != null) Modifier.onGloballyPositioned { dragState.grids[page] = it to metrics } else Modifier),
    ) {
        (dragState?.target as? DropTarget.Cell)?.takeIf { it.page == page && dragState.isDragging }?.let { target ->
            DropHighlight(target, metrics, firstRow, tint)
        }
        items.forEach { item ->
            val p = item.position
            if (p.y < firstRow || p.y + p.spanY > metrics.rows || p.x + p.spanX > metrics.columns) return@forEach
            key(item.id) {
                val beingDragged = dragState?.item?.id == item.id
                Box(
                    Modifier
                        .offset(x = metrics.cellWidth * p.x, y = metrics.cellHeight * (p.y - firstRow))
                        .size(metrics.cellWidth * p.spanX, metrics.cellHeight * p.spanY)
                        .graphicsLayer { alpha = if (beingDragged) 0.3f else 1f },
                ) {
                    when (val content = item.content) {
                        is HomeItemContent.App -> apps[content.componentKey]?.takeIf { !it.isHidden }?.let { app ->
                            AppGridTile(
                                app = app,
                                onClick = { bounds -> actions.onLaunch(app, bounds) },
                                onLongClick = { actions.onAppLongPress(item, app) },
                                iconSize = 52.dp,
                                showLabel = LocalLauncherStyle.current.homeLabels,
                                labelColor = tint.content,
                                labelShadow = tint.shadow,
                                badgeCount = badges(app),
                                drag = drag?.callbacksFor(item),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        is HomeItemContent.Folder -> folders[content.folderId]?.let { folder ->
                            FolderTile(
                                folder = folder,
                                apps = appsOfFolder(folder),
                                onClick = { actions.onOpenFolder(folder) },
                                onLongClick = { actions.onFolderLongPress(item, folder) },
                                showLabel = LocalLauncherStyle.current.homeLabels,
                                labelColor = tint.content,
                                labelShadow = tint.shadow,
                                drag = drag?.callbacksFor(item),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        is HomeItemContent.LauncherWidget -> launcherWidget(item, content.config)
                        is HomeItemContent.Widget -> WidgetCell(
                            item = item,
                            appWidgetId = content.appWidgetId,
                            widgetHost = widgetHost,
                            widthDp = metrics.cellWidth * p.spanX,
                            heightDp = metrics.cellHeight * p.spanY,
                            onLongPress = { actions.onWidgetLongPress(item) },
                        )
                    }
                    if (editingWidgetId == item.id) {
                        val kind = (item.content as? HomeItemContent.LauncherWidget)?.config?.kind
                        WidgetEditOverlay(
                            item = item,
                            metrics = metrics,
                            pageCount = pageCount,
                            onResize = { sx, sy -> onResizeWidget(item, sx, sy) },
                            onRemove = { onRemoveItem(item) },
                            onMove = { page -> onMoveItem(item, page) },
                            onDone = onDoneEditing,
                            minSpanX = kind?.minSpanX ?: 1,
                            minSpanY = kind?.minSpanY ?: 1,
                            onSettings = if (kind == LauncherWidgetKind.TODO_LIST || kind == LauncherWidgetKind.QUICK_ADD) {
                                { actions.onConfigureWidget(item) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A live AppWidget (AppWidgetHostView inside Compose), or a placeholder in previews / if it's gone. */
@Composable
private fun WidgetCell(
    item: HomeItem,
    appWidgetId: Int,
    widgetHost: LauncherWidgetHost?,
    widthDp: Dp,
    heightDp: Dp,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    if (widgetHost == null || LocalInspectionMode.current) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f), modifier = Modifier.fillMaxSize().padding(6.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Widgets, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    AndroidView(
        factory = { context -> widgetHost.createView(context, appWidgetId) ?: FrameLayout(context) },
        update = { view ->
            if (view is LauncherAppWidgetHostView) {
                view.onLongPress = onLongPress
                widgetHost.updateSize(view, widthDp.value, heightDp.value)
            }
        },
        modifier = Modifier.fillMaxSize().padding(4.dp).clip(shape),
    )
}

/** Resize handle (drag the corner by whole cells), move between pages, remove. */
@Composable
private fun WidgetEditOverlay(
    item: HomeItem,
    metrics: HomeCellMetrics,
    pageCount: Int,
    onResize: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onDone: () -> Unit,
    minSpanX: Int = 1,
    minSpanY: Int = 1,
    onSettings: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var previewX by remember { mutableIntStateOf(item.position.spanX) }
    var previewY by remember { mutableIntStateOf(item.position.spanY) }
    val cellW = with(density) { metrics.cellWidth.toPx() }
    val cellH = with(density) { metrics.cellHeight.toPx() }
    val outlineAlpha by animateFloatAsState(1f, label = "edit-outline")
    Box(
        Modifier
            .size(metrics.cellWidth * previewX, metrics.cellHeight * previewY)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = outlineAlpha), RoundedCornerShape(20.dp)),
    ) {
        Row(Modifier.align(Alignment.TopCenter).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.position.page > 0) {
                FilledTonalIconButton(onClick = { onMove(item.position.page - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Move to previous page", Modifier.size(18.dp))
                }
            }
            FilledTonalIconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Delete, "Remove widget", Modifier.size(18.dp)) }
            if (onSettings != null) {
                FilledTonalIconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Tune, "Choose a list", Modifier.size(18.dp)) }
            }
            FilledTonalIconButton(onClick = onDone, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Check, "Done", Modifier.size(18.dp)) }
            if (item.position.page < pageCount - 1) {
                FilledTonalIconButton(onClick = { onMove(item.position.page + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Move to next page", Modifier.size(18.dp))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp, y = 6.dp)
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .pointerInput(item.id, cellW, cellH) {
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragEnd = { onResize(previewX, previewY) },
                        onDragCancel = {
                            previewX = item.position.spanX
                            previewY = item.position.spanY
                        },
                    ) { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        previewX = (item.position.spanX + (dragX / cellW).roundToInt())
                            .coerceIn(minSpanX.coerceAtMost(metrics.columns - item.position.x), metrics.columns - item.position.x)
                        previewY = (item.position.spanY + (dragY / cellH).roundToInt())
                            .coerceIn(minSpanY.coerceAtMost(metrics.rows - item.position.y), metrics.rows - item.position.y)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.OpenInFull, "Resize widget", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Where a dragged item would land: an outlined cell for a move, a folder-shaped backdrop when it
 * would make or join a folder, a red outline when it can't go there.
 */
@Composable
private fun DropHighlight(target: DropTarget.Cell, metrics: HomeCellMetrics, firstRow: Int, tint: WallpaperTint) {
    if (target.y < firstRow) return
    val cell = Modifier
        .offset(x = metrics.cellWidth * target.x, y = metrics.cellHeight * (target.y - firstRow))
        .size(metrics.cellWidth, metrics.cellHeight)
    Box(cell, contentAlignment = Alignment.Center) {
        when (target.action) {
            is DropAction.Move -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .background(tint.chip, RoundedCornerShape(18.dp))
                    .border(1.5.dp, tint.content.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            )
            is DropAction.CreateFolder, is DropAction.AddToFolder -> Box(
                Modifier
                    .offset(y = (-8).dp)
                    .size(68.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f), RoundedCornerShape(22.dp)),
            )
            is DropAction.SmartFolder -> Box(
                Modifier
                    .offset(y = (-8).dp)
                    .size(68.dp)
                    .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(22.dp)),
            )
            DropAction.None -> Unit
        }
    }
}

/** Subtle pill of dots above the dock showing the current home page. */
@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, tint: WallpaperTint, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Surface(shape = RoundedCornerShape(50), color = tint.chip, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pageCount) { page ->
                val selected = page == currentPage
                Box(
                    Modifier
                        .size(width = if (selected) 16.dp else 6.dp, height = 6.dp)
                        .background(tint.content.copy(alpha = if (selected) 1f else 0.5f), RoundedCornerShape(50)),
                )
            }
        }
    }
}

/** Shown on an empty extra page. */
@Composable
fun EmptyPageHint(tint: WallpaperTint, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Long-press an app or the wallpaper to add apps, folders or widgets here",
            style = MaterialTheme.typography.bodyMedium.copy(shadow = tint.shadow),
            color = tint.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

/**
 * Fits whole cells into the page: rows of at least [minCellHeight], the given column count.
 * [rows] (DOCK_AND_GRID) fixes the row count instead, but never below [usedRows] (the rows the
 * home items already use, so choosing fewer rows hides nothing) nor so many that cells get
 * shorter than [FIXED_MIN_CELL_HEIGHT].
 */
@Composable
fun rememberCellMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    columns: Int,
    minCellHeight: Dp = 92.dp,
    rows: Int = LauncherSettings.AUTO_ROWS,
    usedRows: Int = 0,
): HomeCellMetrics =
    remember(maxWidth, maxHeight, columns, rows, usedRows) {
        HomeCellMetrics.fit(maxWidth, maxHeight, columns, minCellHeight, rows, usedRows)
    }

@Composable
fun MeasuredPage(columns: Int, rows: Int = LauncherSettings.AUTO_ROWS, usedRows: Int = 0, content: @Composable (HomeCellMetrics) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content(rememberCellMetrics(maxWidth, maxHeight, columns, rows = rows, usedRows = usedRows))
    }
}

/** The row count for a page (pure, tested): see [rememberCellMetrics]. */
fun HomeCellMetrics.Companion.fit(maxWidth: Dp, maxHeight: Dp, columns: Int, minCellHeight: Dp, rows: Int, usedRows: Int): HomeCellMetrics {
    val auto = (maxHeight / minCellHeight).toInt().coerceIn(MIN_ROWS, MAX_ROWS)
    val chosen = if (rows in LauncherSettings.HOME_ROW_CHOICES) {
        rows.coerceAtMost((maxHeight / FIXED_MIN_CELL_HEIGHT).toInt().coerceAtLeast(MIN_ROWS))
    } else {
        auto
    }
    val count = maxOf(chosen, usedRows.coerceAtMost(MAX_ROWS))
    return HomeCellMetrics(columns, count, maxWidth / columns, maxHeight / count)
}

const val HEADER_ROWS = 2
private const val MIN_ROWS = 4
private const val MAX_ROWS = 7

/** A fixed row count never makes cells shorter than an icon and its name. */
private val FIXED_MIN_CELL_HEIGHT = 76.dp
