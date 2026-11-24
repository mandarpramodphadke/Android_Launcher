package com.ailauncher.domain.suggestions

import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AudioRoute
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/** One app launch (from UsageStatsManager or the launcher's own log). */
data class LaunchSignal(val packageName: String, val timestampMillis: Long)

data class SuggestionContext(val now: ZonedDateTime, val audioRoute: AudioRoute = AudioRoute.SPEAKER)

/**
 * Predicts the apps the user is most likely to open next, entirely on-device:
 *
 * score(app) = Σ launches · recency(half-life 7 d) · time-of-day similarity (σ ≈ 1.5 h)
 *                         · same-kind-of-day bonus (weekday vs weekend)
 *              × context boost (media on headphones, navigation + media in the car)
 *
 * Without launch history it falls back to the launcher's launch counters, so the strip is never
 * empty on day one.
 */
object AppSuggestionRanker {

    fun rank(
        candidates: List<AppInfo>,
        launches: List<LaunchSignal>,
        context: SuggestionContext,
        limit: Int = 5,
    ): List<AppInfo> {
        val visible = candidates.filter { !it.isHidden }.distinctBy { it.packageName }
        if (visible.isEmpty()) return emptyList()
        val nowMillis = context.now.toInstant().toEpochMilli()
        val nowHour = context.now.hour + context.now.minute / 60.0
        val weekend = context.now.dayOfWeek.isWeekend()

        val history = HashMap<String, Double>()
        for (launch in launches) {
            val ageDays = (nowMillis - launch.timestampMillis) / DAY_MILLIS
            if (ageDays < 0 || ageDays > MAX_AGE_DAYS) continue
            val at = Instant.ofEpochMilli(launch.timestampMillis).atZone(context.now.zone)
            val hour = at.hour + at.minute / 60.0
            val recency = exp(-ln(2.0) * ageDays / HALF_LIFE_DAYS)
            val hourDiff = min(abs(hour - nowHour), 24 - abs(hour - nowHour))
            val timeOfDay = 0.25 + exp(-(hourDiff * hourDiff) / (2 * SIGMA_HOURS * SIGMA_HOURS))
            val dayKind = if (at.dayOfWeek.isWeekend() == weekend) 1.0 else 0.6
            history.merge(launch.packageName, recency * timeOfDay * dayKind, Double::plus)
        }

        return visible
            .map { app ->
                val learned = history[app.packageName] ?: 0.0
                // Cold start: launch counter with a recency decay, scaled well below real history.
                val fallback = if (app.launchCount > 0) {
                    val age = ((nowMillis - app.lastLaunchedAt) / DAY_MILLIS).coerceAtLeast(0.0)
                    0.05 * ln(1.0 + app.launchCount) * exp(-ln(2.0) * age / HALF_LIFE_DAYS)
                } else {
                    0.0
                }
                app to (learned + fallback) * contextBoost(app.category, context.audioRoute)
            }
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<AppInfo, Double>> { it.second }.thenBy { it.first.label.lowercase() })
            .take(limit)
            .map { it.first }
    }

    private fun contextBoost(category: AppCategory?, route: AudioRoute): Double = when (route) {
        AudioRoute.HEADPHONES, AudioRoute.BLUETOOTH_AUDIO -> if (category == AppCategory.MEDIA) 2.0 else 1.0
        AudioRoute.CAR -> when (category) {
            AppCategory.TRAVEL -> 2.5
            AppCategory.MEDIA -> 1.8
            AppCategory.COMMUNICATION -> 1.2
            else -> 1.0
        }
        AudioRoute.SPEAKER -> 1.0
    }

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

    private const val DAY_MILLIS = 86_400_000.0
    private const val HALF_LIFE_DAYS = 7.0
    private const val MAX_AGE_DAYS = 28.0
    private const val SIGMA_HOURS = 1.5
}
