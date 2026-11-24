package com.ailauncher.presentation.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ailauncher.data.llm.DeviceResources
import com.ailauncher.data.share.SharedPayload
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.clipboard.ClipAction
import com.ailauncher.domain.clipboard.ClipboardAnalyzer
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.FileRepository
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.DeviceHealth
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.usecase.ChatAgent
import com.ailauncher.presentation.navigation.ChatRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    sharedContent: SharedContentStore,
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
        messages,
        agent.busy,
        llm.state,
        health,
        combine(mode, settingsRepository.launcherSettings) { m, s -> m to s.saveRemindersToCalendar },
    ) { list, busy, engine, h, (m, calendar) ->
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

    fun unloadModel() {
        viewModelScope.launch { llm.unload() }
    }

    private companion object {
        const val KEY_PROMPT_CONSUMED = "prompt_consumed"
    }
}
