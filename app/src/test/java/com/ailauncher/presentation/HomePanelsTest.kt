package com.ailauncher.presentation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ailauncher.presentation.home.HomePanel
import com.ailauncher.presentation.home.HomePanelState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The assistant and System info panels with real touch input: dragging past the last home page
 * pulls the assistant in and it follows the finger; a short drag snaps back; panels drag on to
 * System info and back home, one panel at a time.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
class HomePanelsTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var panels: HomePanelState

    private fun show() {
        compose.setContent {
            panels = remember { HomePanelState(systemPanel = true) }
            val drag = rememberDraggableState { panels.dispatch(it) }
            Box(Modifier.fillMaxSize().onSizeChanged { panels.updateWidth(it.width.toFloat()) }) {
                // Home: two pages, the pager hands its leftover drag to the panels.
                Box(Modifier.fillMaxSize().nestedScroll(panels.pagerConnection)) {
                    HorizontalPager(rememberPagerState(initialPage = 1) { 2 }, Modifier.fillMaxSize().testTag("pager")) { page -> Text("Page $page") }
                }
                if (panels.showingPanel) {
                    val gesture = Modifier.draggable(drag, Orientation.Horizontal, onDragStarted = { panels.grab() }, onDragStopped = { panels.settle(it) })
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = panels.width + panels.offset }.background(Color.White).then(gesture).testTag("chat")) { Text("Chat") }
                    if (panels.systemVisible) {
                        Box(Modifier.fillMaxSize().graphicsLayer { translationX = 2 * panels.width + panels.offset }.background(Color.White).then(gesture).testTag("system")) { Text("System") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theAssistantFollowsTheFingerAndSettles() {
        show()
        val width = panels.width
        // Hold mid-drag: the panel is part-way in, exactly where the finger is.
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * 0.9f, height / 2f))
            repeat(10) { moveBy(Offset(-width * 0.03f, 0f)) }
        }
        compose.waitForIdle()
        assertThat(panels.offset).isLessThan(-width * 0.15f)
        assertThat(panels.offset).isGreaterThan(-width * 0.45f)
        assertThat(panels.chatProgress).isGreaterThan(0f)
        // A slow, short pull snaps back home.
        compose.onNodeWithTag("pager").performTouchInput {
            advanceEventTime(600)
            up()
        }
        compose.waitForIdle()
        assertThat(panels.current).isEqualTo(HomePanel.HOME)
        assertThat(panels.offset).isEqualTo(0f)

        // A full swipe opens the assistant.
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(panels.current).isEqualTo(HomePanel.CHAT)
        assertThat(panels.offset).isWithin(1f).of(-width)
    }

    @Test
    fun panelsGoOneAtATimeAndBack() {
        show()
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("chat").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(panels.current).isEqualTo(HomePanel.SYSTEM)

        compose.onNodeWithTag("system").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(panels.current).isEqualTo(HomePanel.CHAT)
        compose.onNodeWithTag("chat").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(panels.current).isEqualTo(HomePanel.HOME)
        assertThat(panels.showingPanel).isFalse()
    }
}
