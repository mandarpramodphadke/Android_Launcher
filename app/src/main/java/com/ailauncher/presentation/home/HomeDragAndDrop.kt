package com.ailauncher.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.home.DropAction
import com.ailauncher.domain.home.GridSpec
import com.ailauncher.domain.home.HomeDropPlanner
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.FolderTile
import com.ailauncher.presentation.theme.WallpaperTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Where a dragged item would land. */
sealed interface DropTarget {
    data class Cell(val page: Int, val x: Int, val y: Int, val action: DropAction) : DropTarget

    /** Over the dock: pin the app or folder there. */
    data object Dock : DropTarget
}

/**
 * The app or folder being dragged on the home pages: the finger's position (window coordinates),
 * where it holds the tile, and the drop target under it. Pages and the dock report where they are
 * on screen so the cell under the finger can be worked out.
 */
@Stable
class HomeDragState {
    var item by mutableStateOf<HomeItem?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grab: Offset = Offset.Zero
        private set
    var tileSize by mutableStateOf(IntSize.Zero)
        private set
    var target by mutableStateOf<DropTarget?>(null)

    /** -1 / +1 while the finger rests at the left / right edge (flips the page), else 0. */
    var edge by mutableIntStateOf(0)

    internal val grids = HashMap<Int, Pair<LayoutCoordinates, HomeCellMetrics>>()
    internal var dock: LayoutCoordinates? = null
    internal var root: LayoutCoordinates? = null
    internal var pager: LayoutCoordinates? = null

    val isDragging: Boolean get() = item != null

    fun start(item: HomeItem, grab: Offset, tileSize: IntSize, pointer: Offset) {
        this.grab = grab
        this.tileSize = tileSize
        this.pointer = pointer
        this.item = item
    }

    fun move(pointer: Offset) {
        this.pointer = pointer
    }

    /** Ends the drag and returns what was dragged and where it was let go. */
    fun finish(): Pair<HomeItem, DropTarget?>? {
        val dragged = item ?: return null
        val landed = target
        reset()
        return dragged to landed
    }

    fun cancel() = reset()

    private fun reset() {
        item = null
        target = null
        edge = 0
    }

    /** The target under the finger on [page] ([scrolling]: the pager is between pages, so none). */
    fun resolveTarget(state: HomeUiState, page: Int, scrolling: Boolean, edgePx: Float): DropTarget? {
        val dragged = item ?: return null
        val p = pointer
        edge = pager?.takeIf { it.isAttached }?.boundsInWindow()?.let { bounds ->
            when {
                p.y !in bounds.top..bounds.bottom -> 0
                p.x < bounds.left + edgePx -> -1
                p.x > bounds.right - edgePx -> 1
                else -> 0
            }
        } ?: 0
        dock?.takeIf { it.isAttached }?.let { if (it.boundsInWindow().contains(p)) return DropTarget.Dock }
        if (scrolling) return null
        val (coords, metrics) = grids[page]?.takeIf { it.first.isAttached } ?: return null
        val local = coords.windowToLocal(p)
        val size = coords.size
        if (local.x < 0f || local.y < 0f || local.x >= size.width || local.y >= size.height) return null
        val x = (local.x / (size.width / metrics.columns.toFloat())).toInt().coerceIn(0, metrics.columns - 1)
        val y = (local.y / (size.height / metrics.rows.toFloat())).toInt().coerceIn(0, metrics.rows - 1)
        val spec = GridSpec(metrics.columns, metrics.rows, HEADER_ROWS)
        val action = HomeDropPlanner.resolve(state.homeItems, dragged, page, x, y, spec) { id -> state.folders[id]?.isSmart == true }
        return DropTarget.Cell(page, x, y, action)
    }
}

/**
 * Keeps the drop target current while a drag is on, and flips pages when the finger rests at the
 * screen edge (past the last page, a new page is added).
 */
@Composable
internal fun DragPageFlipper(
    drag: HomeDragState,
    pagerState: PagerState,
    state: HomeUiState,
    onAddPage: () -> Unit,
) {
    val currentState by rememberUpdatedState(state)
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    // Recompute when the finger moves or the pager settles on another page.
    LaunchedEffect(drag, pagerState) {
        snapshotFlow { Triple(drag.pointer, pagerState.currentPage, pagerState.isScrollInProgress) to drag.isDragging }
            .collect { (inputs, dragging) ->
                if (dragging) drag.target = drag.resolveTarget(currentState, inputs.second, inputs.third, edgePx)
            }
    }
    LaunchedEffect(drag, pagerState) {
        snapshotFlow { drag.edge.takeIf { drag.isDragging } ?: 0 }.collectLatest { edge ->
            while (edge != 0) {
                delay(EDGE_FLIP_DELAY_MILLIS)
                val next = pagerState.currentPage + edge
                when {
                    next in 0 until currentState.pageCount -> pagerState.animateScrollToPage(next)
                    edge > 0 && currentState.pageCount < MAX_PAGES -> {
                        onAddPage()
                        // The new page appears with the next state; then the loop scrolls to it.
                    }
                    else -> return@collectLatest
                }
            }
        }
    }
}

/** The tile under the finger while dragging, drawn above everything on the home screen. */
@Composable
internal fun DragGhost(drag: HomeDragState, state: HomeUiState, tint: WallpaperTint) {
    val item = drag.item ?: return
    val root = drag.root?.takeIf { it.isAttached } ?: return
    val density = LocalDensity.current
    val size = drag.tileSize
    Box(
        Modifier
            .offset {
                val local = root.windowToLocal(drag.pointer) - drag.grab
                IntOffset(local.x.roundToInt(), local.y.roundToInt())
            }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
                alpha = 0.95f
            }
            // A picture of the tile under the finger: not something to read out or tap.
            .clearAndSetSemantics {},
    ) {
        when (val content = item.content) {
            is HomeItemContent.App -> state.apps[content.componentKey]?.let { app ->
                AppGridTile(app = app, onClick = {}, onLongClick = {}, iconSize = 52.dp, labelColor = tint.content, labelShadow = tint.shadow)
            }
            is HomeItemContent.Folder -> state.folders[content.folderId]?.let { folder ->
                FolderTile(folder = folder, apps = state.appsOf(folder), onClick = {}, onLongClick = {}, labelColor = tint.content, labelShadow = tint.shadow)
            }
            else -> Unit
        }
    }
}

private const val EDGE_FLIP_DELAY_MILLIS = 650L
private const val MAX_PAGES = 9
