package com.ailauncher.presentation.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** The screens to the right of the home pages: the assistant, then System info. */
enum class HomePanel { HOME, CHAT, SYSTEM }

/**
 * The assistant and System info as panels beside the home screen, like the Discover feed on a
 * Pixel: they follow the finger while it moves and settle with its speed, instead of opening like
 * a new screen.
 *
 * The offset runs from 0 (home) to −width (assistant) and −2 × width (System info).
 */
@Stable
class HomePanelState(private val systemPanel: Boolean) {

    val draggable = AnchoredDraggableState(HomePanel.HOME)

    var width = 0f
        private set

    /** Lays out the anchors for a screen [newWidth] pixels wide (keeps the current panel). */
    fun updateWidth(newWidth: Float) {
        if (newWidth <= 0f || newWidth == width) return
        width = newWidth
        draggable.updateAnchors(
            DraggableAnchors {
                HomePanel.HOME at 0f
                HomePanel.CHAT at -newWidth
                if (systemPanel) HomePanel.SYSTEM at -2 * newWidth
            },
            draggable.targetValue,
        )
    }

    /** Current offset, or the settled panel's position before the first layout. */
    val offset: Float
        get() = draggable.offset.takeUnless { it.isNaN() } ?: when (draggable.settledValue) {
            HomePanel.HOME -> 0f
            HomePanel.CHAT -> -width
            HomePanel.SYSTEM -> -2 * width
        }

    /** 0 on the home screen, 1 with the assistant fully in view. */
    val chatProgress: Float by derivedStateOf { if (width <= 0f) 0f else (-offset / width).coerceIn(0f, 1f) }

    /** 0 until System info starts to slide in, 1 when it fills the screen. */
    val systemProgress: Float by derivedStateOf { if (width <= 0f) 0f else ((-offset - width) / width).coerceIn(0f, 1f) }

    /** Anything other than the plain home screen shows (or is being dragged in). */
    val showingPanel: Boolean by derivedStateOf { offset < -0.5f || draggable.settledValue != HomePanel.HOME }

    val chatVisible: Boolean by derivedStateOf { offset < -0.5f && offset > -2 * width + 0.5f || draggable.settledValue == HomePanel.CHAT }
    val systemVisible: Boolean by derivedStateOf { systemPanel && (offset < -width - 0.5f || draggable.settledValue == HomePanel.SYSTEM) }

    val current: HomePanel get() = draggable.settledValue

    /** Moves the panels with the finger; returns the part of [delta] that was used. */
    fun dispatch(delta: Float): Float = draggable.dispatchRawDelta(delta)

    /** Takes over from a running settle animation when a new drag starts. */
    suspend fun grab() {
        draggable.anchoredDrag(MutatePriority.UserInput) { }
    }

    /**
     * Settles after a drag: a quick flick goes one panel in its direction, otherwise the nearest
     * panel wins. A flick never skips a panel (home to System info).
     */
    suspend fun settle(velocity: Float) {
        if (width <= 0f) return
        val from = draggable.settledValue
        val position = -offset / width // 0 home, 1 chat, 2 system
        val last = if (systemPanel) 2 else 1
        val nearest = position.coerceIn(0f, last.toFloat())
        val target = when {
            velocity <= -FLING_VELOCITY -> kotlin.math.ceil(nearest - 0.05f).toInt()
            velocity >= FLING_VELOCITY -> kotlin.math.floor(nearest + 0.05f).toInt()
            else -> kotlin.math.round(nearest).toInt()
        }.coerceIn((from.ordinal - 1).coerceAtLeast(0), (from.ordinal + 1).coerceAtMost(last))
        animateTo(HomePanel.entries[target], velocity)
    }

    /** Slides to [panel], carrying [velocity] (pixels per second) into the spring. */
    suspend fun animateTo(panel: HomePanel, velocity: Float = 0f) {
        val target = if (panel == HomePanel.SYSTEM && !systemPanel) HomePanel.CHAT else panel
        draggable.anchoredDrag(target) { anchors, latest ->
            val to = anchors.positionOf(latest)
            if (!to.isNaN()) animate(offset, to, velocity, PanelSpring) { value, speed -> dragTo(value, speed) }
        }
    }

    suspend fun snapTo(panel: HomePanel) {
        draggable.anchoredDrag(panel) { anchors, latest ->
            val to = anchors.positionOf(latest)
            if (!to.isNaN()) dragTo(to)
        }
    }

    /**
     * For the home pager: at the last page, the finger's leftover leftward movement pulls the
     * assistant in; while a panel is partly in, moving right pushes it back before the pager moves.
     */
    val pagerConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.x <= 0f || offset >= 0f) return Offset.Zero
            return Offset(dispatch(available.x), 0f)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.x >= 0f || abs(available.x) < abs(available.y)) return Offset.Zero
            return Offset(dispatch(available.x), 0f)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset >= -0.5f && draggable.settledValue == HomePanel.HOME) return Velocity.Zero
            settle(available.x)
            return available
        }
    }

    private companion object {
        /** Pixels per second that count as a flick. */
        const val FLING_VELOCITY = 900f

        /** Quick and without overshoot, so the edge of the next panel never peeks in. */
        val PanelSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
    }
}
