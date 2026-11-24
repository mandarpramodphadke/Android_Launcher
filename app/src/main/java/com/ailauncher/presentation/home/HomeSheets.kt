package com.ailauncher.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ailauncher.data.widgets.WidgetProviderEntry
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.presentation.components.AppListItem
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.presentation.components.FolderIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Long-press on the wallpaper. */
@Composable
fun HomeMenuSheet(
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onPages: () -> Unit,
    onNewFolder: () -> Unit,
    onPlanner: () -> Unit,
    onSettings: () -> Unit,
    onAssistant: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            MenuRow(Icons.Rounded.Widgets, "Widgets") { onDismiss(); onWidgets() }
            MenuRow(Icons.Rounded.ViewCarousel, "Home pages") { onDismiss(); onPages() }
            MenuRow(Icons.Rounded.CreateNewFolder, "New folder") { onDismiss(); onNewFolder() }
            MenuRow(Icons.Rounded.Checklist, "To-dos & planner") { onDismiss(); onPlanner() }
            MenuRow(Icons.Rounded.Wallpaper, "Wallpaper") { onDismiss(); onWallpaper() }
            MenuRow(Icons.Rounded.Settings, "Launcher settings") { onDismiss(); onSettings() }
            MenuRow(Icons.Rounded.AutoAwesome, "AI assistant") { onDismiss(); onAssistant() }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AppListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, onClick = onClick)
}

/** Every installed widget, grouped by app, with previews and search. */
@Composable
fun WidgetPickerSheet(
    /** null while the list is loading. */
    providers: List<WidgetProviderEntry>?,
    preview: (WidgetProviderEntry) -> Any?,
    onPick: (WidgetProviderEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val search = rememberTextFieldState()
    val filtered by remember(providers) {
        derivedStateOf {
            val all = providers.orEmpty()
            val q = search.text.toString()
            if (q.isBlank()) all else FuzzyMatcher.rank(q, all) { listOf(it.label, it.appLabel) }.map { it.first }
        }
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Text("Widgets", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            LauncherTextField(
                state = search,
                placeholder = "Search widgets",
                leadingContent = { Icon(Icons.Rounded.Search, null, Modifier.padding(start = 12.dp, end = 8.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.size(8.dp))
            when {
                providers == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 24.dp))
                providers.isEmpty() -> Text("No widgets installed.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                filtered.groupBy { it.appLabel }.forEach { (app, entries) ->
                    item(key = "header-$app") {
                        Text(
                            app,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                        )
                    }
                    items(entries, key = { it.provider.flattenToString() }) { entry ->
                        Surface(
                            onClick = {
                                onPick(entry)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Previews are decoded off the main thread (some are large bitmaps).
                                val model by produceState<Any?>(null, entry.provider) { value = withContext(Dispatchers.IO) { runCatching { preview(entry) }.getOrNull() } }
                                AsyncImage(model = model, contentDescription = null, modifier = Modifier.size(72.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val cells = if (entry.targetCellWidth > 0) "${entry.targetCellWidth} × ${entry.targetCellHeight}"
                                    else "${(entry.minWidthDp / 90f).toInt().coerceAtLeast(1)} × ${(entry.minHeightDp / 100f).toInt().coerceAtLeast(1)}"
                                    Text(cells, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Add, remove and reorder home pages (page 1, with the clock, always stays first). */
@Composable
fun PagesSheet(
    pageCount: Int,
    items: List<HomeItem>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Home pages", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            (0 until pageCount).forEach { page ->
                val count = items.count { it.position.page == page }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Page ${page + 1}" + if (page == 0) " · clock" else "", style = MaterialTheme.typography.bodyLarge)
                        Text("$count item${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (page > 1) IconButton(onClick = { onMove(page, page - 1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Move page left") }
                    if (page in 1 until pageCount - 1) IconButton(onClick = { onMove(page, page + 1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Move page right") }
                    if (page > 0) IconButton(onClick = { onRemove(page) }) { Icon(Icons.Rounded.Delete, "Remove page ${page + 1}") }
                }
                HorizontalDivider()
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.FilledTonalButton(onClick = onAdd, enabled = pageCount < 9) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add page")
                }
            }
        }
    }
}

/** Long-press on a folder (home page or dock). */
@Composable
fun FolderActionsSheet(
    folder: CustomFolder,
    apps: List<com.ailauncher.domain.model.AppInfo>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleDock: () -> Unit,
    /** null when the folder isn't on a home page. */
    onRemoveFromHome: (() -> Unit)?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FolderIcon(folder, apps, 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        folder.description ?: "${apps.size} app${if (apps.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuRow(Icons.Rounded.FolderOpen, "Open") { onDismiss(); onOpen() }
            MenuRow(Icons.Rounded.DriveFileRenameOutline, "Edit folder") { onDismiss(); onEdit() }
            MenuRow(Icons.Rounded.PushPin, if (folder.isInDock) "Remove from dock" else "Pin to dock") { onDismiss(); onToggleDock() }
            if (onRemoveFromHome != null) MenuRow(Icons.Rounded.RemoveCircleOutline, "Remove from home screen") { onDismiss(); onRemoveFromHome() }
            MenuRow(Icons.Rounded.Delete, "Delete folder") { onDismiss(); onDelete() }
        }
    }
}
