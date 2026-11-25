package com.ailauncher.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Which way the finger moves. */
enum class SwipeDirection(val sign: Int) { LEFT(-1), RIGHT(1) }

/**
 * Calls [onSwipe] after a mostly horizontal swipe of at least 96 dp in [direction]. It only watches
 * (the final pass), so lists still scroll, and it ignores swipes that something on the screen used,
 * such as a text field or a horizontal row of chips. Pass a stable [onSwipe] (see
 * [rememberSwipeAction]) so recomposition doesn't restart the gesture.
 */
fun Modifier.horizontalSwipe(direction: SwipeDirection, onSwipe: () -> Unit): Modifier = pointerInput(direction, onSwipe) {
    val threshold = 96.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var dx = 0f
        var dy = 0f
        var used = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (change.isConsumed) used = true
            dx = change.position.x - down.position.x
            dy = change.position.y - down.position.y
        }
        if (!used && dx * direction.sign > threshold && abs(dx) > abs(dy) * 1.5f) onSwipe()
    }
}

/** A lambda that stays the same object across recompositions but always calls the latest [action]. */
@Composable
fun rememberSwipeAction(action: () -> Unit): () -> Unit {
    val current by rememberUpdatedState(action)
    return remember { { current() } }
}
