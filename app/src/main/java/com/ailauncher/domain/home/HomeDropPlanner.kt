package com.ailauncher.domain.home

import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent

/** What happens when a dragged home item is let go over a cell. */
sealed interface DropAction {
    /** Back where it was (same cell, outside the grid, or onto something it can't join). */
    data object None : DropAction

    data class Move(val target: GridPosition) : DropAction

    /** An app dropped onto another app: both go into a new folder in [target]'s cell. */
    data class CreateFolder(val target: HomeItem) : DropAction

    /** An app dropped onto a folder. */
    data class AddToFolder(val folderItem: HomeItem, val folderId: Long) : DropAction

    /** An app dropped onto a smart folder, which fills itself. */
    data class SmartFolder(val folderId: Long) : DropAction
}

/** Pure drop logic for dragging apps and folders around the home pages. */
object HomeDropPlanner {

    /**
     * [page]/[x]/[y] is the cell under the finger. Apps and folders take one cell; widgets are
     * not dragged (they have their own move and resize controls).
     */
    fun resolve(
        items: List<HomeItem>,
        dragged: HomeItem,
        page: Int,
        x: Int,
        y: Int,
        spec: GridSpec,
        isSmartFolder: (Long) -> Boolean = { false },
    ): DropAction {
        val cell = GridPosition(page, x, y)
        if (!HomeGridPlanner.fits(cell, spec)) return DropAction.None
        val from = dragged.position
        if (from.page == page && from.x == x && from.y == y) return DropAction.None
        val occupant = items.firstOrNull { it.id != dragged.id && it.position.overlaps(cell) }
        val draggedApp = dragged.content as? HomeItemContent.App
        return when (val content = occupant?.content) {
            null -> {
                val target = cell.copy(spanX = from.spanX, spanY = from.spanY)
                if (HomeGridPlanner.isFree(items, target, spec, ignoreId = dragged.id)) DropAction.Move(target) else DropAction.None
            }
            is HomeItemContent.App -> when {
                draggedApp == null -> DropAction.None
                packageOf(draggedApp.componentKey) == packageOf(content.componentKey) -> DropAction.None
                else -> DropAction.CreateFolder(occupant)
            }
            is HomeItemContent.Folder -> when {
                draggedApp == null -> DropAction.None
                isSmartFolder(content.folderId) -> DropAction.SmartFolder(content.folderId)
                else -> DropAction.AddToFolder(occupant, content.folderId)
            }
            is HomeItemContent.Widget, is HomeItemContent.LauncherWidget -> DropAction.None
        }
    }

    fun packageOf(componentKey: String): String = componentKey.substringBefore('/')
}
