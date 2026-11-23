package com.ailauncher.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorySource

/** Everything the long-press menu can do with an app. */
interface AppActionHandler {
    fun openAppInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun togglePin(app: AppInfo)
    fun hide(app: AppInfo)
    fun rename(app: AppInfo, label: String?)
    fun setCategory(app: AppInfo, category: AppCategory)
    fun moveInDock(app: AppInfo, offset: Int) = Unit
}

/**
 * Long-press menu: App info, Uninstall, Pin to / Remove from dock, Hide, Rename and Change
 * category (a manual override that the AI never replaces).
 */
@Composable
fun AppActionsSheet(
    app: AppInfo,
    handler: AppActionHandler,
    onDismiss: () -> Unit,
    showDockReorder: Boolean = false,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var renaming by remember { mutableStateOf(false) }
    var pickingCategory by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
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
            SheetAction(Icons.Rounded.Category, "Change category") { pickingCategory = true }
            SheetAction(Icons.Rounded.DriveFileRenameOutline, "Rename") { renaming = true }
            SheetAction(Icons.Rounded.VisibilityOff, "Hide app") {
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
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename app") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Label") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim().takeIf { it.isNotEmpty() && it != original }) }) { Text("Save") } },
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
                OutlinedSecureTextField(
                    state = first,
                    label = { Text("Passphrase") },
                    supportingText = { if (confirm && firstText.isNotEmpty() && tooShort) Text("At least $MIN_PASSPHRASE characters") },
                )
                if (confirm) {
                    OutlinedSecureTextField(
                        state = second,
                        label = { Text("Repeat passphrase") },
                        isError = second.text.isNotEmpty() && mismatch,
                    )
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
