package com.ailauncher.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ailauncher.data.packages.AppIconData
import com.ailauncher.presentation.components.formatBytes
import com.ailauncher.presentation.planner.PlannerTab
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.text.DateFormat
import java.util.Date

/** An app in a list_unused_apps card. */
data class UnusedAppEntry(
    val componentKey: String,
    val packageName: String,
    val label: String,
    val lastUsedAt: Long?,
    val sizeBytes: Long?,
)

/** A file in a find_largest_files / query_files_by_activity card. */
data class FileCardEntry(
    val uri: String,
    val name: String,
    val mime: String?,
    val sizeBytes: Long,
    val modifiedAt: Long?,
    val openedAt: Long?,
    val path: String?,
)

/** What the result cards can do; implemented by the chat screen. */
class ToolCardCallbacks(
    val onOpenApp: (UnusedAppEntry) -> Unit = {},
    val onHideApp: (UnusedAppEntry) -> Unit = {},
    val onUninstallApp: (UnusedAppEntry) -> Unit = {},
    val onOpenFile: (FileCardEntry) -> Unit = {},
    val onShareFile: (FileCardEntry) -> Unit = {},
    val onOpenPlanner: (PlannerTab) -> Unit = {},
    /** A permission or special access the tool reported missing ("usage_access", "media", …). */
    val onGrant: (String) -> Unit = {},
)

/** Interactive extras under a tool card, driven by the executor's `display` payload. */
@Composable
fun ToolResultExtras(display: JsonObject?, callbacks: ToolCardCallbacks) {
    display ?: return
    val kind = display.string("kind")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (kind) {
            "unused_apps" -> UnusedApps(display.array("apps").mapNotNull(::unusedApp), callbacks)
            "largest_files", "file_query" -> Files(display.array("files").mapNotNull(::fileEntry), callbacks)
            "todo", "todo_list" -> LinkButton(Icons.Rounded.Checklist, "Open to-dos") { callbacks.onOpenPlanner(PlannerTab.TODOS) }
            "planner" -> LinkButton(Icons.Rounded.ViewTimeline, "Open planner") { callbacks.onOpenPlanner(PlannerTab.PLAN) }
        }
        display.string("needs")?.let { need -> GrantRow(need, onGrant = { callbacks.onGrant(need) }) }
    }
}

@Composable
private fun UnusedApps(apps: List<UnusedAppEntry>, callbacks: ToolCardCallbacks) {
    if (apps.isEmpty()) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) apps else apps.take(COLLAPSED)
    HorizontalDivider()
    shown.forEach { app ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = remember(app.componentKey) {
                AppIconData(app.packageName, app.componentKey.substringAfter('/', ""), 0L)
            }
            AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val context = LocalContext.current
                Text(
                    listOfNotNull(
                        app.lastUsedAt?.let { "Last used ${shortDate(it)}" } ?: "Not used recently",
                        app.sizeBytes?.let { context.formatBytes(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { callbacks.onOpenApp(app) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open ${app.label}") }
            IconButton(onClick = { callbacks.onHideApp(app) }) { Icon(Icons.Rounded.Lock, "Hide ${app.label} in Private Space") }
            IconButton(onClick = { callbacks.onUninstallApp(app) }) { Icon(Icons.Rounded.Delete, "Uninstall ${app.label}") }
        }
    }
    if (apps.size > COLLAPSED) {
        TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "Show all ${apps.size}") }
    }
}

@Composable
private fun Files(files: List<FileCardEntry>, callbacks: ToolCardCallbacks) {
    if (files.isEmpty()) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) files else files.take(COLLAPSED)
    val context = LocalContext.current
    HorizontalDivider()
    shown.forEach { file ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(fileIcon(file.mime), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        context.formatBytes(file.sizeBytes),
                        (file.openedAt ?: file.modifiedAt)?.let { shortDate(it) },
                        file.path?.trimEnd('/'),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { callbacks.onOpenFile(file) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open ${file.name}") }
            IconButton(onClick = { callbacks.onShareFile(file) }) { Icon(Icons.Rounded.Share, "Share ${file.name}") }
        }
    }
    if (files.size > COLLAPSED) {
        TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "Show all ${files.size}") }
    }
}

@Composable
private fun GrantRow(need: String, onGrant: () -> Unit) {
    val (label, explanation) = when (need) {
        "usage_access" -> "Allow usage access" to "To include apps you open outside this launcher. Usage data stays on this phone."
        "media" -> "Allow photos, videos & audio" to "Files are searched on this phone only."
        "all_files" -> "Allow all files access" to "Needed for documents and archives (PDFs, ZIPs). Optional."
        "notification_access" -> "Turn on notification digest" to "Notifications are summarized on-device and deleted after 24 hours."
        "contacts" -> "Allow contacts" to "To find who to call or message. Contacts never leave this phone."
        else -> return
    }
    Column(Modifier.padding(top = 4.dp)) {
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onGrant, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun LinkButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

private fun fileIcon(mime: String?): ImageVector = when {
    mime == null -> Icons.AutoMirrored.Rounded.InsertDriveFile
    mime.startsWith("image/") -> Icons.Rounded.Image
    mime.startsWith("video/") -> Icons.Rounded.Movie
    mime.startsWith("audio/") -> Icons.Rounded.Audiotrack
    mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") -> Icons.Rounded.Folder
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

private fun shortDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private const val COLLAPSED = 6

private fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun JsonObject.long(key: String): Long? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
private fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()

private fun unusedApp(json: JsonObject): UnusedAppEntry? {
    val key = json.string("componentKey") ?: return null
    return UnusedAppEntry(
        componentKey = key,
        packageName = json.string("packageName") ?: key.substringBefore('/'),
        label = json.string("label") ?: key,
        lastUsedAt = json.long("lastUsedAt"),
        sizeBytes = json.long("sizeBytes"),
    )
}

private fun fileEntry(json: JsonObject): FileCardEntry? {
    val uri = json.string("uri") ?: return null
    return FileCardEntry(
        uri = uri,
        name = json.string("name") ?: uri.substringAfterLast('/'),
        mime = json.string("mime"),
        sizeBytes = json.long("sizeBytes") ?: 0L,
        modifiedAt = json.long("modifiedAt"),
        openedAt = json.long("openedAt"),
        path = json.string("path"),
    )
}
