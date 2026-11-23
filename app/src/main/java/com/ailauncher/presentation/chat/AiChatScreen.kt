package com.ailauncher.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.ai.SystemPrompts
import com.ailauncher.domain.ai.ToolNames
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.ToolStatus
import com.ailauncher.presentation.components.EngineDetailsSheet
import com.ailauncher.presentation.components.EngineStatusChip
import com.ailauncher.presentation.components.MarkdownText
import com.ailauncher.presentation.components.ThemedSystemBarIcons

private val QUICK_ACTIONS = listOf("Set alarm for 7 AM", "Organize my apps", "Summarize my day", "Timer 10 min", "Turn on flashlight")

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onOpenModelSetup: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var showEngine by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    ThemedSystemBarIcons()

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Proceed either way: tools degrade gracefully (local reminders, agenda without calendar).
        pendingAction?.invoke()
        pendingAction = null
    }

    fun withPermissions(required: List<String>, action: () -> Unit) {
        val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissions.launch(missing.toTypedArray())
        }
    }

    fun permissionsFor(toolName: String): List<String> = buildList {
        if (toolName == ToolNames.CREATE_REMINDER) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (state.saveRemindersToCalendar) {
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val agenda = trimmed.contains("my day", ignoreCase = true) || trimmed.contains("schedule", ignoreCase = true) ||
            trimmed.contains("agenda", ignoreCase = true)
        val action = {
            viewModel.send(trimmed)
            input = ""
        }
        if (agenda) withPermissions(listOf(Manifest.permission.READ_CALENDAR), action) else action()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(SystemPrompts.ASSISTANT_NAME)
                        Spacer(Modifier.width(12.dp))
                        EngineStatusChip(state.engine, state.health, onClick = { showEngine = true })
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = state.messages.isNotEmpty()) {
                        Icon(Icons.Rounded.DeleteSweep, "Clear chat")
                    }
                },
            )
        },
        bottomBar = {
            ChatInput(
                value = input,
                onValueChange = { input = it },
                busy = state.busy,
                reasoning = state.mode == InferenceMode.REASONING,
                onToggleReasoning = viewModel::toggleReasoning,
                onSend = { send(input) },
                onStop = viewModel::stop,
                onQuickAction = ::send,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.messages.asReversed(), key = { it.message.id }) { item ->
                MessageItem(
                    item = item,
                    onConfirm = { invocation ->
                        withPermissions(permissionsFor(invocation.name)) { viewModel.confirmTool(item.message.id, invocation.id) }
                    },
                    onCancel = { invocation -> viewModel.cancelTool(item.message.id, invocation.id) },
                )
            }
            if (state.messages.isEmpty()) {
                item(key = "empty") { EmptyChat() }
            }
            if (state.engine == EngineState.NoModel) {
                item(key = "no-model") { ModelMissingCard(onOpenModelSetup) }
            }
        }
    }

    if (showEngine) {
        EngineDetailsSheet(
            state = state.engine,
            health = state.health,
            lastTokensPerSecond = state.lastTokensPerSecond,
            onLoad = viewModel::loadModel,
            onUnload = viewModel::unloadModel,
            onManageModel = {
                showEngine = false
                onOpenModelSetup()
            },
            onDismiss = { showEngine = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear conversation?") },
            text = { Text("The chat history stored on this phone will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearChat()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    reasoning: Boolean,
    onToggleReasoning: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onQuickAction: (String) -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            AnimatedVisibility(visible = !busy && value.isEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_ACTIONS.forEach { action -> SuggestionChip(onClick = { onQuickAction(action) }, label = { Text(action) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = reasoning,
                    onClick = onToggleReasoning,
                    label = { Text("Reasoning") },
                    leadingIcon = { Icon(Icons.Rounded.Psychology, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("Message ${SystemPrompts.ASSISTANT_NAME}…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (busy) {
                    FilledIconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop generating") }
                } else {
                    FilledIconButton(onClick = onSend, enabled = value.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(item: ChatMessageUi, onConfirm: (ToolInvocation) -> Unit, onCancel: (ToolInvocation) -> Unit) {
    val message = item.message
    if (message.role == ChatRole.USER) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(message.content, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.thinking?.let { thinking ->
            ThoughtAccordion(thinking = thinking, live = item.streaming && !item.thinkingComplete, messageId = message.id)
        }
        val showTyping = item.streaming && message.content.isBlank() && (message.thinking == null || item.thinkingComplete) && message.toolCalls.isEmpty()
        when {
            message.content.isNotBlank() -> Surface(
                color = if (message.status == MessageStatus.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                MarkdownText(
                    message.content,
                    color = if (message.status == MessageStatus.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            showTyping -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
                Text("…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            }
        }
        if (item.toolCallInProgress) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Preparing action…", style = MaterialTheme.typography.labelMedium)
            }
        }
        message.toolCalls.forEach { invocation ->
            ToolActionCard(invocation, onConfirm = { onConfirm(invocation) }, onCancel = { onCancel(invocation) })
        }
        val footer = when {
            message.status == MessageStatus.CANCELLED -> "Stopped"
            !item.streaming && message.tokensPerSecond != null -> "%.1f tok/s · %s".format(
                message.tokensPerSecond,
                if (message.mode == InferenceMode.REASONING) "reasoning" else "fast",
            )
            else -> null
        }
        footer?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Collapsible view of Qwen3's `<think>` block. Expanded live while the model is thinking. */
@Composable
private fun ThoughtAccordion(thinking: String, live: Boolean, messageId: Long) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    val open = expanded || live
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.widthIn(max = 340.dp).clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(if (live) "Thinking…" else "Thought process", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
            }
            if (live) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            AnimatedVisibility(visible = open) {
                Text(
                    thinking.ifBlank { "…" },
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Inline confirmation card for a tool call (alarm, reminder, app launch, …). */
@Composable
private fun ToolActionCard(invocation: ToolInvocation, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val (icon, title) = toolPresentation(invocation.name)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (invocation.status) {
                ToolStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                ToolStatus.SUCCEEDED -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
        modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (invocation.summary.isNotBlank() && invocation.summary != title) {
                        Text(invocation.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            when (invocation.status) {
                ToolStatus.AWAITING_CONFIRMATION -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm) { Text("Confirm") }
                }
                ToolStatus.RUNNING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ToolStatus.SUCCEEDED -> StatusLine(Icons.Rounded.CheckCircle, invocation.resultMessage ?: "Done")
                ToolStatus.FAILED -> StatusLine(Icons.Rounded.ErrorOutline, invocation.resultMessage ?: "Failed")
                ToolStatus.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun toolPresentation(name: String): Pair<ImageVector, String> = when (name) {
    ToolNames.SET_ALARM -> Icons.Rounded.Alarm to "Set alarm"
    ToolNames.SET_TIMER -> Icons.Rounded.Timer to "Start timer"
    ToolNames.CREATE_REMINDER -> Icons.Rounded.NotificationAdd to "Create reminder"
    ToolNames.LAUNCH_APP -> Icons.AutoMirrored.Rounded.OpenInNew to "Open app"
    ToolNames.TOGGLE_SETTING -> Icons.Rounded.ToggleOn to "Device setting"
    ToolNames.SORT_APPS -> Icons.Rounded.AutoAwesome to "Organize apps"
    ToolNames.GET_TODAY_AGENDA -> Icons.Rounded.CalendarToday to "Today's agenda"
    else -> Icons.Rounded.AutoAwesome to name
}

@Composable
private fun EmptyChat() {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Hi, I'm ${SystemPrompts.ASSISTANT_NAME}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "I run entirely on your phone. Ask me anything, or tell me to set alarms, reminders and timers, open apps or organize your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun ModelMissingCard(onOpenModelSetup: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("On-device model not downloaded", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Quick commands like \"open camera\" or \"timer 5 min\" already work offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = onOpenModelSetup) { Text("Set up") }
        }
    }
}
