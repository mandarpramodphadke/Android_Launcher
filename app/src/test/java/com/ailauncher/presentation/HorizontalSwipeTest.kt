package com.ailauncher.presentation

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ailauncher.presentation.components.SwipeDirection
import com.ailauncher.presentation.components.horizontalSwipe
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The swipe that leads from the assistant to System info (and back). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
class HorizontalSwipeTest {

    @get:Rule val compose = createComposeRule()

    private var swipes = 0

    private fun show() {
        compose.setContent {
            Column(Modifier.fillMaxSize().horizontalSwipe(SwipeDirection.LEFT) { swipes++ }) {
                Box(Modifier.fillMaxWidth().height(300.dp).testTag("page")) { Text("Chat") }
                // A row of chips that scrolls sideways keeps its own swipes.
                Row(Modifier.fillMaxWidth().height(80.dp).testTag("chips").horizontalScroll(rememberScrollState())) {
                    repeat(10) { Box(Modifier.width(150.dp).height(80.dp)) { Text("Chip $it") } }
                }
            }
        }
    }

    @Test
    fun swipeLeftOpensTheNextPageOnlyWhenNothingElseUsedIt() {
        show()
        compose.onNodeWithTag("page").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(swipes).isEqualTo(1)

        compose.onNodeWithTag("page").performTouchInput { swipeRight() }
        compose.onNodeWithTag("page").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertThat(swipes).isEqualTo(1)

        compose.onNodeWithTag("chips").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(swipes).isEqualTo(1)
    }
}
