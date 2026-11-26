package com.ailauncher.presentation.home

import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.LauncherSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** DOCK_AND_GRID (1.8): how many rows a home page gets. */
class HomeRowsTest {

    private fun rows(height: Int, chosen: Int, used: Int = 0) =
        HomeCellMetrics.fit(400.dp, height.dp, columns = 4, minCellHeight = 92.dp, rows = chosen, usedRows = used).rows

    @Test
    fun automaticFitsWholeCells() {
        assertThat(rows(560, LauncherSettings.AUTO_ROWS)).isEqualTo(6)
        assertThat(rows(300, LauncherSettings.AUTO_ROWS)).isEqualTo(4)
        assertThat(rows(900, LauncherSettings.AUTO_ROWS)).isEqualTo(7)
    }

    @Test
    fun aChosenCountIsUsedUnlessItWouldHideAppsOrSquashCells() {
        assertThat(rows(560, 5)).isEqualTo(5)
        assertThat(rows(560, 7)).isEqualTo(7)
        // Cells never get shorter than an icon and its name.
        assertThat(rows(400, 7)).isEqualTo(5)
        // Apps already on row 6: five rows would hide them, so six are kept.
        assertThat(rows(560, 5, used = 6)).isEqualTo(6)
        val metrics = HomeCellMetrics.fit(400.dp, 560.dp, 4, 92.dp, rows = 5, usedRows = 0)
        assertThat(metrics.cellHeight).isEqualTo(112.dp)
    }
}
