package com.ailauncher.domain.model

/** A rectangle of cells on one home screen page. Page 0 is the page with the clock. */
data class GridPosition(
    val page: Int,
    val x: Int,
    val y: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
) {
    fun overlaps(other: GridPosition): Boolean =
        page == other.page &&
            x < other.x + other.spanX && other.x < x + spanX &&
            y < other.y + other.spanY && other.y < y + spanY
}

sealed interface HomeItemContent {
    /** A launcher activity, by [AppInfo.componentKey]. */
    data class App(val componentKey: String) : HomeItemContent

    data class Folder(val folderId: Long) : HomeItemContent

    /** A bound AppWidget; [provider] is the flattened provider ComponentName (kept for restore). */
    data class Widget(val appWidgetId: Int, val provider: String) : HomeItemContent

    /** A widget the launcher draws itself (to-do list, Today, add a to-do). */
    data class LauncherWidget(val config: LauncherWidgetConfig) : HomeItemContent
}

/** The launcher's own widgets, with their default and minimum sizes in cells. */
enum class LauncherWidgetKind(
    val key: String,
    val label: String,
    val description: String,
    val defaultSpanX: Int,
    val defaultSpanY: Int,
    val minSpanX: Int,
    val minSpanY: Int,
) {
    TODO_LIST("todo_list", "To-do list", "Your open to-dos. Tick them off or add new ones.", 4, 2, 2, 2),
    TODAY("today", "Today", "What's on your plan now and next, and what's due today.", 4, 2, 2, 1),
    QUICK_ADD("todo_add", "Add a to-do", "A bar that adds a to-do with one tap.", 4, 1, 2, 1),
    ;

    companion object {
        fun fromKey(key: String?): LauncherWidgetKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Settings of one launcher widget. [listName] limits a to-do widget to one list (null = all
 * lists). Stored as "kind" or "kind:list name" in `home_items.launcherWidget`.
 */
data class LauncherWidgetConfig(val kind: LauncherWidgetKind, val listName: String? = null) {
    fun encode(): String = listName?.takeIf { it.isNotBlank() }?.let { "${kind.key}:$it" } ?: kind.key

    companion object {
        fun decode(value: String?): LauncherWidgetConfig? {
            if (value.isNullOrBlank()) return null
            val kind = LauncherWidgetKind.fromKey(value.substringBefore(':')) ?: return null
            return LauncherWidgetConfig(kind, value.substringAfter(':', "").takeIf { it.isNotBlank() })
        }
    }
}

/** Something placed on a home screen page. */
data class HomeItem(
    val id: Long,
    val position: GridPosition,
    val content: HomeItemContent,
)

/** Accent colours a custom folder can use; the key is what Room stores. */
enum class FolderAccent {
    INDIGO, TEAL, AMBER, ROSE, GREEN, SKY, VIOLET, SLATE;

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String?): FolderAccent = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: INDIGO
    }
}

/**
 * A user-made folder: a name, an optional purpose ("apps for my job"), and an ordered list of
 * packages. It can be opened from the drawer, pinned on a home page or placed in the dock.
 *
 * A smart folder has a [rule] and fills itself: its [packageNames] are worked out from the rule
 * (new apps, a category, apps not used lately…) and can't be edited by hand.
 */
data class CustomFolder(
    val id: Long,
    val name: String,
    val description: String?,
    val accent: FolderAccent,
    val sortOrder: Int,
    val packageNames: List<String>,
    /** Slot in the 5-slot dock shared with pinned apps, or null. */
    val dockPosition: Int?,
    val rule: FolderRule? = null,
) {
    val isInDock: Boolean get() = dockPosition != null
    val isSmart: Boolean get() = rule != null

    /** The launcher features a "launcher tools" folder holds (empty for every other folder). */
    val tools: List<LauncherTool> get() = if (rule == FolderRule.LauncherTools) LauncherTool.entries else emptyList()
}

/** A slot of the persistent dock: an app or a folder. */
sealed interface DockEntry {
    val position: Int

    data class App(val app: AppInfo) : DockEntry {
        override val position: Int get() = app.dockPosition ?: 0
    }

    data class Folder(val folder: CustomFolder) : DockEntry {
        override val position: Int get() = folder.dockPosition ?: 0
    }
}

/** An `android.content.pm.ShortcutInfo`, reduced to what the long-press menu shows. */
data class AppShortcut(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String?,
)

/** The phone's battery, for the line under the home screen clock. */
data class BatteryGlance(val percent: Int, val charging: Boolean)
