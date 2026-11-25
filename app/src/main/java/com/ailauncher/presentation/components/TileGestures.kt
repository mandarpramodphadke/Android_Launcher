package com.ailauncher.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drag callbacks for a home-screen tile. Positions are in window coordinates.
 *
 * @property onDragStart the finger's position inside the tile, the tile's size, and the finger's
 *   position on screen.
 */
class TileDragCallbacks(
    val onDragStart: (grab: Offset, tileSize: IntSize, pointer: Offset) -> Unit,
    val onDrag: (pointer: Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit,
)

/** What a tile does on tap, long press and drag; see [rememberTileGestureState] and [tileGestures]. */
@Stable
class TileGestureState internal constructor(internal val haptics: HapticFeedback) {
    internal var onTap: () -> Unit = {}
    internal var onLongPress: () -> Unit = {}
    internal var drag: TileDragCallbacks? = null
    internal var onHeld: (Boolean) -> Unit = {}
    internal var coordinates: LayoutCoordinates? = null
}

/**
 * Holds the latest callbacks for [tileGestures], so recomposition never interrupts a drag in
 * progress (the gesture reads them from here instead of restarting).
 */
@Composable
fun rememberTileGestureState(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    drag: TileDragCallbacks,
    onHeld: (Boolean) -> Unit = {},
): TileGestureState {
    val haptics = LocalHapticFeedback.current
    val state = remember(haptics) { TileGestureState(haptics) }
    SideEffect {
        state.onTap = onTap
        state.onLongPress = onLongPress
        state.drag = drag
        state.onHeld = onHeld
    }
    return state
}

/**
 * Tap, long press and long-press-then-drag in one gesture, for tiles that can be moved:
 * - a tap calls `onTap`;
 * - a long press released without moving calls `onLongPress` (the item's menu);
 * - a long press followed by a move drags the tile until the finger lifts.
 *
 * A swipe that starts before the long press is left to the pager and the drawer.
 */
fun Modifier.tileGestures(state: TileGestureState, tapLabel: String, longPressLabel: String): Modifier = this
    .semantics {
        role = Role.Button
        onClick(label = tapLabel) {
            state.onTap()
            true
        }
        onLongClick(label = longPressLabel) {
            state.onLongPress()
            true
        }
    }
    .onGloballyPositioned { state.coordinates = it }
    .pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Taken: the wallpaper's own tap/long-press handling doesn't see this touch.
            down.consume()
            val drag = state.drag
            if (drag != null && awaitHold(down, onTap = { state.onTap() })) {
                state.haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.onHeld(true)
                followHeldFinger(
                    down = down,
                    coordinates = { state.coordinates },
                    drag = { state.drag ?: drag },
                    onDragStarted = { state.onHeld(false) },
                    onLift = { dragged ->
                        state.onHeld(false)
                        if (!dragged) state.onLongPress()
                    },
                )
            }
        }
    }

/**
 * Phase 1 of a tile gesture: true when the finger stays down without moving for the long-press
 * timeout. A quick lift calls [onTap]; moving first (a page swipe, the drawer) gives up.
 */
private suspend fun AwaitPointerEventScope.awaitHold(down: PointerInputChange, onTap: () -> Unit): Boolean {
    val slop = viewConfiguration.touchSlop
    var tapped = false
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || event.changes.size > 1) break
            if (change.changedToUp()) {
                tapped = true
                change.consume()
                break
            }
            if ((change.position - down.position).getDistance() > slop) break
        }
    }
    if (ended == null) return true
    if (tapped) onTap()
    return false
}

/**
 * Phase 2: the tile is held. Moving past the touch slop starts a drag that follows the finger
 * until it lifts ([onLift] with true); lifting without moving calls [onLift] with false.
 */
private suspend fun AwaitPointerEventScope.followHeldFinger(
    down: PointerInputChange,
    coordinates: () -> LayoutCoordinates?,
    drag: () -> TileDragCallbacks,
    onDragStarted: () -> Unit,
    onLift: (dragged: Boolean) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    var dragging = false
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                change.consume()
                break
            }
            val coords = coordinates()
            if (!dragging && coords != null && (change.position - down.position).getDistance() > slop) {
                dragging = true
                onDragStarted()
                drag().onDragStart(down.position, coords.size, coords.localToWindow(change.position))
            }
            if (dragging && coords != null && coords.isAttached) drag().onDrag(coords.localToWindow(change.position))
            change.consume()
        }
        onLift(dragging)
        if (dragging) drag().onDragEnd()
    } catch (e: CancellationException) {
        onDragStarted()
        if (dragging) drag().onDragCancel()
        throw e
    }
}
