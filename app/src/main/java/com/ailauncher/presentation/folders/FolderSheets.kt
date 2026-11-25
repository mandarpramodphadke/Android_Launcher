package com.ailauncher.presentation.folders

import com.ailauncher.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.LauncherTool
import com.ailauncher.domain.search.FuzzyMatcher
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.AppIcon
import com.ailauncher.presentation.components.LaunchRequest
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.presentation.components.LauncherToolIcon
import com.ailauncher.presentation.components.TileLabel
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
    /** A launcher feature in the "AI Launcher" tools folder. */
    onOpenTool: (LauncherTool) -> Unit = {},
    /** Long-press on an app in a smart folder (its apps can't be removed by hand). */
    onSmartAppLongPress: (AppInfo) -> Unit = {},
    /** Smart folder: keep its apps and stop it filling itself. */
    onMakeEditable: () -> Unit = {},
    /** The folder's rule reads app usage and Usage access is off. */
    usageAccessMissing: Boolean = false,
    onAllowUsageAccess: () -> Unit = {},
) {
    val launchFactory = rememberLaunchRequestFactory()
    val (container, content) = folder.accent.colors()
    val rule = folder.rule
    var confirmDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AppInfo?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
                    Icon(if (rule != null) Icons.Rounded.AutoAwesome else Icons.Rounded.Folder, null, tint = content)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    (folder.description ?: rule?.summary)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    }
                    if (rule != null && folder.description != null) {
                        Text(
                            "Fills itself: ${rule.summary.replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            if (usageAccessMissing) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Based on apps opened from AI Launcher. Allow usage access to include apps opened elsewhere. It stays on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onAllowUsageAccess) { Text("Allow") }
                    }
                }
            }
            // System info is a full-version page until it reaches Google Play.
            val tools = folder.tools.filter { it != LauncherTool.SYSTEM_INFO || BuildConfig.SYSTEM_INFO }
            when {
                tools.isNotEmpty() -> tools.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { tool ->
                            ToolTile(
                                tool = tool,
                                onClick = {
                                    onDismiss()
                                    onOpenTool(tool)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                apps.isEmpty() -> Text(
                    rule?.emptyText() ?: "No apps yet. Add some to this folder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> apps.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            AppGridTile(
                                app = app,
                                onClick = { bounds ->
                                    onLaunch(launchFactory(app, bounds))
                                    onDismiss()
                                },
                                onLongClick = { if (rule != null) onSmartAppLongPress(app) else removing = app },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rule == null) {
                    item { AssistChip(onClick = onEditApps, label = { Text("Add / remove apps") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }) }
                    item { AssistChip(onClick = onEditDetails, label = { Text("Edit details") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }) }
                } else {
                    item { AssistChip(onClick = onEditDetails, label = { Text("Edit folder") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }) }
                    if (rule != FolderRule.LauncherTools) {
                        item { AssistChip(onClick = onMakeEditable, label = { Text("Pick apps myself") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }) }
                    }
                }
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

/** A launcher feature as a tile (icon and label), in the tools folder. */
@Composable
private fun ToolTile(tool: LauncherTool, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = tool.description, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LauncherToolIcon(tool, 52.dp)
        Spacer(Modifier.size(4.dp))
        TileLabel(tool.label, MaterialTheme.colorScheme.onSurface, null)
    }
}

private fun FolderRule.emptyText(): String = when (this) {
    is FolderRule.Category -> "No ${category.displayName} apps yet. Long-press an app in the drawer and choose its category to sort it here."
    is FolderRule.RecentlyInstalled -> "No apps installed in the last $days days."
    is FolderRule.NotUsedFor -> "Every app has been opened in the last $days days."
    is FolderRule.MostUsed -> "Open a few apps and the ones you use most show up here."
    FolderRule.LauncherTools -> ""
}

/**
 * Create or edit a folder: single-line Name (required) and Description fields, an accent colour,
 * and either a searchable multi-select app checklist or, for a smart folder, the rule that fills it.
 */
@Composable
fun FolderEditorSheet(
    existing: CustomFolder?,
    allApps: List<AppInfo>,
    initialSelection: List<String> = existing?.packageNames.orEmpty(),
    onSave: (name: String, description: String?, accent: FolderAccent, packageNames: List<String>, rule: FolderRule?) -> Unit,
    onDismiss: () -> Unit,
    /** Offer "Fills itself" (smart folders). */
    smartFolders: Boolean = false,
    /** Apps (or tools) a smart folder with this rule would hold now. */
    previewCount: (FolderRule) -> Int = { 0 },
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FolderEditorContent(existing, allApps, initialSelection, onSave, onDismiss, smartFolders, previewCount)
    }
}

/** The folder editor's content (the sheet's body; the screenshot test renders it directly). */
@Composable
internal fun FolderEditorContent(
    existing: CustomFolder?,
    allApps: List<AppInfo>,
    initialSelection: List<String>,
    onSave: (name: String, description: String?, accent: FolderAccent, packageNames: List<String>, rule: FolderRule?) -> Unit,
    onDismiss: () -> Unit,
    smartFolders: Boolean,
    previewCount: (FolderRule) -> Int,
) {
    val name = rememberTextFieldState(existing?.name.orEmpty())
    var smart by remember { mutableStateOf(smartFolders && existing?.rule != null) }
    var rule by remember { mutableStateOf(existing?.rule ?: FolderRule.RecentlyInstalled()) }

    /** Picks a rule; a blank name, or the previous rule's name, follows the rule ("New apps"). */
    fun chooseRule(next: FolderRule) {
        val current = name.text.toString().trim()
        if (current.isEmpty() || current == rule.defaultName) name.setTextAndPlaceCursorAtEnd(next.defaultName)
        rule = next
    }
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
    Column(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        Text(if (existing == null) "New folder" else "Edit folder", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(12.dp))
        if (smartFolders) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !smart,
                    onClick = { smart = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Choose apps") }
                SegmentedButton(
                    selected = smart,
                    onClick = {
                        if (!smart && name.text.isBlank()) name.setTextAndPlaceCursorAtEnd(rule.defaultName)
                        smart = true
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
                ) { Text("Fills itself") }
            }
            Spacer(Modifier.size(12.dp))
        }
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
        if (smart) {
            SmartRulePicker(rule = rule, onRule = ::chooseRule, previewCount = previewCount)
        } else {
            AppChecklist(search, filtered, selected)
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
                    val description = description.text.toString().ifBlank { null }
                    if (smart) {
                        onSave(name.text.toString(), description, accent, emptyList(), rule)
                    } else {
                        onSave(name.text.toString(), description, accent, ordered, null)
                    }
                    onDismiss()
                },
            ) { Text(if (existing == null) "Create" else "Save", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The searchable multi-select app list of a regular folder. */
@Composable
private fun AppChecklist(search: TextFieldState, filtered: List<AppInfo>, selected: MutableList<String>) {
    Column {
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
    }
}

/** What fills a smart folder: new apps, unused apps, most used, a category, or the launcher's tools. */
@Composable
private fun SmartRulePicker(rule: FolderRule, onRule: (FolderRule) -> Unit, previewCount: (FolderRule) -> Int) {
    Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
        RuleOption("New apps", "Apps you installed recently", selected = rule is FolderRule.RecentlyInstalled, onSelect = {
            onRule(FolderRule.RecentlyInstalled((rule as? FolderRule.RecentlyInstalled)?.days ?: 14))
        }) {
            OptionChips(FolderRule.INSTALLED_DAY_OPTIONS, (rule as? FolderRule.RecentlyInstalled)?.days, { "$it days" }) { onRule(FolderRule.RecentlyInstalled(it)) }
        }
        RuleOption("Not used lately", "Apps you haven't opened for a while", selected = rule is FolderRule.NotUsedFor, onSelect = {
            onRule(FolderRule.NotUsedFor((rule as? FolderRule.NotUsedFor)?.days ?: 30))
        }) {
            OptionChips(FolderRule.UNUSED_DAY_OPTIONS, (rule as? FolderRule.NotUsedFor)?.days, { "$it+ days" }) { onRule(FolderRule.NotUsedFor(it)) }
        }
        RuleOption("Most used", "The apps you open most often", selected = rule is FolderRule.MostUsed, onSelect = {
            onRule(FolderRule.MostUsed((rule as? FolderRule.MostUsed)?.count ?: 12))
        }) {
            OptionChips(FolderRule.MOST_USED_OPTIONS, (rule as? FolderRule.MostUsed)?.count, { "Top $it" }) { onRule(FolderRule.MostUsed(it)) }
        }
        RuleOption("By category", "Games, Social, Finance… (as sorted in the drawer)", selected = rule is FolderRule.Category, onSelect = {
            onRule(FolderRule.Category((rule as? FolderRule.Category)?.category ?: AppCategory.GAMES))
        }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCategory.entries.forEach { category ->
                    FilterChip(
                        selected = (rule as? FolderRule.Category)?.category == category,
                        onClick = { onRule(FolderRule.Category(category)) },
                        label = { Text(category.displayName) },
                    )
                }
            }
        }
        RuleOption("AI Launcher tools", "Assistant, my day, planner, to-dos, Private Space…", selected = rule == FolderRule.LauncherTools, onSelect = {
            onRule(FolderRule.LauncherTools)
        })
    }
    val count = previewCount(rule)
    Text(
        when {
            rule == FolderRule.LauncherTools -> "$count launcher tools"
            count == 1 -> "Right now: 1 app. It updates by itself."
            else -> "Right now: $count apps. It updates by itself."
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun RuleOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    options: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected && options != null) {
            Box(Modifier.padding(start = 48.dp, top = 6.dp, end = 4.dp)) { options() }
        }
    }
}

@Composable
private fun OptionChips(values: List<Int>, selected: Int?, label: (Int) -> String, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = selected == value, onClick = { onPick(value) }, label = { Text(label(value)) })
        }
    }
}
