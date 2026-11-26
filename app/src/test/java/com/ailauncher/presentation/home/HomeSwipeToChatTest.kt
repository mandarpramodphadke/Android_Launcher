package com.ailauncher.presentation.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.presentation.theme.AiLauncherTheme
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The real home content, wrapped as LauncherHomeScreen wraps it (taps, the drawer's vertical drag,
 * the two-finger detector): swiping left on the last page must pull the assistant panel in.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
class HomeSwipeToChatTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var panels: HomePanelState

    private fun show(twoFinger: Boolean, pages: Int = 2) {
        compose.setContent {
            AiLauncherTheme(LauncherSettings()) {
                panels = remember { HomePanelState(systemPanel = true) }
                val tracker = remember { MultiTouchTracker() }
                val panelDrag = rememberDraggableState { panels.dispatch(it) }
                Box(Modifier.fillMaxSize().onSizeChanged { panels.updateWidth(it.width.toFloat()) }) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .twoFingerSwipeDown(enabled = { twoFinger && !panels.showingPanel }, tracker = tracker) {}
                            .pointerInput(Unit) { detectTapGestures(onTap = {}, onDoubleTap = {}, onLongPress = {}) }
                            .draggable(rememberDraggableState {}, Orientation.Vertical, enabled = !panels.showingPanel)
                            .testTag("home"),
                    ) {
                        HomeContent(
                            state = HomeUiState(pageCount = pages, settings = LauncherSettings()),
                            tint = WallpaperTint.Light,
                            style = LocalLauncherStyle.current,
                            pagerState = rememberPagerState(initialPage = pages - 1) { pages },
                            queryState = rememberTextFieldState(),
                            clip = null,
                            widgetHost = null,
                            editingWidgetId = null,
                            callbacks = HomeCallbacks(),
                            pagerNestedScroll = panels.pagerConnection,
                        )
                    }
                    if (panels.showingPanel) {
                        val gesture = Modifier.draggable(panelDrag, Orientation.Horizontal, onDragStarted = { panels.grab() }, onDragStopped = { panels.settle(it) })
                        Box(Modifier.fillMaxSize().graphicsLayer { translationX = panels.width + panels.offset }.background(Color.White).then(gesture)) { Text("Chat") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun swipeLeftOnHome() {
        compose.onNodeWithTag("home").performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f) }
        compose.waitForIdle()
    }

    @Test
    fun swipingPastTheLastPageOpensTheAssistant() {
        show(twoFinger = false)
        swipeLeftOnHome()
        assertThat(panels.current).isEqualTo(HomePanel.CHAT)
    }

    @Test
    fun withTheTwoFingerGestureToo() {
        show(twoFinger = true)
        swipeLeftOnHome()
        assertThat(panels.current).isEqualTo(HomePanel.CHAT)
    }

    @Test
    fun withASingleHomePage() {
        show(twoFinger = true, pages = 1)
        swipeLeftOnHome()
        assertThat(panels.current).isEqualTo(HomePanel.CHAT)
    }
}
