package com.ailauncher.presentation.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.AppListRow
import com.ailauncher.presentation.components.FolderIcon
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.presentation.theme.LocalLauncherStyle
import kotlinx.coroutines.launch

/** What the drawer can ask for; wired up by the home screen, which hosts the drawer as an overlay. */
class DrawerCallbacks(
    val onLaunch: (AppInfo, Rect) -> Unit = { _, _ -> },
    val onLongPress: (AppInfo) -> Unit = {},
    val onToggleLayout: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSelectTab: (DrawerTab) -> Unit = {},
    val onOrganize: () -> Unit = {},
    val onOpenFolder: (CustomFolder) -> Unit = {},
    val onCreateFolder: () -> Unit = {},
    val onOpenPrivateSpace: () -> Unit = {},
    val onOpenPlanner: () -> Unit = {},
)

/** Searching for the launcher's own planner ("todo", "planner", "tasks") offers it first. */
private val PLANNER_QUERY = Regex("""^(to-?do|todo|plan|task|agenda|sched)""", RegexOption.IGNORE_CASE)

/**
 * The app drawer's visible content: drag handle, search, tabs (A–Z, AI Smart Categories, Custom
 * Folders), the predicted-apps strip and the app lists. Stateless (screenshot-testable); the home
 * screen drives its open/close drag and hosts its sheets.
 */
