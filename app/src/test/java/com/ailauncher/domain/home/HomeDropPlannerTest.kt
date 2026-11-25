package com.ailauncher.domain.home

import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.LauncherWidgetConfig
import com.ailauncher.domain.model.LauncherWidgetKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeDropPlannerTest {

    private val spec = GridSpec(columns = 4, rows = 6, reservedTopRowsOnFirstPage = 2)

    private fun app(id: Long, pkg: String, page: Int, x: Int, y: Int) = HomeItem(id, GridPosition(page, x, y), HomeItemContent.App("$pkg/.Main"))
    private fun folder(id: Long, folderId: Long, page: Int, x: Int, y: Int) = HomeItem(id, GridPosition(page, x, y), HomeItemContent.Folder(folderId))

    private val chat = app(1, "chat", 1, 0, 0)
    private val maps = app(2, "maps", 1, 1, 0)
    private val work = folder(3, folderId = 30, page = 1, x = 2, y = 0)
    private val smart = folder(4, folderId = 40, page = 1, x = 3, y = 0)
    private val widget = HomeItem(5, GridPosition(1, 0, 2, 2, 2), HomeItemContent.LauncherWidget(LauncherWidgetConfig(LauncherWidgetKind.TODO_LIST)))
    private val items = listOf(chat, maps, work, smart, widget)

    private fun drop(dragged: HomeItem, page: Int, x: Int, y: Int) =
        HomeDropPlanner.resolve(items, dragged, page, x, y, spec) { it == 40L }

    @Test
    fun emptyCellMovesTheItem() {
        assertThat(drop(chat, 1, 3, 5)).isEqualTo(DropAction.Move(GridPosition(1, 3, 5)))
        assertThat(drop(work, 2, 0, 0)).isEqualTo(DropAction.Move(GridPosition(2, 0, 0)))
        assertThat(drop(chat, 0, 1, 2)).isEqualTo(DropAction.Move(GridPosition(0, 1, 2)))
    }

    @Test
    fun appOntoAppMakesAFolderAndOntoAFolderJoinsIt() {
        assertThat(drop(chat, 1, 1, 0)).isEqualTo(DropAction.CreateFolder(maps))
        assertThat(drop(maps, 1, 2, 0)).isEqualTo(DropAction.AddToFolder(work, 30))
        assertThat(drop(maps, 1, 3, 0)).isEqualTo(DropAction.SmartFolder(40))
    }

    @Test
    fun nothingHappensWhereTheItemCannotGo() {
        assertThat(drop(chat, 1, 0, 0)).isEqualTo(DropAction.None) // where it was
        assertThat(drop(chat, 1, 1, 3)).isEqualTo(DropAction.None) // onto the widget
        assertThat(drop(work, 1, 0, 0)).isEqualTo(DropAction.None) // a folder onto an app
        assertThat(drop(work, 1, 3, 0)).isEqualTo(DropAction.None) // a folder onto a folder
        assertThat(drop(chat, 0, 0, 1)).isEqualTo(DropAction.None) // the clock rows of page 1
        assertThat(drop(chat, 1, 4, 0)).isEqualTo(DropAction.None) // outside the grid
        val twin = app(9, "chat", 2, 0, 0) // a second activity of the same app
        assertThat(HomeDropPlanner.resolve(items + twin, twin, 1, 0, 0, spec)).isEqualTo(DropAction.None)
    }
}
