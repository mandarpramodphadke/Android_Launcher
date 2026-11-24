package com.ailauncher.presentation.navigation

import com.ailauncher.domain.action.LauncherDestination
import com.ailauncher.domain.action.LauncherNavigator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Event bus behind [LauncherNavigator]; collected by [LauncherNavHost]. */
@Singleton
class LauncherNavigatorImpl @Inject constructor() : LauncherNavigator {
    private val events = MutableSharedFlow<LauncherDestination>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val destinations: SharedFlow<LauncherDestination> = events.asSharedFlow()

    override fun navigate(destination: LauncherDestination) {
        events.tryEmit(destination)
    }
}
