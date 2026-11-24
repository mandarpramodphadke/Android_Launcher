package com.ailauncher.domain.action

import kotlinx.coroutines.flow.Flow

/** In-launcher destinations a tool (or a quick command) can open. */
sealed interface LauncherDestination {
    data class Folder(val folderId: Long) : LauncherDestination
    data object Planner : LauncherDestination
    data object Todos : LauncherDestination
    data object Home : LauncherDestination
}

/**
 * Lets tools navigate the launcher's own UI ("open my Work folder") without the domain knowing
 * about screens. Implemented as an event bus observed by the navigation host.
 */
interface LauncherNavigator {
    val destinations: Flow<LauncherDestination>
    fun navigate(destination: LauncherDestination)
}
