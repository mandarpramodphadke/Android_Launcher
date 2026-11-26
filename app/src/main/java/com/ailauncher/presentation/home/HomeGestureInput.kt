package com.ailauncher.presentation.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Whether the gesture in progress (or just finished) used two fingers, so the one-finger swipe-down
 * action is skipped for it. Reset when the next gesture starts.
 */
internal class MultiTouchTracker {
    var active = false
        internal set
}

/**
 * CUSTOM_GESTURES: two fingers swiped down anywhere on the home screen. It watches on the Initial
 * pass without consuming anything, so taps, the drawer drag and the pager keep working.
 */
internal fun Modifier.twoFingerSwipeDown(enabled: Boolean, tracker: MultiTouchTracker, onSwipe: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(tracker) {
            val threshold = 72.dp.toPx()
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                tracker.active = false
                val starts = HashMap<PointerId, Offset>()
                var fired = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        tracker.active = true
                        pressed.forEach { starts.getOrPut(it.id) { it.position } }
                        val moved = pressed.mapNotNull { change -> starts[change.id]?.let { change.position.y - it.y } }
                        if (!fired && moved.size >= 2 && moved.all { it > threshold }) {
                            fired = true
                            onSwipe()
                        }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
