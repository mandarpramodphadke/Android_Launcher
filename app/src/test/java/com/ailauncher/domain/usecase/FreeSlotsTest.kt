package com.ailauncher.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FreeSlotsTest {

    private fun t(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun emptyDayIsOneSlotFromEightToNine() {
        assertThat(FreeSlots.between(emptyList())).containsExactly(t(8) to t(21))
    }

    @Test
    fun gapsBetweenBlocksAndEventsInAnyOrder() {
        val busy = listOf(t(13) to t(14), t(9) to t(10, 30), t(10, 30) to t(11))
        assertThat(FreeSlots.between(busy)).containsExactly(
            t(8) to t(9),
            t(11) to t(13),
            t(14) to t(21),
        ).inOrder()
    }

    @Test
    fun overlappingAndOutOfDayIntervalsAreMerged() {
        val busy = listOf(t(6) to t(8, 45), t(12) to t(15), t(13) to t(14), t(20, 40) to t(23))
        assertThat(FreeSlots.between(busy)).containsExactly(t(8, 45) to t(12), t(15) to t(20, 40)).inOrder()
    }

    @Test
    fun gapsShorterThanHalfAnHourAreIgnored() {
        val busy = listOf(t(8) to t(9), t(9, 20) to t(20, 45))
        assertThat(FreeSlots.between(busy)).isEmpty()
    }

    @Test
    fun customBoundsMinimumAndLimit() {
        val busy = (0 until 10).map { i -> t(8 + i) to t(8 + i, 30) }
        val slots = FreeSlots.between(busy, dayStart = t(8), dayEnd = t(18), minFree = 15, max = 3)
        assertThat(slots).containsExactly(t(8, 30) to t(9), t(9, 30) to t(10), t(10, 30) to t(11)).inOrder()
        assertThat(FreeSlots.between(busy, dayStart = t(10), dayEnd = t(9))).isEmpty()
    }
}
