package com.ailauncher.domain.usecase

/**
 * Free time of a day between busy intervals (minutes since midnight). Shared by the
 * get_day_plan tool and the Daily Planner screen.
 */
object FreeSlots {
    const val DAY_START = 8 * 60
    const val DAY_END = 21 * 60
    const val MIN_FREE = 30

    /** Gaps of at least [minFree] minutes inside [dayStart]..[dayEnd], earliest first (at most [max]). */
    fun between(
        busy: List<Pair<Int, Int>>,
        dayStart: Int = DAY_START,
        dayEnd: Int = DAY_END,
        minFree: Int = MIN_FREE,
        max: Int = 6,
    ): List<Pair<Int, Int>> {
        if (dayEnd <= dayStart) return emptyList()
        val slots = mutableListOf<Pair<Int, Int>>()
        var cursor = dayStart
        busy.map { (s, e) -> s.coerceIn(dayStart, dayEnd) to e.coerceIn(dayStart, dayEnd) }
            .filter { (s, e) -> e > s }
            .sortedBy { it.first }
            .forEach { (s, e) ->
                if (s - cursor >= minFree) slots += cursor to s
                cursor = maxOf(cursor, e)
            }
        if (dayEnd - cursor >= minFree) slots += cursor to dayEnd
        return slots.take(max)
    }
}
