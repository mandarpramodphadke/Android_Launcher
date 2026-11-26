package com.ailauncher.presentation.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** CUSTOM_GESTURES (1.8): the two-finger swipe down, with real multi-touch input. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
class TwoFingerSwipeTest {

    @get:Rule val compose = createComposeRule()

    private var swipes = 0
    private val tracker = MultiTouchTracker()

    private fun show(enabled: Boolean = true) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("home").twoFingerSwipeDown({ enabled }, tracker) { swipes++ })
        }
    }

    private fun twoFingers(dy: Float) {
        compose.onNodeWithTag("home").performTouchInput {
            down(0, Offset(centerX - 100f, 400f))
            down(1, Offset(centerX + 100f, 400f))
            repeat(10) {
                moveBy(0, Offset(0f, dy / 10))
                moveBy(1, Offset(0f, dy / 10))
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
    }

    @Test
    fun twoFingersDownRunTheActionOnce() {
        show()
        twoFingers(dy = 500f)
        assertThat(swipes).isEqualTo(1)
        // Marked, so the one-finger swipe-down action skips this gesture.
        assertThat(tracker.active).isTrue()
    }

    @Test
    fun oneFingerOrAShortMoveDoNothing() {
        show()
        compose.onNodeWithTag("home").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertThat(swipes).isEqualTo(0)
        assertThat(tracker.active).isFalse()

        twoFingers(dy = 60f)
        assertThat(swipes).isEqualTo(0)
    }

    @Test
    fun offInThePlayVersion() {
        show(enabled = false)
        twoFingers(dy = 500f)
        assertThat(swipes).isEqualTo(0)
    }
}
