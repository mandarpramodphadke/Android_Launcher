package com.ailauncher.presentation.chat

import android.net.Uri
import com.ailauncher.data.chat.ChatBackgroundStore
import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.ChatBackground
import com.ailauncher.domain.model.LauncherSettings
import java.io.File
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ailauncher.BuildConfig
import com.ailauncher.data.llm.DeviceResources
import com.ailauncher.data.report.ReportFormTemplate
import com.ailauncher.data.share.SharedPayload
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.clipboard.ClipAction
import com.ailauncher.domain.clipboard.ClipboardAnalyzer
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.ContentReport
import com.ailauncher.domain.model.DeviceHealth
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ReportOutcome
import com.ailauncher.domain.model.ReportReason
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.ContentReportRepository
import com.ailauncher.domain.repository.FileRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.usecase.ChatAgent
import com.ailauncher.presentation.navigation.ChatRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A message plus the transient streaming flags of the reply being generated. */
data class ChatMessageUi(
    val message: ChatMessage,
    val streaming: Boolean = false,
    val thinkingComplete: Boolean = true,
    val toolCallInProgress: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val mode: InferenceMode = InferenceMode.FAST_ACTION,
    val busy: Boolean = false,
    val engine: EngineState = EngineState.Unloaded,
    val health: DeviceHealth? = null,
    val lastTokensPerSecond: Float? = null,
    val saveRemindersToCalendar: Boolean = true,
    /** Replies the user reported (hidden behind "Show"). */
    val reportedIds: Set<Long> = emptySet(),
    val assistant: AssistantProfile = AssistantProfile(),
    val background: ChatBackground = ChatBackground.DEFAULT,
    val backgroundDim: Float = LauncherSettings.DEFAULT_CHAT_DIM,
    /** The picked photo and its version (for the image cache), when there is one. */
    val backgroundFile: File? = null,
    val backgroundVersion: Long? = null,
    val thinkByDefault: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val agent: ChatAgent,
    private val llm: LlmEngine,
    private val chatRepository: ChatRepository,
    deviceResources: DeviceResources,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val fileRepository: FileRepository,
    private val reports: ContentReportRepository,
    sharedContent: SharedContentStore,
    private val backgroundStore: ChatBackgroundStore,
) : ViewModel() {

    /** The message field. Owned here and edited synchronously by the field (no cursor lag). */
    val input = TextFieldState()

    private val _shared = MutableStateFlow<SharedPayload?>(null)
    /** Text or a PDF shared to the launcher, waiting for the user to pick what to do with it. */
    val shared: StateFlow<SharedPayload?> = _shared.asStateFlow()

    private val _notices = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the snackbar (e.g. "Moved Foo to Private Space"). */
    val notices: Flow<String> = _notices.receiveAsFlow()

    private val mode = MutableStateFlow(InferenceMode.FAST_ACTION)

    /** RAM / thermal snapshot for the live status indicator, refreshed every 2 s while visible. */
    private val health: Flow<DeviceHealth> = flow {
        while (true) {
            emit(deviceResources.snapshot())
            delay(2_000)
        }
    }.flowOn(Dispatchers.IO)

    private val messages: Flow<List<ChatMessageUi>> = combine(
        chatRepository.observeMessages(ChatRepository.DEFAULT_CONVERSATION),
        agent.streaming,
    ) { stored, streaming ->
        stored.map { message ->
            if (streaming != null && message.id == streaming.messageId) {
                ChatMessageUi(
                    message = message.copy(content = streaming.output.answer, thinking = streaming.output.thinking),
                    streaming = true,
                    thinkingComplete = streaming.output.thinkingComplete,
                    toolCallInProgress = streaming.output.toolCallInProgress,
                )
            } else {
                ChatMessageUi(message)
            }
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        combine(messages, reports.reportedMessageIds, ::Pair),
        agent.busy,
        llm.state,
        health,
        combine(mode, settingsRepository.launcherSettings, settingsRepository.modelConfig, backgroundStore.version) { m, s, c, v -> Looks(m, s, c.reasoningByDefault, v) },
    ) { (list, reported), busy, engine, h, looks ->
        val m = looks.mode
        val calendar = looks.settings.saveRemindersToCalendar
        ChatUiState(
            messages = list,
            mode = m,
            busy = busy,
            engine = engine,
            health = h,
            lastTokensPerSecond = list.lastOrNull {
                it.message.role == ChatRole.ASSISTANT && it.message.status == MessageStatus.COMPLETE && it.message.tokensPerSecond != null
            }?.message?.tokensPerSecond,
            saveRemindersToCalendar = calendar,
            reportedIds = reported,
            assistant = looks.settings.assistant,
            background = looks.settings.chatBackground,
            backgroundDim = looks.settings.chatBackgroundDim,
            backgroundFile = backgroundStore.file.takeIf { looks.photoVersion != null },
            backgroundVersion = looks.photoVersion,
            thinkByDefault = looks.thinkByDefault,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            if (settingsRepository.modelConfig.first().reasoningByDefault) mode.value = InferenceMode.REASONING
            // Load the model and pre-fill the system/tools prefix while the user types; it is freed
            // again by the idle watchdog.
            if (llm.state.value == EngineState.Unloaded) agent.warmUp()
        }
        viewModelScope.launch {
            sharedContent.pending.filterNotNull().collect { payload ->
                _shared.value = payload
                sharedContent.clear()
            }
        }
        val prompt = runCatching { savedStateHandle.toRoute<ChatRoute>().prompt }.getOrNull()
        if (!prompt.isNullOrBlank() && savedStateHandle.get<Boolean>(KEY_PROMPT_CONSUMED) != true) {
            savedStateHandle[KEY_PROMPT_CONSUMED] = true
            send(prompt)
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        agent.send(text, mode.value)
    }

    fun sendInput() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.clearText()
        send(text)
    }

    /** Re-sends the last thing the user asked (after granting a permission a tool needed). */
    fun retryLast() {
        viewModelScope.launch {
            val last = chatRepository.observeMessages(ChatRepository.DEFAULT_CONVERSATION).first()
                .lastOrNull { it.role == ChatRole.USER }?.content ?: return@launch
            send(last)
        }
    }

    // ------------------------------------------------------------------- shared content

    fun useShared(action: ClipAction) {
        val payload = _shared.value ?: return
        // Leave room for the system prompt, tool schemas and the reply.
        val budget = if (llm.contextSize >= 4096) 6_000 else 2_000
        val text = when (payload.kind) {
            SharedPayload.Kind.PDF -> "From the PDF \"${payload.title ?: "document"}\":\n${payload.text}"
            SharedPayload.Kind.TEXT -> payload.text
        }
        val prompt = ClipboardAnalyzer.promptFor(action, text, maxChars = budget) ?: return
        _shared.value = null
        send(prompt)
    }

    fun dismissShared() {
        _shared.value = null
    }

    // ------------------------------------------------------------------ card actions

    fun openApp(entry: UnusedAppEntry) {
        viewModelScope.launch {
            val app = appRepository.apps.first().firstOrNull { it.componentKey == entry.componentKey }
                ?: appRepository.apps.first().firstOrNull { it.packageName == entry.packageName }
            if (app == null || !appRepository.launch(app)) _notices.send("${entry.label} could not be opened")
        }
    }

    fun hideApp(entry: UnusedAppEntry) {
        viewModelScope.launch {
            appRepository.setHidden(entry.componentKey, true)
            _notices.send("Moved ${entry.label} to Private Space")
        }
    }

    fun uninstallApp(entry: UnusedAppEntry) = appRepository.requestUninstall(entry.packageName)

    /** Feeds the "files I opened" history (query_files_by_activity, opened_or_clicked). */
    fun recordFileAccess(file: FileCardEntry, accessType: String) {
        viewModelScope.launch {
            fileRepository.recordAccess(
                FileEntry(
                    uri = file.uri,
                    name = file.name,
                    mimeType = file.mime,
                    sizeBytes = file.sizeBytes,
                    modifiedAtMillis = file.modifiedAt,
                    addedAtMillis = null,
                    relativePath = file.path,
                    isDownload = file.path?.startsWith("Download", ignoreCase = true) == true,
                ),
                accessType,
            )
        }
    }

    /** Without a report form in this build, "Send report" opens an email draft instead. */
    val reportsOpenEmail: Boolean = ReportFormTemplate.parse(BuildConfig.REPORT_FORM_URL) == null && BuildConfig.SUPPORT_EMAIL.isNotBlank()

    private val _emailReports = Channel<ReportOutcome.Email>(Channel.BUFFERED)
    /** Reports to finish in the email app (no in-app report channel configured in this build). */
    val emailReports: Flow<ReportOutcome.Email> = _emailReports.receiveAsFlow()

    /** Reports an assistant reply (Google Play AI-content policy). Sends only what the dialog listed. */
    fun report(messageId: Long, reason: ReportReason, details: String?, includeQuestion: Boolean) {
        viewModelScope.launch {
            val history = chatRepository.observeMessages(ChatRepository.DEFAULT_CONVERSATION).first()
            val index = history.indexOfFirst { it.id == messageId }
            val reply = history.getOrNull(index) ?: return@launch
            val question = if (includeQuestion) history.take(index).lastOrNull { it.role == ChatRole.USER }?.content else null
            val model = settingsRepository.modelConfig.first().activeModelId
            val flavor = if (BuildConfig.PLAY_BUILD) "play" else "full"
            val outcome = reports.submit(
                ContentReport(
                    messageId = messageId,
                    reason = reason,
                    details = details?.trim()?.takeIf { it.isNotEmpty() },
                    question = question,
                    reply = reply.content,
                    appInfo = "AI Launcher ${BuildConfig.VERSION_NAME} ($flavor) · $model",
                ),
            )
            when (outcome) {
                ReportOutcome.Queued -> _notices.send("Thanks. Your report will be sent to the developer")
                is ReportOutcome.Email -> _emailReports.send(outcome)
                ReportOutcome.SavedOnly -> _notices.send("Reply hidden. Reporting isn't set up in this build")
            }
        }
    }

    /** A folder picked from a file tool's "Choose folders" button (Google Play build); asks again. */
    fun addSearchFolder(uri: String) {
        viewModelScope.launch {
            if (fileRepository.addSearchFolder(uri)) retryLast() else _notices.send("That folder can't be searched")
        }
    }

    fun enableNotificationDigest() {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(notificationDigestEnabled = true) } }
    }

    fun showMessage(text: String) {
        _notices.trySend(text)
    }

    fun stop() = agent.stop()

    fun toggleReasoning() {
        mode.value = if (mode.value == InferenceMode.REASONING) InferenceMode.FAST_ACTION else InferenceMode.REASONING
    }

    fun confirmTool(messageId: Long, invocationId: String) = agent.confirmTool(messageId, invocationId)

    fun cancelTool(messageId: Long, invocationId: String) = agent.cancelTool(messageId, invocationId)

    fun clearChat() = agent.clearConversation()

    fun loadModel() {
        viewModelScope.launch { llm.preload() }
    }

    // ------------------------------------------------------------------- customization

    fun setBackground(background: ChatBackground) {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(chatBackground = background) } }
    }

    fun setBackgroundDim(dim: Float) {
        viewModelScope.launch { settingsRepository.updateLauncherSettings { it.copy(chatBackgroundDim = dim.coerceIn(0f, 0.9f)) } }
    }

    /** A photo from the photo picker becomes the chat background. */
    fun importBackground(uri: Uri) {
        viewModelScope.launch {
            if (backgroundStore.import(uri)) {
                settingsRepository.updateLauncherSettings { it.copy(chatBackground = ChatBackground.PHOTO) }
            } else {
                _notices.send("That picture couldn't be opened")
            }
        }
    }

    fun saveAssistant(profile: AssistantProfile, thinkByDefault: Boolean) {
        viewModelScope.launch {
            val clean = profile.copy(
                name = profile.name.trim().take(AssistantProfile.MAX_NAME),
                instructions = profile.instructions.trim().take(AssistantProfile.MAX_INSTRUCTIONS),
            )
            settingsRepository.updateLauncherSettings { it.copy(assistant = clean) }
            settingsRepository.updateModelConfig { it.copy(reasoningByDefault = thinkByDefault) }
            mode.value = if (thinkByDefault) InferenceMode.REASONING else InferenceMode.FAST_ACTION
            _notices.send("Saved. ${clean.displayName} uses the new behaviour from your next message")
        }
    }

    /** The exact system prompt [profile] produces, for "Show the full prompt". */
    fun systemPromptFor(profile: AssistantProfile): String = agent.systemPrompt(profile)

    fun unloadModel() {
        viewModelScope.launch { llm.unload() }
    }

    /** Everything about how the chat looks and behaves, read from settings. */
    private data class Looks(val mode: InferenceMode, val settings: LauncherSettings, val thinkByDefault: Boolean, val photoVersion: Long?)

    private companion object {
        const val KEY_PROMPT_CONSUMED = "prompt_consumed"
    }
}
