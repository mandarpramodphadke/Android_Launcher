package com.ailauncher.domain.home

import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import kotlin.math.ceil

/** Grid geometry of the home screen pages (the clock page reserves its top rows). */
data class GridSpec(val columns: Int, val rows: Int, val reservedTopRowsOnFirstPage: Int) {
    fun reservedRows(page: Int) = if (page == 0) reservedTopRowsOnFirstPage.coerceAtMost(rows - 1) else 0
}

/** Pure placement logic for home screen items: occupancy, first free cell, resize validation. */
object HomeGridPlanner {

    fun fits(target: GridPosition, spec: GridSpec): Boolean =
        target.x >= 0 && target.y >= spec.reservedRows(target.page) && target.spanX >= 1 && target.spanY >= 1 &&
            target.x + target.spanX <= spec.columns && target.y + target.spanY <= spec.rows

    /** Whether [target] is inside the grid and overlaps no item other than [ignoreId]. */
    fun isFree(items: List<HomeItem>, target: GridPosition, spec: GridSpec, ignoreId: Long? = null): Boolean =
        fits(target, spec) && items.none { it.id != ignoreId && it.position.overlaps(target) }

    /** First free cell on [page] in reading order (row by row), or null when the page is full. */
    fun findFreeCell(items: List<HomeItem>, page: Int, spanX: Int, spanY: Int, spec: GridSpec): GridPosition? {
        val sx = spanX.coerceIn(1, spec.columns)
        val sy = spanY.coerceIn(1, spec.rows)
        for (y in spec.reservedRows(page)..spec.rows - sy) {
            for (x in 0..spec.columns - sx) {
                val candidate = GridPosition(page, x, y, sx, sy)
                if (isFree(items, candidate, spec)) return candidate
            }
        }
        return null
    }

    /** Free space starting at [preferredPage], then the following pages, then the earlier ones. */
    fun findFreeCellAnywhere(
        items: List<HomeItem>,
        preferredPage: Int,
        pageCount: Int,
        spanX: Int,
        spanY: Int,
        spec: GridSpec,
    ): GridPosition? {
        val order = (preferredPage until pageCount) + (0 until preferredPage)
        return order.firstNotNullOfOrNull { findFreeCell(items, it, spanX, spanY, spec) }
    }

    /**
     * Cells a widget needs for its minimum size (Launcher3's rule: round up, clamp to the grid).
     * Sizes are in dp; [cellWidthDp]/[cellHeightDp] are one cell including its padding.
     */
    fun widgetSpan(minWidthDp: Float, minHeightDp: Float, cellWidthDp: Float, cellHeightDp: Float, spec: GridSpec): Pair<Int, Int> {
        val x = ceil(minWidthDp / cellWidthDp.coerceAtLeast(1f)).toInt().coerceIn(1, spec.columns)
        val y = ceil(minHeightDp / cellHeightDp.coerceAtLeast(1f)).toInt().coerceIn(1, (spec.rows - spec.reservedTopRowsOnFirstPage).coerceAtLeast(1))
        return x to y
    }

    /** The largest valid resize of [item] towards [spanX]×[spanY] that stays free and in bounds. */
    fun clampResize(items: List<HomeItem>, item: HomeItem, spanX: Int, spanY: Int, spec: GridSpec): GridPosition {
        var sx = spanX.coerceIn(1, spec.columns - item.position.x)
        var sy = spanY.coerceIn(1, spec.rows - item.position.y)
        while (sx > 1 || sy > 1) {
            val candidate = item.position.copy(spanX = sx, spanY = sy)
            if (isFree(items, candidate, spec, ignoreId = item.id)) return candidate
            if (sx >= sy && sx > 1) sx-- else sy--
        }
        return item.position.copy(spanX = 1, spanY = 1)
    }
}
