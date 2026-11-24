package com.ailauncher.domain.suggestions

import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AudioRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AppSuggestionRankerTest {

    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 24, 8, 30, 0, 0, zone) // Tuesday morning

    private fun app(pkg: String, category: AppCategory? = null, launches: Int = 0, last: Long = 0, hidden: Boolean = false) = AppInfo(
        componentKey = "$pkg/.Main", packageName = pkg, activityName = ".Main", systemLabel = pkg.substringAfterLast('.'),
        customLabel = null, category = category, categorySource = null, isHidden = hidden, dockPosition = null,
        launchCount = launches, lastLaunchedAt = last, isSystemApp = false, installedAt = 0, updatedAt = 0,
    )

    private fun at(daysAgo: Long, hour: Int) = now.minusDays(daysAgo).withHour(hour).withMinute(0).toInstant().toEpochMilli()

    @Test
    fun timeOfDayHabitsWin() {
        val apps = listOf(app("com.news"), app("com.game"), app("com.mail"))
        val launches = (1L..10L).flatMap { d -> listOf(LaunchSignal("com.news", at(d, 8)), LaunchSignal("com.game", at(d, 22))) } +
            LaunchSignal("com.mail", at(1, 13))
        val ranked = AppSuggestionRanker.rank(apps, launches, SuggestionContext(now))
        assertThat(ranked.first().packageName).isEqualTo("com.news")
        // In the evening the game takes over.
        val evening = AppSuggestionRanker.rank(apps, launches, SuggestionContext(now.withHour(22)))
        assertThat(evening.first().packageName).isEqualTo("com.game")
    }

    @Test
    fun headphonesBoostMediaAndHiddenAppsNeverShow() {
        val apps = listOf(app("com.music", AppCategory.MEDIA), app("com.notes", AppCategory.PRODUCTIVITY), app("com.secret", hidden = true))
        val launches = listOf(
            LaunchSignal("com.notes", at(1, 8)), LaunchSignal("com.notes", at(2, 8)),
            LaunchSignal("com.music", at(1, 8)), LaunchSignal("com.secret", at(1, 8)), LaunchSignal("com.secret", at(2, 8)),
        )
        assertThat(AppSuggestionRanker.rank(apps, launches, SuggestionContext(now)).first().packageName).isEqualTo("com.notes")
        val withHeadphones = AppSuggestionRanker.rank(apps, launches, SuggestionContext(now, AudioRoute.HEADPHONES))
        assertThat(withHeadphones.first().packageName).isEqualTo("com.music")
        assertThat(withHeadphones.map { it.packageName }).doesNotContain("com.secret")
    }

    @Test
    fun coldStartFallsBackToLaunchCounters() {
        val apps = listOf(app("com.a", launches = 3, last = at(1, 9)), app("com.b", launches = 40, last = at(1, 9)), app("com.c"))
        val ranked = AppSuggestionRanker.rank(apps, emptyList(), SuggestionContext(now))
        assertThat(ranked.map { it.packageName }).containsExactly("com.b", "com.a").inOrder()
    }
}
