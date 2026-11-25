package com.ailauncher.presentation.components

import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.data.packages.AppLauncher
import com.ailauncher.domain.home.GridSpec
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppShortcut
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.HomeLayoutRepository
import com.ailauncher.domain.repository.ShortcutRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** A tap on an icon: the app plus the icon's on-screen bounds and the launch animation. */
data class LaunchRequest(val app: AppInfo, val sourceBounds: Rect?, val options: Bundle?)

/**
 * Behaviour shared by the home screen and the app drawer: launching (with the icon's bounds for
 * the clip-reveal animation) and every action of the long-press menu.
 */
abstract class LauncherAppsViewModel(
    protected val appRepository: AppRepository,
    private val appLauncher: AppLauncher,
    private val dockRepository: DockRepository,
    private val homeLayout: HomeLayoutRepository,
    private val folderRepository: FolderRepository,
    private val shortcutRepository: ShortcutRepository,
) : ViewModel(), AppActionHandler {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    protected fun message(text: String) {
        _messages.trySend(text)
    }

    /** Grid of the home pages as last measured by the home screen (used when adding items). */
    protected open val gridSpec: GridSpec get() = HomeGridMetrics.spec
    protected open val currentHomePage: Int get() = HomeGridMetrics.currentPage

    open fun launch(request: LaunchRequest) {
        viewModelScope.launch {
            if (!appLauncher.launch(request.app, request.sourceBounds, request.options)) {
                message("${request.app.label} could not be opened")
            }
        }
    }

    // --- AppActionHandler ------------------------------------------------------------------
    override fun openAppInfo(app: AppInfo) = appRepository.openAppInfo(app.packageName)
    override fun uninstall(app: AppInfo) = appRepository.requestUninstall(app.packageName)

    override fun togglePin(app: AppInfo) {
        viewModelScope.launch {
            when {
                app.isInDock -> {
                    dockRepository.unpinApp(app.componentKey)
                    message("Removed ${app.label} from the dock")
                }
                dockRepository.pinApp(app.componentKey) -> message("Pinned ${app.label} to the dock")
                else -> message("The dock is full (${DockRepository.MAX_SIZE} slots)")
            }
        }
    }

    override fun moveInDock(app: AppInfo, offset: Int) {
        viewModelScope.launch { appRepository.moveInDock(app.componentKey, offset) }
    }

    override fun hide(app: AppInfo) {
        viewModelScope.launch {
            appRepository.setHidden(app.componentKey, true)
            homeLayout.removeApp(app.componentKey)
            message("${app.label} moved to Private Space")
        }
    }

    override fun rename(app: AppInfo, label: String?) {
        viewModelScope.launch { appRepository.setCustomLabel(app.componentKey, label) }
    }

    override fun setCategory(app: AppInfo, category: AppCategory) {
        viewModelScope.launch { appRepository.setCategory(app.packageName, category, CategorySource.USER) }
    }

    override suspend fun shortcuts(app: AppInfo): List<AppShortcut> = shortcutRepository.shortcuts(app.packageName)

    override fun launchShortcut(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle?) {
        if (!appLauncher.startShortcut(shortcut, sourceBounds, options)) message("That shortcut is no longer available")
    }

    override fun addToHome(app: AppInfo) {
        viewModelScope.launch {
            val position = homeLayout.addApp(app.componentKey, currentHomePage, gridSpec)
            message(if (position != null) "Added ${app.label} to page ${position.page + 1}" else "The home screen is full")
        }
    }

    override fun addToFolder(app: AppInfo, folder: CustomFolder) {
        viewModelScope.launch {
            if (folder.isSmart) {
                message("${folder.name} fills itself. Edit its rule to change what's in it")
            } else if (app.packageName in folder.packageNames) {
                message("${app.label} is already in ${folder.name}")
            } else {
                folderRepository.setApps(folder.id, folder.packageNames + app.packageName)
                message("Added ${app.label} to ${folder.name}")
            }
        }
    }
}

/** Last measured home grid and visible page, shared with the drawer's "Add to home". */
object HomeGridMetrics {
    @Volatile var spec: GridSpec = GridSpec(columns = 4, rows = 5, reservedTopRowsOnFirstPage = 2)
    @Volatile var currentPage: Int = 0
    @Volatile var cellWidthDp: Float = 90f
    @Volatile var cellHeightDp: Float = 100f
}
