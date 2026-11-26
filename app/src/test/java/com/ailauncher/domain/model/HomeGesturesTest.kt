package com.ailauncher.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** CUSTOM_GESTURES (1.8): which action a gesture runs. */
class HomeGesturesTest {

    @Test
    fun untilChosenTheOlderSwitchesStillDecide() {
        val defaults = LauncherSettings()
        assertThat(defaults.actionFor(HomeGesture.SWIPE_DOWN)).isEqualTo(GestureAction.NOTIFICATIONS)
        assertThat(defaults.actionFor(HomeGesture.DOUBLE_TAP)).isEqualTo(GestureAction.LOCK)
        assertThat(defaults.actionFor(HomeGesture.TWO_FINGER_SWIPE_DOWN)).isEqualTo(GestureAction.QUICK_SETTINGS)
        val switchedOff = LauncherSettings(swipeDownForNotifications = false, doubleTapToLock = false)
        assertThat(switchedOff.actionFor(HomeGesture.SWIPE_DOWN)).isEqualTo(GestureAction.NOTHING)
        assertThat(switchedOff.actionFor(HomeGesture.DOUBLE_TAP)).isEqualTo(GestureAction.NOTHING)
    }

    @Test
    fun aChosenActionWinsAndOnlyOpenAppKeepsAnApp() {
        var settings = LauncherSettings(doubleTapToLock = true)
            .withGesture(HomeGesture.DOUBLE_TAP, GestureAction.OPEN_APP, "com.spotify.music/.MainActivity")
        assertThat(settings.actionFor(HomeGesture.DOUBLE_TAP)).isEqualTo(GestureAction.OPEN_APP)
        assertThat(settings.appFor(HomeGesture.DOUBLE_TAP)).isEqualTo("com.spotify.music/.MainActivity")
        // Another gesture is untouched.
        assertThat(settings.actionFor(HomeGesture.SWIPE_DOWN)).isEqualTo(GestureAction.NOTIFICATIONS)

        settings = settings.withGesture(HomeGesture.DOUBLE_TAP, GestureAction.ASSISTANT)
        assertThat(settings.actionFor(HomeGesture.DOUBLE_TAP)).isEqualTo(GestureAction.ASSISTANT)
        assertThat(settings.appFor(HomeGesture.DOUBLE_TAP)).isNull()

        settings = settings.withGesture(HomeGesture.TWO_FINGER_SWIPE_DOWN, GestureAction.NOTHING)
        assertThat(settings.actionFor(HomeGesture.TWO_FINGER_SWIPE_DOWN)).isEqualTo(GestureAction.NOTHING)
    }
}
