package com.ailauncher.domain.model

/**
 * What fills a smart folder. Stored as a short key in `custom_folders.smartRule`
 * ("installed:14", "category:GAMES", …), see [encode] and [decode].
 */
sealed interface FolderRule {

    /** Apps in one of the drawer's categories (AI-sorted, or set by the user). */
    data class Category(val category: AppCategory) : FolderRule

    /** Apps installed in the last [days] days, newest first. */
    data class RecentlyInstalled(val days: Int = 14) : FolderRule

    /** Apps not opened for [days] days or more, longest unused first. */
    data class NotUsedFor(val days: Int = 30) : FolderRule

    /** The [count] apps opened most often in the last four weeks. */
    data class MostUsed(val count: Int = 12) : FolderRule

    /** The launcher's own features: assistant, planner, to-dos, Private Space and more. */
    data object LauncherTools : FolderRule

    /** Rules that read app usage (better with Usage access). */
    val usesAppUsage: Boolean get() = this is NotUsedFor || this is MostUsed

    fun encode(): String = when (this) {
        is Category -> "category:${category.name}"
        is RecentlyInstalled -> "installed:$days"
        is NotUsedFor -> "unused:$days"
        is MostUsed -> "most_used:$count"
        LauncherTools -> "tools"
    }

    /** A name for a new folder with this rule. */
    val defaultName: String
        get() = when (this) {
            is Category -> category.displayName
            is RecentlyInstalled -> "New apps"
            is NotUsedFor -> "Not used lately"
            is MostUsed -> "Most used"
            LauncherTools -> "AI Launcher"
        }

    /** One line saying what the folder holds, e.g. "Apps installed in the last 14 days". */
    val summary: String
        get() = when (this) {
            is Category -> "${category.displayName} apps"
            is RecentlyInstalled -> "Apps installed in the last ${days.days()}"
            is NotUsedFor -> "Apps not opened for ${days.days()} or more"
            is MostUsed -> "Your $count most opened apps of the last 4 weeks"
            LauncherTools -> "Assistant, planner, to-dos and more"
        }

    companion object {
        const val MAX_DAYS = 365
        const val MAX_COUNT = 30

        val INSTALLED_DAY_OPTIONS = listOf(7, 14, 30)
        val UNUSED_DAY_OPTIONS = listOf(30, 60, 90)
        val MOST_USED_OPTIONS = listOf(8, 12, 16)

        /** Lenient: unknown or malformed values give null (an ordinary folder). */
        fun decode(value: String?): FolderRule? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val name = text.substringBefore(':').lowercase()
            val arg = text.substringAfter(':', "")
            return when (name) {
                "category" -> AppCategory.fromName(arg)?.let(::Category)
                "installed" -> arg.toIntOrNull()?.takeIf { it > 0 }?.let { RecentlyInstalled(it.coerceAtMost(MAX_DAYS)) }
                "unused" -> arg.toIntOrNull()?.takeIf { it > 0 }?.let { NotUsedFor(it.coerceAtMost(MAX_DAYS)) }
                "most_used" -> arg.toIntOrNull()?.takeIf { it > 0 }?.let { MostUsed(it.coerceAtMost(MAX_COUNT)) }
                "tools" -> LauncherTools
                else -> null
            }
        }

        private fun Int.days(): String = if (this == 1) "day" else "$this days"
    }
}

/** A launcher feature that can sit in the "AI Launcher" tools folder. */
enum class LauncherTool(val label: String, val description: String) {
    ASSISTANT("Assistant", "Ask your on-device assistant"),
    MY_DAY("My day", "Summary of today: events, plan and to-dos"),
    PLANNER("Planner", "Plan your day in time blocks"),
    TODOS("To-dos", "Your to-do lists"),
    CATCH_UP("Catch up", "What did I miss? (notification digest)"),
    PRIVATE_SPACE("Private", "Private Space: your hidden apps"),
    WIDGETS("Widgets", "Add a widget to the home screen"),
    SETTINGS("Settings", "Launcher settings"),
    SYSTEM_INFO("System", "Battery, memory, storage, CPU and GPU details"),
}

/**
 * App usage at one moment, for smart folders. [lastUsed] and [launchCounts] are keyed by package
 * name; without Usage access they only cover apps opened from the launcher.
 */
data class UsageSnapshot(
    val hasUsageAccess: Boolean = false,
    val lastUsed: Map<String, Long> = emptyMap(),
    val launchCounts: Map<String, Int> = emptyMap(),
) {
    companion object {
        val EMPTY = UsageSnapshot()
    }
}
