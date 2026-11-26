package com.ailauncher.domain.model

/** The home-screen gestures a user can choose an action for (CUSTOM_GESTURES). Swipe up is the drawer. */
enum class HomeGesture { SWIPE_DOWN, DOUBLE_TAP, TWO_FINGER_SWIPE_DOWN }

/** What a home-screen gesture does. */
enum class GestureAction {
    NOTHING,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK,
    ASSISTANT,
    SEARCH,
    APP_DRAWER,
    OPEN_APP,
    FLASHLIGHT,
    SYSTEM_INFO,
    PLANNER,
}

/**
 * The action set for [gesture]. Until the user picks one, swipe down and double-tap follow the
 * older switches ("Swipe down for notifications", "Double-tap to lock"), so an update changes nothing.
 */
fun LauncherSettings.actionFor(gesture: HomeGesture): GestureAction = when (gesture) {
    HomeGesture.SWIPE_DOWN -> swipeDownAction ?: if (swipeDownForNotifications) GestureAction.NOTIFICATIONS else GestureAction.NOTHING
    HomeGesture.DOUBLE_TAP -> doubleTapAction ?: if (doubleTapToLock) GestureAction.LOCK else GestureAction.NOTHING
    HomeGesture.TWO_FINGER_SWIPE_DOWN -> twoFingerSwipeAction
}

/** The app (component key) [gesture] opens when its action is [GestureAction.OPEN_APP]. */
fun LauncherSettings.appFor(gesture: HomeGesture): String? = when (gesture) {
    HomeGesture.SWIPE_DOWN -> swipeDownApp
    HomeGesture.DOUBLE_TAP -> doubleTapApp
    HomeGesture.TWO_FINGER_SWIPE_DOWN -> twoFingerApp
}

/** [gesture] set to [action] (and, for [GestureAction.OPEN_APP], to open [app]). */
fun LauncherSettings.withGesture(gesture: HomeGesture, action: GestureAction, app: String? = null): LauncherSettings {
    val target = if (action == GestureAction.OPEN_APP) app ?: appFor(gesture) else null
    return when (gesture) {
        HomeGesture.SWIPE_DOWN -> copy(swipeDownAction = action, swipeDownApp = target)
        HomeGesture.DOUBLE_TAP -> copy(doubleTapAction = action, doubleTapApp = target)
        HomeGesture.TWO_FINGER_SWIPE_DOWN -> copy(twoFingerSwipeAction = action, twoFingerApp = target)
    }
}
