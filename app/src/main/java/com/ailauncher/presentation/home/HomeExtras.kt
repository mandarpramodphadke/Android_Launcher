package com.ailauncher.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ailauncher.data.media.NowPlaying
import com.ailauncher.domain.model.ContactMatch
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.search.QuickAnswer
import com.ailauncher.domain.search.SettingsShortcut
import com.ailauncher.presentation.theme.WallpaperTint
import java.util.Locale

/** What the music chip and the instant results can ask for (wired to the ViewModel). */
class HomeExtrasCallbacks(
    val onMediaPlayPause: (NowPlaying) -> Unit = {},
    val onMediaNext: (NowPlaying) -> Unit = {},
    val onMediaPrevious: (NowPlaying) -> Unit = {},
    val onMediaOpen: (NowPlaying) -> Unit = {},
    val onCopyAnswer: (QuickAnswer) -> Unit = {},
    val onSettingsPage: (SettingsShortcut) -> Unit = {},
    val onCallContact: (ContactMatch) -> Unit = {},
    val onMessageContact: (ContactMatch) -> Unit = {},
    val onTodo: (TodoItem) -> Unit = {},
    val onFile: (FileEntry) -> Unit = {},
)

// ------------------------------------------------------------------------------- music

/**
 * What's playing, as a chip among the header chips under the clock: album art, title and artist
 * (tap opens the player), previous, play / pause and next.
 */
@Composable
internal fun MediaChip(item: NowPlaying, tint: WallpaperTint, callbacks: HomeExtrasCallbacks) {
    Surface(shape = RoundedCornerShape(50), color = tint.chip, contentColor = tint.content) {
        Row(Modifier.padding(start = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClickLabel = "Open ${item.appLabel}") { callbacks.onMediaOpen(item) }
                    .padding(vertical = 4.dp)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(item, 32)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.widthIn(max = 150.dp)) {
                    Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.artist ?: item.appLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint.content.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MediaButtons(item, callbacks, buttonSize = 36)
        }
    }
}

/** Minimal mode: one line of text under the clock, with the same controls. */
@Composable
internal fun MinimalMediaLine(item: NowPlaying, tint: WallpaperTint, callbacks: HomeExtrasCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOfNotNull(item.title, item.artist).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium.copy(shadow = tint.shadow, fontWeight = FontWeight.Light),
            color = tint.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Open ${item.appLabel}") { callbacks.onMediaOpen(item) }
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.width(4.dp))
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint.secondary) {
            MediaButtons(item, callbacks, buttonSize = 36)
        }
    }
}

@Composable
private fun MediaButtons(item: NowPlaying, callbacks: HomeExtrasCallbacks, buttonSize: Int) {
    if (item.canSkipPrevious) {
        IconButton(onClick = { callbacks.onMediaPrevious(item) }, modifier = Modifier.size(buttonSize.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(20.dp))
        }
    }
    IconButton(onClick = { callbacks.onMediaPlayPause(item) }, modifier = Modifier.size(buttonSize.dp)) {
        Icon(if (item.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (item.isPlaying) "Pause" else "Play", Modifier.size(22.dp))
    }
    if (item.canSkipNext) {
        IconButton(onClick = { callbacks.onMediaNext(item) }, modifier = Modifier.size(buttonSize.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AlbumArt(item: NowPlaying, sizeDp: Int) {
    val art = remember(item.art) { item.art?.asImageBitmap() }
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
    } else {
        Box(
            Modifier.size(sizeDp.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ------------------------------------------------------------------------- instant search

/**
 * Instant answers above the app results while typing in the command bar: a sum or conversion,
 * settings pages, contacts, to-dos and files. Each row does one thing; Enter still asks the
 * assistant.
 */
@Composable
internal fun InstantResultsCard(results: InstantResults, callbacks: HomeExtrasCallbacks, modifier: Modifier = Modifier, border: BorderStroke? = null) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = border,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            var first = true
            @Composable
            fun Divider() {
                if (!first) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                first = false
            }
            results.answer?.let { answer ->
                Divider()
                val conversion = answer.answer.any { it.isLetter() || it == '°' }
                ResultRow(
                    icon = if (conversion) Icons.Rounded.SwapHoriz else Icons.Rounded.Calculate,
                    title = answer.answer,
                    subtitle = if (conversion) answer.question else "${answer.question} =",
                    emphasized = true,
                    onClick = { callbacks.onCopyAnswer(answer) },
                    clickLabel = "Copy ${answer.copyText}",
                    trailing = { Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            results.contacts.forEach { contact ->
                Divider()
                ResultRow(
                    icon = Icons.Rounded.Person,
                    title = contact.displayName,
                    subtitle = listOfNotNull(contact.phoneLabel, contact.phoneNumber).joinToString(" · "),
                    onClick = { callbacks.onCallContact(contact) },
                    clickLabel = "Call ${contact.displayName}",
                    trailing = {
                        IconButton(onClick = { callbacks.onMessageContact(contact) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Message, "Message ${contact.displayName}", Modifier.size(20.dp))
                        }
                        IconButton(onClick = { callbacks.onCallContact(contact) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Call, "Call ${contact.displayName}", Modifier.size(20.dp))
                        }
                    },
                )
            }
            results.settings.forEach { page ->
                Divider()
                ResultRow(icon = Icons.Rounded.Settings, title = page.title, subtitle = "Phone settings", onClick = { callbacks.onSettingsPage(page) }, clickLabel = "Open ${page.title} settings")
            }
            results.todos.forEach { todo ->
                Divider()
                ResultRow(icon = Icons.Rounded.CheckBoxOutlineBlank, title = todo.title, subtitle = "To-do · ${todo.listName}", onClick = { callbacks.onTodo(todo) }, clickLabel = "Open to-dos")
            }
            results.files.forEach { file ->
                Divider()
                ResultRow(
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    title = file.name,
                    subtitle = listOfNotNull(file.relativePath?.trimEnd('/'), formatSize(file.sizeBytes)).joinToString(" · "),
                    onClick = { callbacks.onFile(file) },
                    clickLabel = "Open ${file.name}",
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    clickLabel: String,
    emphasized: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(title, subtitle).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let { Row(verticalAlignment = Alignment.CenterVertically) { it() } }
    }
}

private fun formatSize(bytes: Long): String? = when {
    bytes <= 0 -> null
    bytes < 1_000_000 -> "${(bytes / 1000).coerceAtLeast(1)} KB"
    bytes < 1_000_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1e6)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1e9)
}
