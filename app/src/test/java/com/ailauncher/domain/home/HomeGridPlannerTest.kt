package com.ailauncher.domain.home

import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeGridPlannerTest {

    private val spec = GridSpec(columns = 4, rows = 6, reservedTopRowsOnFirstPage = 2)
    private fun app(id: Long, page: Int, x: Int, y: Int, sx: Int = 1, sy: Int = 1) =
        HomeItem(id, GridPosition(page, x, y, sx, sy), HomeItemContent.App("pkg$id/.A"))

    @Test
    fun firstPageSkipsTheClockRows() {
        assertThat(HomeGridPlanner.findFreeCell(emptyList(), 0, 1, 1, spec)).isEqualTo(GridPosition(0, 0, 2))
        assertThat(HomeGridPlanner.findFreeCell(emptyList(), 1, 1, 1, spec)).isEqualTo(GridPosition(1, 0, 0))
    }

    @Test
    fun findsTheNextFreeCellAndRespectsSpans() {
        val items = listOf(app(1, 1, 0, 0), app(2, 1, 1, 0, sx = 2, sy = 2))
        assertThat(HomeGridPlanner.findFreeCell(items, 1, 1, 1, spec)).isEqualTo(GridPosition(1, 3, 0))
        // A 2x2 widget does not fit beside them in rows 0-1, so it goes to row 2.
        assertThat(HomeGridPlanner.findFreeCell(items, 1, 2, 2, spec)).isEqualTo(GridPosition(1, 0, 2, 2, 2))
        assertThat(HomeGridPlanner.isFree(items, GridPosition(1, 2, 1), spec)).isFalse()
        assertThat(HomeGridPlanner.isFree(items, GridPosition(1, 3, 5, 2, 1), spec)).isFalse() // out of bounds
    }

    @Test
    fun fullPagesSpillToTheNextPage() {
        val full = (0 until 4).flatMap { x -> (2 until 6).map { y -> app((x * 10 + y).toLong(), 0, x, y) } }
        assertThat(HomeGridPlanner.findFreeCell(full, 0, 1, 1, spec)).isNull()
        assertThat(HomeGridPlanner.findFreeCellAnywhere(full, 0, 2, 1, 1, spec)).isEqualTo(GridPosition(1, 0, 0))
    }

    @Test
    fun resizeIsClampedToFreeSpace() {
        val widget = app(1, 1, 0, 0, sx = 2, sy = 2)
        val blocker = app(2, 1, 3, 0)
        val resized = HomeGridPlanner.clampResize(listOf(widget, blocker), widget, 4, 2, spec)
        assertThat(resized).isEqualTo(GridPosition(1, 0, 0, 3, 2))
    }

    @Test
    fun widgetSpanRoundsUp() {
        assertThat(HomeGridPlanner.widgetSpan(180f, 110f, 90f, 100f, spec)).isEqualTo(2 to 2)
        assertThat(HomeGridPlanner.widgetSpan(1000f, 40f, 90f, 100f, spec)).isEqualTo(4 to 1)
    }
}
