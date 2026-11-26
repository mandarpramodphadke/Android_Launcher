package com.ailauncher.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ailauncher.BuildConfig
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.Memory
import com.ailauncher.domain.model.SpokenReplies

/**
 * Settings ▸ AI assistant additions of 1.8, each only where its feature is on in this build (full
 * version first): the assistant's memory, and replies read aloud.
 */
internal fun LazyListScope.assistantExtrasSettings(
    settings: LauncherSettings,
    memories: List<Memory>,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onShowMemories: () -> Unit,
) {
    if (BuildConfig.ASSISTANT_MEMORY) {
        item {
            SwitchSetting(
                "Memory",
                "Say \"remember my car is on level 3\" and the assistant uses it in later answers. Kept on this phone.",
                settings.assistantMemory,
            ) { on -> onUpdate { it.copy(assistantMemory = on) } }
        }
        item {
            ActionSetting(
                "Saved memories",
                when (memories.size) {
                    0 -> "Nothing saved yet"
                    1 -> "1 saved · tap to see or delete"
                    else -> "${memories.size} saved · tap to see or delete"
                },
                onClick = onShowMemories,
            )
        }
    }
    if (BuildConfig.SPOKEN_REPLIES) {
        item {
            ChoiceSetting(
                title = "Read replies aloud",
                options = listOf(
                    SpokenReplies.AFTER_VOICE to "After I use my voice",
                    SpokenReplies.ALWAYS to "Always",
                    SpokenReplies.OFF to "Never",
                ),
                selected = settings.spokenReplies,
                onSelect = { choice -> onUpdate { it.copy(spokenReplies = choice) } },
            )
        }
        item {
            Text(
                "Uses your phone's text-to-speech with an offline voice, so replies never leave the phone. If only online voices are installed, add an offline one in Android's text-to-speech settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

/** Everything the assistant remembers, newest first, each one deletable. */
@Composable
internal fun MemoriesSheet(memories: List<Memory>, onDelete: (Memory) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Saved memories", style = MaterialTheme.typography.titleLarge)
            Text(
                "What you asked the assistant to remember. It is kept on this phone and shown only to the on-device model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (memories.isEmpty()) {
                Text(
                    "Nothing yet. Try \"remember that my locker code is 4512\".",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(memories, key = { it.id }) { memory ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(memory.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(memory) }) { Icon(Icons.Rounded.Close, "Forget \"${memory.text}\"") }
                        }
                        HorizontalDivider()
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmClear = true }) { Text("Forget everything") }
                }
            }
            Spacer(Modifier.padding(4.dp))
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Forget everything?") },
            text = { Text("The assistant forgets all ${memories.size} things you asked it to remember.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
