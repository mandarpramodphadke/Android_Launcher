package com.ailauncher.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.AppIcon
import com.ailauncher.presentation.components.LaunchRequest
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.presentation.components.colors
import com.ailauncher.presentation.components.rememberLaunchRequestFactory

/** An open folder: its name, its description (purpose) and its apps, with folder actions. */
@Composable
fun FolderSheet(
    folder: CustomFolder,
    apps: List<AppInfo>,
    columns: Int,
    onLaunch: (LaunchRequest) -> Unit,
    onEditDetails: () -> Unit,
    onEditApps: () -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
    onAddToHome: () -> Unit,
    onToggleDock: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val launchFactory = rememberLaunchRequestFactory()
    val (container, content) = folder.accent.colors()
    var confirmDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AppInfo?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, null, tint = content)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    folder.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            if (apps.isEmpty()) {
                Text(
                    "No apps yet. Add some to this folder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                apps.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            AppGridTile(
                                app = app,
                                onClick = { bounds ->
                                    onLaunch(launchFactory(app, bounds))
                                    onDismiss()
                                },
                                onLongClick = { removing = app },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = onEditApps, label = { Text("Add / remove apps") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }) }
                item { AssistChip(onClick = onEditDetails, label = { Text("Edit details") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }) }
                item { AssistChip(onClick = onAddToHome, label = { Text("Add to home") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, null) }) }
                item {
                    AssistChip(
                        onClick = onToggleDock,
                        label = { Text(if (folder.isInDock) "Remove from dock" else "Pin to dock") },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    )
                }
                item { AssistChip(onClick = { confirmDelete = true }, label = { Text("Delete folder") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${folder.name}”?") },
            text = { Text("The folder is removed from the drawer, dock and home screen. Its apps stay installed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    onDismiss()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    removing?.let { app ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(app.label) },
            text = { Text("Remove from ${folder.name}? The app stays installed.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveApp(app)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Create or edit a folder: single-line Name (required) and Description fields, an accent colour,
 * and a searchable multi-select app checklist.
 */
@Composable
fun FolderEditorSheet(
    existing: CustomFolder?,
    allApps: List<AppInfo>,
    initialSelection: List<String> = existing?.packageNames.orEmpty(),
    onSave: (name: String, description: String?, accent: FolderAccent, packageNames: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(existing?.name.orEmpty())
    val description = rememberTextFieldState(existing?.description.orEmpty())
    val search = rememberTextFieldState()
    var accent by remember { mutableStateOf(existing?.accent ?: FolderAccent.INDIGO) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initialSelection) } }
    val filtered by remember(allApps) {
        derivedStateOf {
            val q = search.text.toString()
            if (q.isBlank()) allApps else FuzzyMatcher.rank(q, allApps) { listOf(it.label) }.map { it.first }
        }
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(if (existing == null) "New folder" else "Edit folder", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            LauncherTextField(
                state = name,
                placeholder = "Folder name (required)",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                isError = false,
            )
            Spacer(Modifier.size(8.dp))
            LauncherTextField(
                state = description,
                placeholder = "Description, e.g. apps for my job",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FolderAccent.entries.forEach { option ->
                    val (container, content) = option.colors()
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(container)
                            .border(if (option == accent) 2.dp else 1.dp, if (option == accent) content else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(role = Role.RadioButton) { accent = option }
                            .semantics { contentDescription = "${option.name.lowercase()} colour" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == accent) Icon(Icons.Rounded.Check, null, tint = content, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            LauncherTextField(
                state = search,
                placeholder = "Search apps to add",
                leadingContent = { Icon(Icons.Rounded.Search, null, Modifier.padding(start = 12.dp, end = 8.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Text(
                "${selected.size} selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Checkbox) { if (checked) selected.remove(app.packageName) else selected.add(app.packageName) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        AppIcon(app, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = name.text.isNotBlank(),
                    onClick = {
                        // Keep the folder's existing order; newly ticked apps go to the end.
                        val ordered = initialSelection.filter { it in selected } + selected.filter { it !in initialSelection }
                        onSave(name.text.toString(), description.text.toString().ifBlank { null }, accent, ordered)
                        onDismiss()
                    },
                ) { Text(if (existing == null) "Create" else "Save", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
