package com.ailauncher.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.graphics.Rect
import android.os.Bundle
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppShortcut
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.CategorySource
import coil3.compose.AsyncImage
import com.ailauncher.data.packages.ShortcutIconData

/** Everything the long-press menu can do with an app. */
interface AppActionHandler {
    fun openAppInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun togglePin(app: AppInfo)
    fun hide(app: AppInfo)
    fun rename(app: AppInfo, label: String?)
    fun setCategory(app: AppInfo, category: AppCategory)
    fun moveInDock(app: AppInfo, offset: Int) = Unit
    suspend fun shortcuts(app: AppInfo): List<AppShortcut> = emptyList()
    fun launchShortcut(shortcut: AppShortcut, sourceBounds: Rect?, options: Bundle?) = Unit
    fun addToHome(app: AppInfo) = Unit
    fun addToFolder(app: AppInfo, folder: CustomFolder) = Unit
}

/**
 * Long-press menu: the app's own shortcuts, Pin to / Remove from dock, Add to home screen, Add to
 * folder, Change category, Rename, Move to Private Space, App info and Uninstall.
 */
@Composable
fun AppActionsSheet(
    app: AppInfo,
    handler: AppActionHandler,
    onDismiss: () -> Unit,
    showDockReorder: Boolean = false,
    folders: List<CustomFolder> = emptyList(),
    onCreateFolder: (() -> Unit)? = null,
    /** Set when the app was long-pressed on a home page: offers "Remove from home screen". */
    onRemoveFromHome: (() -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var renaming by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }
    var pickingFolder by remember { mutableStateOf(false) }
    var shortcuts by remember(app.packageName) { mutableStateOf<List<AppShortcut>>(emptyList()) }
    LaunchedEffect(app.packageName) { shortcuts = runCatching { handler.shortcuts(app) }.getOrDefault(emptyList()) }
    val launchFactory = rememberLaunchRequestFactory()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 48.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                    val category = app.category?.displayName ?: "Uncategorized"
                    val source = when (app.categorySource) {
                        CategorySource.AI -> " · sorted by AI"
                        CategorySource.USER -> " · set by you"
                        CategorySource.SYSTEM -> " · from the app"
                        CategorySource.HEURISTIC -> " · quick guess"
                        null -> ""
                    }
                    Text(category + source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shortcuts.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                shortcuts.forEach { shortcut ->
                    val bounds = remember { IconBounds() }
                    AppListItem(
                        headlineContent = { Text(shortcut.shortLabel, maxLines = 1) },
                        supportingContent = shortcut.longLabel?.takeIf { it != shortcut.shortLabel }?.let { { Text(it, maxLines = 1) } },
                        leadingContent = {
                            AsyncImage(
                                model = ShortcutIconData(shortcut.packageName, shortcut.id),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).trackBounds(bounds),
                            )
                        },
                        onClick = {
                            val request = launchFactory(app, bounds.rect)
                            handler.launchShortcut(shortcut, request.sourceBounds, request.options)
                            onDismiss()
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (showDockReorder && app.isInDock) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { handler.moveInDock(app, -1) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Move left")
                    }
                    TextButton(onClick = { handler.moveInDock(app, 1) }) {
                        Text("Move right")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                    }
                }
            }
            SheetAction(Icons.Rounded.PushPin, if (app.isInDock) "Remove from dock" else "Pin to dock") {
                handler.togglePin(app)
                onDismiss()
            }
            if (onRemoveFromHome != null) {
                SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from home screen") {
                    onRemoveFromHome()
                    onDismiss()
                }
            } else {
                SheetAction(Icons.AutoMirrored.Rounded.AddToHomeScreen, "Add to home screen") {
                    handler.addToHome(app)
                    onDismiss()
                }
            }
            SheetAction(Icons.Rounded.CreateNewFolder, "Add to folder") { pickingFolder = true }
            SheetAction(Icons.Rounded.Category, "Change category") { pickingCategory = true }
            SheetAction(Icons.Rounded.DriveFileRenameOutline, "Rename") { renaming = true }
            SheetAction(Icons.Rounded.Lock, "Move to Private Space") {
                handler.hide(app)
                onDismiss()
            }
            SheetAction(Icons.Rounded.Info, "App info") {
                handler.openAppInfo(app)
                onDismiss()
            }
            if (!app.isSystemApp) {
                SheetAction(Icons.Rounded.Delete, "Uninstall") {
                    handler.uninstall(app)
                    onDismiss()
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = app.customLabel ?: app.systemLabel,
            original = app.systemLabel,
            onDismiss = { renaming = false },
            onConfirm = {
                handler.rename(app, it)
                renaming = false
                onDismiss()
            },
        )
    }
    if (pickingCategory) {
        CategoryPickerDialog(
            current = app.category,
            onDismiss = { pickingCategory = false },
            onPick = {
                handler.setCategory(app, it)
                pickingCategory = false
                onDismiss()
            },
        )
    }
    if (pickingFolder) {
        AlertDialog(
            onDismissRequest = { pickingFolder = false },
            title = { Text("Add ${app.label} to…") },
            text = {
                LazyColumn {
                    // Smart folders fill themselves, so apps can't be added to them by hand.
                    items(folders.filter { !it.isSmart }, key = { it.id }) { folder ->
                        AppListItem(
                            headlineContent = { Text(folder.name, maxLines = 1) },
                            supportingContent = folder.description?.let { { Text(it, maxLines = 1) } },
                            leadingContent = { Icon(Icons.Rounded.Folder, null, tint = folder.accent.colors().second) },
                            onClick = {
                                handler.addToFolder(app, folder)
                                pickingFolder = false
                                onDismiss()
                            },
                        )
                    }
                    if (folders.none { !it.isSmart }) item { Text("No folders yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            confirmButton = {
                if (onCreateFolder != null) {
                    TextButton(onClick = {
                        pickingFolder = false
                        onDismiss()
                        onCreateFolder()
                    }) { Text("New folder") }
                }
            },
            dismissButton = { TextButton(onClick = { pickingFolder = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    AppListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
fun RenameDialog(current: String, original: String, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val state = rememberTextFieldState(current)
    val save = { onConfirm(state.text.toString().trim().takeIf { it.isNotEmpty() && it != original }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename app") },
        text = {
            LauncherTextField(
                state = state,
                placeholder = original,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { save() },
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { TextButton(onClick = save) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun CategoryPickerDialog(current: AppCategory?, onDismiss: () -> Unit, onPick: (AppCategory) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category") },
        text = {
            LazyColumn {
                items(AppCategory.entries) { category ->
                    AppListItem(
                        headlineContent = { Text(category.displayName) },
                        supportingContent = { Text(category.hint, maxLines = 1) },
                        leadingContent = { RadioButton(selected = category == current, onClick = { onPick(category) }) },
                        onClick = { onPick(category) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Passphrase prompt for encrypted backups; [confirm] asks for the passphrase twice. */
@Composable
fun PassphraseDialog(
    title: String,
    confirm: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    val first = rememberTextFieldState()
    val second = rememberTextFieldState()
    val firstText = first.text
    val tooShort = firstText.length < MIN_PASSPHRASE
    val mismatch = confirm && firstText.toString() != second.text.toString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (confirm) "The backup is encrypted on this phone with this passphrase. Without it the backup cannot be restored."
                    else "Enter the passphrase used to create the backup.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LauncherSecureTextField(state = first, placeholder = "Passphrase", imeAction = if (confirm) ImeAction.Next else ImeAction.Done)
                if (confirm && firstText.isNotEmpty() && tooShort) {
                    Text("At least $MIN_PASSPHRASE characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (confirm) {
                    LauncherSecureTextField(state = second, placeholder = "Repeat passphrase", isError = second.text.isNotEmpty() && mismatch)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = firstText.isNotEmpty() && (!confirm || (!tooShort && !mismatch)),
                onClick = { onSubmit(first.text.toString().toCharArray()) },
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MIN_PASSPHRASE = 8
