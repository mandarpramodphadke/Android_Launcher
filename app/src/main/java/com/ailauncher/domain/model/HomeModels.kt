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
) {
    val isInDock: Boolean get() = dockPosition != null
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