@Composable
internal fun DrawerContent(
    state: DrawerUiState,
    queryState: TextFieldState,
    minimal: Boolean,
    callbacks: DrawerCallbacks,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
) {
    // Minimal mode is text-only, which only the list layout can show.
    val layout = if (minimal) DrawerLayout.LIST else state.layout
    val effective = state.copy(layout = layout)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        // Explicit: Material can't infer a content colour for a translucent surface (it fell back to black).
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // Grab here (or pull down at the top of the list) to close.
            Box(handleModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(50)))
            }
            DrawerTopBar(
                queryState = queryState,
                layout = layout,
                onToggleLayout = callbacks.onToggleLayout.takeUnless { minimal },
                onOpenPrivateSpace = callbacks.onOpenPrivateSpace,
                onOpenSettings = callbacks.onOpenSettings,
            )
            val queryText = queryState.text.toString()
            val searching = queryText.isNotBlank()
            if (!searching) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val tabs = listOf(
                        Triple(DrawerTab.ALPHABETICAL, "A–Z", Icons.Rounded.SortByAlpha),
                        Triple(DrawerTab.CATEGORIES, "AI Smart", Icons.Rounded.AutoAwesome),
                        Triple(DrawerTab.FOLDERS, "Folders", Icons.Rounded.Folder),
                    )
                    tabs.forEachIndexed { index, (tab, label, icon) ->
                        SegmentedButton(
                            selected = state.tab == tab,
                            onClick = { callbacks.onSelectTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                            icon = { Icon(icon, null, Modifier.size(18.dp)) },
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            Box(Modifier.weight(1f).then(listModifier)) {
                when {
                    state.loading -> Unit
                    // Results trail the field by a frame; don't flash "no match" while they catch up.
                    searching -> SearchResults(effective, queryText, current = state.query == queryText, minimal, callbacks)
                    state.tab == DrawerTab.ALPHABETICAL -> AlphabeticalContent(effective, minimal, callbacks)
                    state.tab == DrawerTab.FOLDERS -> FoldersContent(effective, callbacks)
                    else -> CategoryContent(
                        state = effective,
                        minimal = minimal,
                        onLaunch = callbacks.onLaunch,
                        onLongPress = callbacks.onLongPress,
                        onOrganize = callbacks.onOrganize,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerTopBar(
    queryState: TextFieldState,
    layout: DrawerLayout,
    onToggleLayout: (() -> Unit)?,
    onOpenPrivateSpace: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LauncherTextField(
            state = queryState,
            placeholder = "Search apps",
            leadingContent = { Icon(Icons.Rounded.Search, null, Modifier.padding(start = 14.dp, end = 8.dp)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
        )
        if (onToggleLayout != null) {
            IconButton(onClick = onToggleLayout) {
                Icon(
                    if (layout == DrawerLayout.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (layout == DrawerLayout.GRID) "Show as list" else "Show as grid",
                )
            }
        }
        IconButton(onClick = onOpenPrivateSpace) { Icon(Icons.Rounded.Lock, "Private Space") }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Launcher settings") }
    }
}

/** Five predicted apps (usage habits, time of day, connected audio). */
@Composable
private fun SuggestedRow(state: DrawerUiState, callbacks: DrawerCallbacks) {
    if (state.suggestions.isEmpty()) return
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            "Suggested",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            state.suggestions.forEach { app ->
                AppGridTile(
                    app = app,
                    onClick = { bounds -> callbacks.onLaunch(app, bounds) },
                    onLongClick = { callbacks.onLongPress(app) },
                    iconSize = 48.dp,
                    badgeCount = state.badges[app.packageName] ?: 0,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(5 - state.suggestions.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SearchResults(state: DrawerUiState, query: String, current: Boolean, minimal: Boolean, callbacks: DrawerCallbacks) {
    val planner = PLANNER_QUERY.containsMatchIn(query.trim())
    if (state.searchResults.isEmpty()) {
        if (!current) return
        if (planner) {
            PlannerShortcut(callbacks.onOpenPlanner, Modifier.padding(12.dp))
            return
        }
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("No apps match \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (minimal) {
        LazyColumn(contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            if (planner) item(key = "planner") { PlannerShortcut(callbacks.onOpenPlanner) }
            items(state.searchResults, key = { it.componentKey }) { app ->
                AppListRow(app = app, onClick = { bounds -> callbacks.onLaunch(app, bounds) }, onLongClick = { callbacks.onLongPress(app) }, showIcon = false)
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.columns),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        if (planner) item(key = "planner", span = { GridItemSpan(maxLineSpan) }) { PlannerShortcut(callbacks.onOpenPlanner, Modifier.padding(bottom = 8.dp)) }
        items(state.searchResults, key = { it.componentKey }) { app ->
            AppGridTile(
                app = app,
                onClick = { bounds -> callbacks.onLaunch(app, bounds) },
                onLongClick = { callbacks.onLongPress(app) },
                badgeCount = state.badges[app.packageName] ?: 0,
            )
        }
    }
}

@Composable
private fun AlphabeticalContent(state: DrawerUiState, minimal: Boolean, callbacks: DrawerCallbacks) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Index of each section header in the lazy list (one suggestions item, then headers + apps).
    val headerIndex = remember(state.alphabetical) {
        var index = 1
        state.alphabetical.associate { section ->
            val at = index
            index += 1 + section.apps.size
            section.key to at
        }
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (state.layout == DrawerLayout.GRID) {
                AlphabeticalGrid(state, gridState, callbacks)
            } else {
                AlphabeticalList(state, minimal, listState, callbacks)
            }
        }
        FastScroller(
            letters = state.alphabetical.map { it.key },
            onLetter = { letter ->
                val index = headerIndex[letter] ?: return@FastScroller
                scope.launch {
                    if (state.layout == DrawerLayout.GRID) gridState.scrollToItem(index) else listState.scrollToItem(index)
                }
            },
            modifier = Modifier.fillMaxHeight().navigationBarsPadding(),
        )
    }
}

@Composable
private fun AlphabeticalGrid(state: DrawerUiState, gridState: LazyGridState, callbacks: DrawerCallbacks) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(state.columns),
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "suggested", span = { GridItemSpan(maxLineSpan) }) { SuggestedRow(state, callbacks) }
        state.alphabetical.forEach { section ->
            item(key = "header-${section.key}", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(section.key) }
            items(section.apps, key = { it.componentKey }) { app ->
                AppGridTile(
                    app = app,
                    onClick = { bounds -> callbacks.onLaunch(app, bounds) },
                    onLongClick = { callbacks.onLongPress(app) },
                    badgeCount = state.badges[app.packageName] ?: 0,
                )
            }
        }
    }
}

@Composable
private fun AlphabeticalList(state: DrawerUiState, minimal: Boolean, listState: LazyListState, callbacks: DrawerCallbacks) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "suggested") { if (!minimal) SuggestedRow(state, callbacks) }
        state.alphabetical.forEach { section ->
            item(key = "header-${section.key}") { SectionHeader(section.key) }
            items(section.apps, key = { it.componentKey }) { app ->
                AppListRow(
                    app = app,
                    onClick = { bounds -> callbacks.onLaunch(app, bounds) },
                    onLongClick = { callbacks.onLongPress(app) },
                    supportingText = app.category?.displayName.takeUnless { minimal },
                    showIcon = !minimal,
                    badgeCount = state.badges[app.packageName] ?: 0,
                )
            }
        }
    }
}

/** Custom folders: name, description and a preview of their apps. */
@Composable
private fun FoldersContent(state: DrawerUiState, callbacks: DrawerCallbacks) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "planner") { PlannerShortcut(callbacks.onOpenPlanner) }
        item(key = "new") {
            FilledTonalButton(onClick = callbacks.onCreateFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New folder")
            }
        }
        if (state.folders.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Group apps your way, e.g. \"Work: apps for my job\". Then say \"open my Work folder\" to the assistant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.folders, key = { "folder-${it.id}" }) { folder ->
            val apps = state.appsOf(folder)
            Card(
                onClick = { callbacks.onOpenFolder(folder) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FolderIcon(folder, apps, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            folder.description ?: folder.rule?.summary ?: "${apps.size} app${if (apps.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Draggable A–Z rail; jumps the list to the touched letter and shows a preview bubble. */
@Composable
private fun FastScroller(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    if (letters.size < 2) return
    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<String?>(null) }
    val currentOnLetter by rememberUpdatedState(onLetter)

    fun select(y: Float) {
        val index = ((y / height.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != active) {
            active = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetter(letter)
        }
    }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 8.dp)
                .onSizeChanged { height = it.height }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { select(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ ->
                        change.consume()
                        select(change.position.y)
                    }
                }
                .pointerInput(letters) {
                    detectTapGestures(onPress = {
                        select(it.y)
                        tryAwaitRelease()
                        active = null
                    })
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    letter,
                    fontSize = 11.sp,
                    fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = active != null, modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier
                    .padding(end = 48.dp)
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(active.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun CategoryContent(
    state: DrawerUiState,
    minimal: Boolean,
    onLaunch: (AppInfo, Rect) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOrganize: () -> Unit,
) {
    val style = LocalLauncherStyle.current
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(if (minimal) 0.dp else 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "banner") { CategorizationBanner(state, onOrganize) }
        state.categories.forEach { section ->
            val title = section.category?.displayName ?: "Not sorted yet"
            // Colorful and Neon colour-code categories; other themes use the regular text colour.
            val accent = style.categoryColor(section.category)
            item(key = "cat-$title") {
                if (minimal) {
                    Column {
                        CategoryHeader(title, section.apps.size, accent ?: MaterialTheme.colorScheme.primary, Modifier.padding(top = 12.dp))
                        section.apps.forEach { app ->
                            AppListRow(app = app, onClick = { bounds -> onLaunch(app, bounds) }, onLongClick = { onLongPress(app) }, showIcon = false)
                        }
                    }
                    return@item
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        CategoryHeader(title, section.apps.size, accent)
                        if (state.layout == DrawerLayout.LIST) {
                            section.apps.forEach { app ->
                                AppListRow(app = app, onClick = { bounds -> onLaunch(app, bounds) }, onLongClick = { onLongPress(app) }, badgeCount = state.badges[app.packageName] ?: 0)
                            }
                        } else {
                            section.apps.chunked(state.columns).forEach { row ->
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    row.forEach { app ->
                                        AppGridTile(
                                            app = app,
                                            onClick = { bounds -> onLaunch(app, bounds) },
                                            onLongClick = { onLongPress(app) },
                                            badgeCount = state.badges[app.packageName] ?: 0,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(state.columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int, accent: Color?, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (accent != null) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategorizationBanner(state: DrawerUiState, onOrganize: () -> Unit) {
    val progress = state.progress
    when {
        progress.running -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (progress.total > 0) "AI is organizing your apps… ${progress.categorized}/${progress.total}"
                        else "AI is organizing your apps…",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.categorized.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        state.notSortedByAi > 0 -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.notSortedByAi} apps use quick-guess categories",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        if (state.modelReady) "Let the on-device AI sort them - nothing leaves your phone."
                        else "Download the on-device model for smarter sorting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (state.modelReady) {
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onOrganize) { Text("Organize") }
                }
            }
        }
        else -> Unit
    }
}

/** The launcher's own To-dos & Daily Planner screen. */
@Composable
private fun PlannerShortcut(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Checklist, null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("To-dos & planner", style = MaterialTheme.typography.titleMedium)
                Text("Your lists and today's time blocks", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
