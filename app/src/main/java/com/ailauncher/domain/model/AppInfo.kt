package com.ailauncher.domain.model

/**
 * Fixed taxonomy used for the AI "Smart Categories" drawer. A fixed list (rather than free-form
 * labels) keeps a 1.7B model consistent, lets the sampler be grammar-constrained, and gives users a
 * predictable set of overrides.
 */
enum class AppCategory(val displayName: String, val hint: String) {
    PRODUCTIVITY("Productivity", "office suites, notes, calendars, email clients, documents, to-do lists"),
    COMMUNICATION("Communication", "phone dialer, SMS, messengers, video calling"),
    SOCIAL("Social", "social networks, communities, dating"),
    FINANCE("Finance", "banking, payments, UPI, wallets, investing, crypto, budgeting"),
    SHOPPING("Shopping", "online stores, marketplaces, food and grocery delivery"),
    MEDIA("Media", "music, video streaming, podcasts, audiobooks, radio"),
    PHOTOGRAPHY("Photography", "camera, gallery, photo and video editors"),
    GAMES("Games", "every kind of game"),
    TRAVEL("Travel", "maps, navigation, ride hailing, public transit, flights, hotels"),
    HEALTH("Health", "fitness, running, workouts, meditation, medical, period tracking"),
    EDUCATION("Education", "learning, languages, courses, dictionaries, school apps"),
    NEWS("News", "news, magazines, e-book readers, weather"),
    UTILITIES("Utilities", "settings, browsers, file managers, calculator, clock, security and system tools"),
    ;

    companion object {
        /** Lenient lookup used for model output and backups ("Media", "MEDIA", " media "). */
        fun fromName(name: String?): AppCategory? {
            val value = name?.trim()?.removeSurrounding("\"") ?: return null
            return entries.firstOrNull {
                it.displayName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            }
        }
    }
}

/** Where an app's category came from. User overrides are never replaced by automatic passes. */
enum class CategorySource { USER, AI, SYSTEM, HEURISTIC }

/**
 * A launchable activity as shown in the drawer. [componentKey] ("package/activity") is the stable
 * identity: one package may expose several launcher activities.
 */
data class AppInfo(
    val componentKey: String,
    val packageName: String,
    val activityName: String,
    val systemLabel: String,
    val customLabel: String?,
    val category: AppCategory?,
    val categorySource: CategorySource?,
    val isHidden: Boolean,
    val dockPosition: Int?,
    val launchCount: Int,
    val lastLaunchedAt: Long,
    val isSystemApp: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val aliases: List<String> = emptyList(),
) {
    val label: String get() = customLabel?.takeIf { it.isNotBlank() } ?: systemLabel
    val isInDock: Boolean get() = dockPosition != null

    companion object {
        fun componentKey(packageName: String, activityName: String) = "$packageName/$activityName"
    }
}

/** Result of reconciling the Room cache with PackageManager. */
data class AppSyncResult(
    val added: List<String>,
    val removed: List<String>,
    val updated: List<String>,
) {
    val hasChanges: Boolean get() = added.isNotEmpty() || removed.isNotEmpty() || updated.isNotEmpty()
}

/** Progress of the AI categorization pass, surfaced as a banner in the drawer. */
data class CategorizationProgress(
    val running: Boolean,
    val categorized: Int,
    val total: Int,
    val message: String? = null,
)
