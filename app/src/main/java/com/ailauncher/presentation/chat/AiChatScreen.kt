package com.ailauncher.presentation.chat

import androidx.compose.foundation.background
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.ailauncher.domain.model.ChatBackground
import java.io.File
import com.ailauncher.BuildConfig
import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.data.share.SharedPayload
import com.ailauncher.domain.ai.SystemPrompts
import com.ailauncher.domain.ai.ToolNames
import com.ailauncher.domain.clipboard.ClipAction
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ReportReason
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.ToolStatus
import com.ailauncher.presentation.components.EngineDetailsSheet
import com.ailauncher.presentation.components.EngineStatusChip
import com.ailauncher.presentation.components.SwipeDirection
import com.ailauncher.presentation.components.horizontalSwipe
import com.ailauncher.presentation.components.rememberSwipeAction
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.presentation.components.MarkdownText
import com.ailauncher.presentation.components.NotificationDigestDisclosure
import com.ailauncher.presentation.components.SpecialAccess
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import com.ailauncher.presentation.components.sharedCommandBar
import com.ailauncher.presentation.planner.PlannerTab
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val QUICK_ACTIONS = listOf(
    "Summarize my day",
    "Plan my day",
    "What did I miss?",
    "Set alarm for 7 AM",
    "Apps I haven't used in a month",
    "Largest videos",
    "Timer 10 min",
    "Turn on flashlight",
)

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onOpenModelSetup: () -> Unit,
    onOpenPlanner: (PlannerTab) -> Unit = {},
    /** The System info page; when set, swiping left (onwards from home) or the chip icon opens it. */
    onOpenSystemInfo: (() -> Unit)? = null,
    /** A message to send once (the panel was opened with a prompt); then [onPromptConsumed]. */
    pendingPrompt: String? = null,
    onPromptConsumed: () -> Unit = {},
    /** Shown as a panel beside the home screen: the panel handles swipes and status-bar icons. */
    asPanel: Boolean = false,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shared by viewModel.shared.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var showEngine by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showDigestDisclosure by remember { mutableStateOf(false) }
    var reportingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCustomize by remember { mutableStateOf(false) }
    val customizable = BuildConfig.CHAT_CUSTOMIZATION
    // The system photo picker: no storage permission; the photo is copied into the app.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importBackground)
    }
    val assistantName = if (customizable) state.assistant.displayName else SystemPrompts.ASSISTANT_NAME
    val background = if (customizable) state.background else ChatBackground.DEFAULT
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (!asPanel) ThemedSystemBarIcons()
    LaunchedEffect(viewModel) { viewModel.notices.collect { snackbar.showSnackbar(it) } }
    // No in-app report channel in this build: finish the report in the email app.
    LaunchedEffect(viewModel) {
        viewModel.emailReports.collect { email ->
            val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(email.to))
                .putExtra(Intent.EXTRA_SUBJECT, email.subject)
                .putExtra(Intent.EXTRA_TEXT, email.body)
            if (runCatching { context.startActivity(intent) }.isFailure) snackbar.showSnackbar("No email app found to send the report")
        }
    }

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

    fun permissionsFor(invocation: ToolInvocation): List<String> = buildList {
        when (invocation.name) {
            ToolNames.CREATE_REMINDER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                if (state.saveRemindersToCalendar) {
                    add(Manifest.permission.READ_CALENDAR)
                    add(Manifest.permission.WRITE_CALENDAR)
                }
            }
            ToolNames.CONTACT_ACTION -> {
                add(Manifest.permission.READ_CONTACTS)
                // Without CALL_PHONE a call opens the dialer pre-filled instead.
                val action = (invocation.resolved ?: invocation.arguments)["action"]?.jsonPrimitive?.contentOrNull
                if (action.equals("call", ignoreCase = true)) add(Manifest.permission.CALL_PHONE)
            }
            ToolNames.MANAGE_PLANNER, ToolNames.GET_TODAY_AGENDA -> add(Manifest.permission.READ_CALENDAR)
        }
    }

    // Permission a tool reported missing: ask, then re-run the question.
    val retryPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.retryLast()
    }
    // Google Play build: the system folder picker grants access to one folder at a time.
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.addSearchFolder(it.toString()) }
    }
    val grant: (String) -> Unit = { need ->
        when (need) {
            "folders" -> runCatching { folderPicker.launch(null) }.onFailure { viewModel.showMessage("No folder picker is available") }
            "usage_access" -> SpecialAccess.open(context, SpecialAccess.usageAccessIntent(context), Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "all_files" -> SpecialAccess.open(context, SpecialAccess.allFilesAccessIntent(context))
            "media" -> retryPermissions.launch(SpecialAccess.mediaPermissions())
            "contacts" -> retryPermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            "notification_access" -> showDigestDisclosure = true
        }
    }
    val cardCallbacks = ToolCardCallbacks(
        onOpenApp = viewModel::openApp,
        onHideApp = viewModel::hideApp,
        onUninstallApp = viewModel::uninstallApp,
        onOpenFile = { file ->
            val uri = file.uri.toUri()
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (runCatching { context.startActivity(view) }.isSuccess) viewModel.recordFileAccess(file, "opened")
            else viewModel.showMessage("No app can open ${file.name}")
        },
        onShareFile = { file ->
            val send = Intent(Intent.ACTION_SEND).setType(file.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, file.uri.toUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (runCatching { context.startActivity(Intent.createChooser(send, file.name)) }.isSuccess) viewModel.recordFileAccess(file, "shared")
        },
        onOpenPlanner = onOpenPlanner,
        onGrant = grant,
    )

    fun needsCalendar(text: String) = text.contains("my day", ignoreCase = true) || text.contains("schedule", ignoreCase = true) ||
        text.contains("agenda", ignoreCase = true) || text.contains("free", ignoreCase = true)

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val action = { viewModel.send(trimmed) }
        if (needsCalendar(trimmed)) withPermissions(listOf(Manifest.permission.READ_CALENDAR), action) else action()
    }

    fun sendInput() {
        val text = viewModel.input.text.toString()
        if (text.isBlank()) return
        if (needsCalendar(text)) withPermissions(listOf(Manifest.permission.READ_CALENDAR)) { viewModel.sendInput() } else viewModel.sendInput()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Opened with a prompt (the home command bar, a clipboard action, the planner): send it once.
    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        onPromptConsumed()
        send(prompt)
    }

    val swipeToSystemInfo = rememberSwipeAction { onOpenSystemInfo?.invoke() }
    val veiled = background != ChatBackground.DEFAULT
    Box(Modifier.fillMaxSize()) {
    ChatBackdrop(background, state.backgroundFile, state.backgroundVersion, state.backgroundDim)
    Scaffold(
        containerColor = if (veiled) Color.Transparent else MaterialTheme.colorScheme.background,
        // Home ▸ swipe left ▸ assistant ▸ swipe left ▸ System info.
        modifier = if (onOpenSystemInfo != null && !asPanel) Modifier.horizontalSwipe(SwipeDirection.LEFT, swipeToSystemInfo) else Modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = if (veiled) {
                    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(assistantName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(12.dp))
                        EngineStatusChip(state.engine, state.health, onClick = { showEngine = true })
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = state.messages.isNotEmpty()) {
                        Icon(Icons.Rounded.DeleteSweep, "Clear chat")
                    }
                    if (customizable) {
                        IconButton(onClick = { showCustomize = true }) { Icon(Icons.Rounded.Tune, "Customize chat and assistant") }
                    }
                    if (onOpenSystemInfo != null) {
                        IconButton(onClick = onOpenSystemInfo) { Icon(Icons.Rounded.Memory, "System info") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ChatInput(
                state = viewModel.input,
                busy = state.busy,
                reasoning = state.mode == InferenceMode.REASONING,
                onToggleReasoning = viewModel::toggleReasoning,
                onSend = ::sendInput,
                onStop = viewModel::stop,
                onQuickAction = ::send,
                compactThinking = BuildConfig.CHAT_CUSTOMIZATION,
                placeholder = "Message $assistantName…",
                translucent = veiled,
            )
        },
    ) { padding ->
        ChatMessageList(
            state = state,
            shared = shared,
            listState = listState,
            padding = padding,
            cardCallbacks = cardCallbacks,
            onConfirm = { item, invocation ->
                withPermissions(permissionsFor(invocation)) { viewModel.confirmTool(item.message.id, invocation.id) }
            },
            onCancel = { item, invocation -> viewModel.cancelTool(item.message.id, invocation.id) },
            onReport = { id -> reportingId = id },
            onSharedAction = viewModel::useShared,
            onSharedDismiss = viewModel::dismissShared,
            onOpenModelSetup = onOpenModelSetup,
        )
    }
    }

    if (showCustomize) {
        ChatCustomizeSheet(
            state = state,
            onBackground = viewModel::setBackground,
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDim = viewModel::setBackgroundDim,
            onSaveAssistant = viewModel::saveAssistant,
            promptFor = viewModel::systemPromptFor,
            onDismiss = { showCustomize = false },
        )
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
    reportingId?.let { id ->
        ReportReplyDialog(
            opensEmail = viewModel.reportsOpenEmail,
            onSend = { reason, details, includeQuestion ->
                viewModel.report(id, reason, details, includeQuestion)
                reportingId = null
            },
            onDismiss = { reportingId = null },
        )
    }
    if (showDigestDisclosure) {
        NotificationDigestDisclosure(
            onAccept = {
                showDigestDisclosure = false
                viewModel.enableNotificationDigest()
                SpecialAccess.open(context, SpecialAccess.notificationListenerIntent(context), Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onDismiss = { showDigestDisclosure = false },
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

/** The conversation, newest at the bottom. Stateless, so screenshot tests can render it. */
@Composable
internal fun ChatMessageList(
    state: ChatUiState,
    shared: SharedPayload?,
    listState: LazyListState,
    padding: PaddingValues,
    cardCallbacks: ToolCardCallbacks,
    onConfirm: (ChatMessageUi, ToolInvocation) -> Unit,
    onCancel: (ChatMessageUi, ToolInvocation) -> Unit,
    onReport: (Long) -> Unit,
    onSharedAction: (ClipAction) -> Unit,
    onSharedDismiss: () -> Unit,
    onOpenModelSetup: () -> Unit,
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        shared?.let { payload ->
            item(key = "shared") {
                SharedContentCard(payload, onAction = onSharedAction, onDismiss = onSharedDismiss)
            }
        }
        items(state.messages.asReversed(), key = { it.message.id }) { item ->
            MessageItem(
                item = item,
                reported = item.message.id in state.reportedIds,
                onReport = { onReport(item.message.id) },
                cardCallbacks = cardCallbacks,
                onConfirm = { invocation -> onConfirm(item, invocation) },
                onCancel = { invocation -> onCancel(item, invocation) },
            )
        }
        if (state.messages.isEmpty()) {
            item(key = "empty") { EmptyChat(if (BuildConfig.CHAT_CUSTOMIZATION) state.assistant.displayName else SystemPrompts.ASSISTANT_NAME) }
        }
        if (state.engine == EngineState.NoModel) {
            item(key = "no-model") { ModelMissingCard(onOpenModelSetup) }
        }
    }
}

@Composable
internal fun ChatInput(
    state: TextFieldState,
    busy: Boolean,
    reasoning: Boolean,
    onToggleReasoning: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onQuickAction: (String) -> Unit,
    /** A small "think" toggle instead of the Reasoning chip, so the message box gets the room. */
    compactThinking: Boolean = false,
    placeholder: String = "Message ${SystemPrompts.ASSISTANT_NAME}…",
    /** Under a chat background picture, the bar becomes translucent. */
    translucent: Boolean = false,
) {
    Surface(
        tonalElevation = 3.dp,
        color = if (translucent) MaterialTheme.colorScheme.surface.copy(alpha = 0.82f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            AnimatedVisibility(visible = !busy && state.text.isEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_ACTIONS.forEach { action -> SuggestionChip(onClick = { onQuickAction(action) }, label = { Text(action) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (compactThinking) {
                    ThinkToggle(reasoning, onToggleReasoning)
                    Spacer(Modifier.width(4.dp))
                } else {
                    FilterChip(
                        selected = reasoning,
                        onClick = onToggleReasoning,
                        label = { Text("Reasoning") },
                        leadingIcon = { Icon(Icons.Rounded.Psychology, null, Modifier.size(18.dp)) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                // The home screen's AI command bar morphs into this field (shared bounds).
                LauncherTextField(
                    state = state,
                    placeholder = placeholder,
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).sharedCommandBar(),
                )
                Spacer(Modifier.width(8.dp))
                if (busy) {
                    FilledIconButton(onClick = onStop) { Icon(Icons.Rounded.Stop, "Stop generating") }
                } else {
                    FilledIconButton(onClick = onSend, enabled = state.text.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }
                }
            }
        }
    }
}

/**
 * What is behind the messages: nothing (the theme's background), the home wallpaper (the window
 * shows it through) or the user's photo, veiled with the surface colour so text stays readable.
 */
@Composable
internal fun ChatBackdrop(background: ChatBackground, photo: File?, version: Long?, dim: Float) {
    if (background == ChatBackground.DEFAULT) return
    if (background == ChatBackground.PHOTO && photo != null) {
        val context = LocalContext.current
        val request = remember(photo, version) {
            ImageRequest.Builder(context).data(photo).memoryCacheKey("chat-background-$version").diskCachePolicy(CachePolicy.DISABLED).build()
        }
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = dim.coerceIn(0f, 0.9f))))
}

/** Think before answering (Reasoning mode) as a small round toggle: filled when on. */
@Composable
private fun ThinkToggle(on: Boolean, onToggle: () -> Unit) {
    IconToggleButton(
        checked = on,
        onCheckedChange = { onToggle() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(Icons.Rounded.Psychology, if (on) "Thinking on: tap for quick answers" else "Thinking off: tap to think before answering", Modifier.size(22.dp))
    }
}

/** Text or a PDF shared to the launcher: choose what the on-device assistant should do with it. */
@Composable
private fun SharedContentCard(payload: SharedPayload, onAction: (ClipAction) -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (payload.kind == SharedPayload.Kind.PDF) Icons.Rounded.PictureAsPdf else Icons.AutoMirrored.Rounded.ShortText,
                    null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    payload.title ?: if (payload.kind == SharedPayload.Kind.PDF) "Shared PDF" else "Shared text",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss") }
            }
            if (payload.text.isNotBlank()) {
                Text(
                    payload.text.take(240),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            payload.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (payload.text.isNotBlank()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val actions = buildList {
                        add(ClipAction.SUMMARIZE)
                        add(ClipAction.EXTRACT_TASKS)
                        if (payload.kind == SharedPayload.Kind.TEXT && payload.text.length <= 120) add(ClipAction.ADD_TODO)
                        add(ClipAction.ASK)
                    }
                    actions.forEach { action -> AssistChip(onClick = { onAction(action) }, label = { Text(action.label) }) }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    item: ChatMessageUi,
    reported: Boolean,
    onReport: () -> Unit,
    cardCallbacks: ToolCardCallbacks,
    onConfirm: (ToolInvocation) -> Unit,
    onCancel: (ToolInvocation) -> Unit,
) {
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
        var revealed by rememberSaveable(message.id) { mutableStateOf(false) }
        when {
            message.content.isNotBlank() && reported && !revealed -> Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Flag, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("You reported this reply.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { revealed = true }) { Text("Show") }
                }
            }
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
            ToolActionCard(invocation, cardCallbacks, onConfirm = { onConfirm(invocation) }, onCancel = { onCancel(invocation) })
        }
        val footer = when {
            message.status == MessageStatus.CANCELLED -> "Stopped"
            !item.streaming && message.tokensPerSecond != null -> "%.1f tok/s · %s".format(
                message.tokensPerSecond,
                if (message.mode == InferenceMode.REASONING) "reasoning" else "fast",
            )
            else -> null
        }
        val reportable = !item.streaming && !reported && message.content.isNotBlank() && message.status != MessageStatus.ERROR
        if (footer != null || reportable) {
            Row(Modifier.widthIn(max = 340.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    footer.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
                // Google Play: AI replies can be reported from inside the app.
                if (reportable) {
                    TextButton(onClick = onReport, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.OutlinedFlag, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Report", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Report an assistant reply: a reason, optional details, and exactly what will be sent. */
@Composable
private fun ReportReplyDialog(
    opensEmail: Boolean,
    onSend: (ReportReason, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var includeQuestion by rememberSaveable { mutableStateOf(true) }
    val details = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Flag, null) },
        title = { Text("Report this reply") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("What's wrong with it?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                ReportReason.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == option, onClick = { reason = option }, role = Role.RadioButton)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                LauncherTextField(
                    state = details,
                    placeholder = "Add details (optional)",
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = includeQuestion, onValueChange = { includeQuestion = it }, role = Role.Checkbox)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeQuestion, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Include my question", style = MaterialTheme.typography.bodyMedium)
                }
                val what = if (includeQuestion) "this reply and your question" else "this reply"
                Text(
                    if (opensEmail) {
                        "Opens your email app with $what filled in, addressed to the developer. Nothing else from your phone is included."
                    } else {
                        "Sends $what to the developer so the assistant can be made safer. Nothing else from your phone is sent."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = reason != null, onClick = { reason?.let { onSend(it, details.text.toString(), includeQuestion) } }) {
                Text("Send report")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun ToolActionCard(invocation: ToolInvocation, cardCallbacks: ToolCardCallbacks, onConfirm: () -> Unit, onCancel: () -> Unit) {
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
                ToolStatus.SUCCEEDED -> {
                    StatusLine(Icons.Rounded.CheckCircle, invocation.resultMessage ?: "Done")
                    ToolResultExtras(invocation.displayData, cardCallbacks)
                }
                ToolStatus.FAILED -> {
                    StatusLine(Icons.Rounded.ErrorOutline, invocation.resultMessage ?: "Failed")
                    ToolResultExtras(invocation.displayData, cardCallbacks)
                }
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
    ToolNames.LIST_UNUSED_APPS -> Icons.Rounded.Apps to "Unused apps"
    ToolNames.FIND_LARGEST_FILES -> Icons.Rounded.Storage to "Largest files"
    ToolNames.QUERY_FILES_BY_ACTIVITY -> Icons.Rounded.FolderOpen to "Files"
    ToolNames.MANAGE_TODO -> Icons.Rounded.Checklist to "To-do"
    ToolNames.MANAGE_PLANNER -> Icons.Rounded.ViewTimeline to "Day planner"
    ToolNames.SUMMARIZE_NOTIFICATIONS -> Icons.Rounded.NotificationsActive to "Notifications"
    ToolNames.CONTACT_ACTION -> Icons.Rounded.Call to "Contact"
    else -> Icons.Rounded.AutoAwesome to name
}

@Composable
private fun EmptyChat(name: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Hi, I'm $name", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "I run entirely on your phone. Ask me anything, or tell me to set alarms, reminders and timers, open apps and folders, " +
                "manage your to-dos and day plan, find big or old files, spot unused apps, or call and message contacts.\n\n" +
                "My answers are generated by AI on this phone and can be wrong. Use Report under a reply to flag anything inappropriate.",
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
