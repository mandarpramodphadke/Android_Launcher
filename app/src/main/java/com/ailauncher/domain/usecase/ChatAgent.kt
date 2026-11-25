package com.ailauncher.domain.usecase

import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.action.ActionResult
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.ai.ModelOutputParser
import com.ailauncher.domain.ai.ParsedOutput
import com.ailauncher.domain.ai.PromptJson
import com.ailauncher.domain.ai.PromptMessage
import com.ailauncher.domain.ai.PromptToolCall
import com.ailauncher.domain.ai.QuickCommandParser
import com.ailauncher.domain.ai.Qwen3PromptBuilder
import com.ailauncher.domain.ai.RawToolCall
import com.ailauncher.domain.ai.SystemPrompts
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.ai.ToolDefinitions
import com.ailauncher.domain.ai.ToolRouter
import com.ailauncher.domain.ai.ToolRouting
import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.GenerationStats
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.LlmPriority
import com.ailauncher.domain.model.LlmException
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.StopReason
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.ToolStatus
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant's agent loop.
 *
 * ```
 * user text ──► QuickCommandParser ──match──► tool card (no LLM)
 *     │
 *     └─► Qwen3 (ChatML + tools) ──stream──► ModelOutputParser ──► answer / thinking / <tool_call>
 *                                                                    │
 *            ToolRouter validates ◄──────────────────────────────────┘
 *              ├─ read-only (get_today_agenda): run now, feed <tool_response> back, generate again
 *              └─ side effects (alarm, reminder, launch …): inline confirmation card
 * ```
 *
 * Work runs in the application scope, so a reply keeps streaming if the chat screen is closed.
 */
@Singleton
class ChatAgent @Inject constructor(
    private val llm: LlmEngine,
    private val chatRepository: ChatRepository,
    private val actionExecutor: ActionExecutor,
    private val settingsRepository: SettingsRepository,
    private val folderRepository: FolderRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** In-memory view of the reply being streamed; persisted to Room once complete. */
    data class StreamingReply(val messageId: Long, val output: ParsedOutput)

    private val toolRouter = ToolRouter()
    private val quickCommands = QuickCommandParser()
    private val mutex = Mutex()

    @Volatile
    private var activeJob: Job? = null

    private val _streaming = MutableStateFlow<StreamingReply?>(null)
    val streaming: StateFlow<StreamingReply?> = _streaming.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The assistant's name, tone and instructions, read from settings before each reply. */
    @Volatile
    private var profile = AssistantProfile()

    fun send(
        text: String,
        mode: InferenceMode,
        conversationId: String = ChatRepository.DEFAULT_CONVERSATION,
    ): Job {
        val trimmed = text.trim()
        val job = scope.launch {
            mutex.withLock {
                _busy.value = true
                try {
                    chatRepository.insert(newMessage(conversationId, ChatRole.USER, trimmed, mode))
                    val quick = quickCommands.parse(trimmed)
                    if (quick != null) handleQuickCommand(conversationId, quick, mode) else runAgentLoop(conversationId, mode)
                } finally {
                    _streaming.value = null
                    _busy.value = false
                }
            }
        }
        activeJob = job
        return job
    }

    /** Stops the current reply; the partial answer is kept and marked as stopped. */
    fun stop() {
        llm.stop()
        activeJob?.cancel()
    }

    fun confirmTool(messageId: Long, invocationId: String) {
        scope.launch {
            mutex.withLock {
                val invocation = chatRepository.message(messageId)?.toolCalls?.firstOrNull { it.id == invocationId }
                    ?: return@withLock
                if (invocation.status == ToolStatus.AWAITING_CONFIRMATION) runInvocation(messageId, invocation)
            }
        }
    }

    fun cancelTool(messageId: Long, invocationId: String) {
        scope.launch {
            mutex.withLock {
                val invocation = chatRepository.message(messageId)?.toolCalls?.firstOrNull { it.id == invocationId }
                    ?: return@withLock
                if (invocation.status == ToolStatus.AWAITING_CONFIRMATION) {
                    chatRepository.updateToolInvocation(
                        messageId,
                        invocation.copy(status = ToolStatus.CANCELLED, resultMessage = "Cancelled"),
                    )
                }
            }
        }
    }

    /**
     * Pre-fills the KV cache with the static system prompt + tool schemas (~1k tokens) at
     * background priority, so the first real request only evaluates the user's turn. A foreground
     * request pre-empts it and still reuses every batch that finished.
     */
    fun warmUp() {
        if (_busy.value) return
        scope.launch {
            profile = settingsRepository.launcherSettings.first().assistant
            val prefix = Qwen3PromptBuilder.build(
                messages = listOf(PromptMessage(PromptMessage.Role.SYSTEM, SystemPrompts.assistant(profile))),
                tools = ToolDefinitions.forContext(llm.contextSize),
                addGenerationPrompt = false,
            )
            try {
                llm.generate(prefix, GenerationParams.fastAction(maxTokens = 0), LlmPriority.BACKGROUND).collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Pre-empted, no model yet, low memory, too hot: warm-up is best effort.
            }
        }
    }

    fun clearConversation(conversationId: String = ChatRepository.DEFAULT_CONVERSATION) {
        stop()
        scope.launch { mutex.withLock { chatRepository.clear(conversationId) } }
    }

    // ------------------------------------------------------------------------------------------ //

    private suspend fun handleQuickCommand(conversationId: String, command: ToolCommand, mode: InferenceMode) {
        val settings = settingsRepository.launcherSettings.first()
        val invocation = invocationFor(command, toolRouter.canonicalArguments(command), toolRouter.summarize(command), settings)
        val id = chatRepository.insert(
            newMessage(conversationId, ChatRole.ASSISTANT, "", mode).copy(toolCalls = listOf(invocation)),
        )
        if (invocation.status != ToolStatus.RUNNING) return
        val done = runInvocation(id, invocation)
        if (command.isReadOnly && done.status == ToolStatus.SUCCEEDED && modelUsable()) {
            runAgentLoop(conversationId, mode)
        }
    }

    private suspend fun runAgentLoop(conversationId: String, mode: InferenceMode) {
        repeat(MAX_TOOL_ROUNDS) {
            val reply = generateReply(conversationId, mode) ?: return
            val autoRun = reply.toolCalls.filter { it.status == ToolStatus.RUNNING }
            if (autoRun.isEmpty()) return
            var feedBack = false
            for (invocation in autoRun) {
                val done = runInvocation(reply.id, invocation)
                val command = (toolRouter.route(RawToolCall(done.name, done.resolved ?: done.arguments)) as? ToolRouting.Valid)?.command
                if (command?.isReadOnly == true) feedBack = true
            }
            if (!feedBack) return
        }
    }

    /** Generates one assistant turn. Returns null if it failed or was cancelled. */
    private suspend fun generateReply(conversationId: String, mode: InferenceMode): ChatMessage? {
        val settings = settingsRepository.launcherSettings.first()
        profile = settings.assistant
        val history = chatRepository.messages(conversationId, HISTORY_LIMIT)
        val placeholder = newMessage(conversationId, ChatRole.ASSISTANT, "", mode).copy(status = MessageStatus.STREAMING)
        val message = placeholder.copy(id = chatRepository.insert(placeholder))

        val raw = StringBuilder()
        var stats: GenerationStats? = null
        try {
            val folders = folderContext(history.lastOrNull { it.role == ChatRole.USER }?.content)
            var window = trimToContext(history, mode)
            while (true) {
                val prompt = buildPrompt(window, mode, folders)
                val params = paramsFor(mode, prompt)
                try {
                    llm.generate(prompt, params).collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                raw.append(event.text)
                                _streaming.value = StreamingReply(message.id, ModelOutputParser.parse(raw.toString()))
                            }
                            is GenerationEvent.Completed -> stats = event.stats
                        }
                    }
                    break
                } catch (e: LlmException.ContextOverflow) {
                    val shorter = dropOldestTurn(window)
                    if (shorter.size == window.size || raw.isNotEmpty()) throw e
                    window = shorter
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val parsed = ModelOutputParser.parse(raw.toString())
                chatRepository.update(
                    message.copy(
                        content = parsed.answer,
                        thinking = parsed.thinking,
                        status = MessageStatus.CANCELLED,
                    ),
                )
            }
            throw e
        } catch (e: LlmException) {
            chatRepository.update(message.copy(content = friendlyError(e), status = MessageStatus.ERROR))
            return null
        } catch (e: Exception) {
            chatRepository.update(
                message.copy(content = "Something went wrong: ${e.message ?: e.javaClass.simpleName}", status = MessageStatus.ERROR),
            )
            return null
        }

        val parsed = ModelOutputParser.parse(raw.toString())
        val userText = history.lastOrNull { it.role == ChatRole.USER }?.content
        val invocations = parsed.toolCalls.map { call ->
            when (val routing = toolRouter.route(call, userText)) {
                is ToolRouting.Valid ->
                    invocationFor(routing.command, call.arguments, routing.summary, settings, resolved = toolRouter.canonicalArguments(routing.command))
                is ToolRouting.Invalid -> ToolInvocation(
                    id = newId(),
                    name = call.name,
                    arguments = call.arguments,
                    status = ToolStatus.FAILED,
                    summary = call.name,
                    resultMessage = routing.reason,
                )
            }
        }
        val cancelled = stats?.stopReason == StopReason.CANCELLED
        val content = when {
            parsed.answer.isNotEmpty() -> parsed.answer
            invocations.isEmpty() && !cancelled -> "I'm not sure how to help with that."
            else -> ""
        }
        val final = message.copy(
            content = content,
            thinking = parsed.thinking,
            toolCalls = invocations,
            status = if (cancelled) MessageStatus.CANCELLED else MessageStatus.COMPLETE,
            tokensPerSecond = stats?.tokensPerSecond,
        )
        chatRepository.update(final)
        _streaming.value = null
        return final
    }

    private suspend fun runInvocation(messageId: Long, invocation: ToolInvocation): ToolInvocation {
        val running = invocation.copy(status = ToolStatus.RUNNING)
        chatRepository.updateToolInvocation(messageId, running)
        val result = try {
            when (val routing = toolRouter.route(RawToolCall(invocation.name, invocation.resolved ?: invocation.arguments))) {
                is ToolRouting.Valid -> actionExecutor.execute(routing.command)
                is ToolRouting.Invalid -> ActionResult.fail(routing.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.fail(e.message ?: "Something went wrong")
        }
        val done = running.copy(
            status = if (result.success) ToolStatus.SUCCEEDED else ToolStatus.FAILED,
            resultMessage = result.message,
            resultData = result.data,
            displayData = result.display,
        )
        chatRepository.updateToolInvocation(messageId, done)
        return done
    }

    private fun invocationFor(
        command: ToolCommand,
        arguments: JsonObject,
        summary: String,
        settings: LauncherSettings,
        resolved: JsonObject = toolRouter.canonicalArguments(command),
    ): ToolInvocation {
        val autoRun = command.isReadOnly || (command is ToolCommand.LaunchApp && settings.autoConfirmAppLaunch)
        return ToolInvocation(
            id = newId(),
            name = command.toolName,
            arguments = arguments,
            status = if (autoRun) ToolStatus.RUNNING else ToolStatus.AWAITING_CONFIRMATION,
            summary = summary,
            resolved = resolved,
        )
    }

    // ------------------------------------------------------------------------------------------ //
    //  Prompt assembly
    // ------------------------------------------------------------------------------------------ //

    /**
     * @param folderContext appended to the latest user turn only (see [folderContext]), so earlier
     * turns render identically on every request and the KV prefix cache keeps matching.
     */
    internal fun buildPrompt(
        history: List<ChatMessage>,
        mode: InferenceMode,
        folderContext: String? = null,
        profile: AssistantProfile = this.profile,
    ): String {
        val messages = mutableListOf(PromptMessage(PromptMessage.Role.SYSTEM, SystemPrompts.assistant(profile)))
        val lastUser = history.indexOfLast { it.role == ChatRole.USER }
        for ((index, m) in history.withIndex()) {
            when (m.role) {
                ChatRole.USER -> messages += PromptMessage(
                    PromptMessage.Role.USER,
                    renderUserTurn(m, folderContext.takeIf { index == lastUser }),
                )
                ChatRole.ASSISTANT -> {
                    if (m.status == MessageStatus.ERROR || m.status == MessageStatus.STREAMING) continue
                    if (m.content.isBlank() && m.toolCalls.isEmpty()) continue
                    messages += PromptMessage(
                        role = PromptMessage.Role.ASSISTANT,
                        content = m.content,
                        reasoning = m.thinking,
                        toolCalls = m.toolCalls.map { PromptToolCall(it.name, it.arguments) },
                    )
                    m.toolCalls.forEach { messages += PromptMessage(PromptMessage.Role.TOOL, toolResponse(it)) }
                }
                ChatRole.TOOL -> Unit
            }
        }
        // The tool set depends only on n_ctx, so it is identical across turns (prefix cache friendly).
        return Qwen3PromptBuilder.build(messages, ToolDefinitions.forContext(llm.contextSize), enableThinking = mode == InferenceMode.REASONING)
    }

    private fun renderUserTurn(m: ChatMessage, folderContext: String? = null): String {
        val sentAt = Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault())
        val context = SystemPrompts.userTurnContext(sentAt) + folderContext?.let { " $it" }.orEmpty()
        val base = m.content + "\n\n" + context
        return if (m.mode == InferenceMode.FAST_ACTION) base + " " + Qwen3PromptBuilder.NO_THINK else base
    }

    /**
     * The user's custom folders, only when the request mentions one ("open my Work folder"):
     * they are not in the static system prompt, so editing a folder never invalidates the
     * prefix cache, and ordinary requests pay no extra tokens.
     */
    private suspend fun folderContext(userText: String?): String? {
        val text = userText?.lowercase() ?: return null
        val folders = runCatching { folderRepository.folders.first() }.getOrDefault(emptyList())
        if (folders.isEmpty()) return null
        val mentioned = "folder" in text || folders.any { it.name.lowercase() in text }
        if (!mentioned) return null
        return SystemPrompts.folderContext(folders.map { it.name to it.description })
    }

    private fun toolResponse(invocation: ToolInvocation): String {
        val data = when (invocation.status) {
            ToolStatus.SUCCEEDED -> invocation.resultData ?: buildJsonObject {
                put("status", "success")
                put("message", invocation.resultMessage.orEmpty())
            }
            ToolStatus.FAILED -> buildJsonObject {
                put("status", "error")
                put("message", invocation.resultMessage.orEmpty())
            }
            ToolStatus.CANCELLED -> buildJsonObject {
                put("status", "cancelled")
                put("message", "The user cancelled this action.")
            }
            ToolStatus.AWAITING_CONFIRMATION -> buildJsonObject {
                put("status", "pending")
                put("message", "Waiting for the user to confirm.")
            }
            ToolStatus.RUNNING -> buildJsonObject { put("status", "running") }
        }
        return PromptJson.encode(data)
    }

    /** Drops the oldest turns until the prompt (≈3 chars/token) leaves room for the reply. */
    private fun trimToContext(history: List<ChatMessage>, mode: InferenceMode): List<ChatMessage> {
        val reserve = minOf(GenerationParams.forMode(mode).maxTokens, llm.contextSize / 2)
        var window = history
        while (window.size > 1 && estimateTokens(buildPrompt(window, mode)) + reserve > llm.contextSize) {
            val shorter = dropOldestTurn(window)
            if (shorter.size == window.size) break
            window = shorter
        }
        return window
    }

    private fun dropOldestTurn(window: List<ChatMessage>): List<ChatMessage> {
        if (window.size <= 1) return window
        val rest = window.drop(1)
        val nextUser = rest.indexOfFirst { it.role == ChatRole.USER }
        return if (nextUser <= 0) rest else rest.drop(nextUser)
    }

    private fun paramsFor(mode: InferenceMode, prompt: String): GenerationParams {
        val base = GenerationParams.forMode(mode)
        val room = (llm.contextSize - estimateTokens(prompt) - 16).coerceAtLeast(64)
        return base.copy(maxTokens = minOf(base.maxTokens, room))
    }

    private fun estimateTokens(text: String) = text.length / 3

    private fun modelUsable(): Boolean = when (llm.state.value) {
        EngineState.NoModel, is EngineState.Unsupported -> false
        else -> true
    }

    private fun friendlyError(e: LlmException): String = when (e) {
        is LlmException.ModelNotAvailable ->
            "The on-device AI model isn't downloaded yet. Open Settings ▸ AI model to get it (≈1.1 GB, one time). " +
                "Quick commands like \"open Spotify\" or \"timer 5 min\" already work."
        else -> e.message ?: "The AI model failed to respond."
    }

    private fun newMessage(conversationId: String, role: ChatRole, content: String, mode: InferenceMode) = ChatMessage(
        id = 0,
        conversationId = conversationId,
        role = role,
        content = content,
        thinking = null,
        toolCalls = emptyList(),
        status = MessageStatus.COMPLETE,
        mode = mode,
        tokensPerSecond = null,
        createdAt = System.currentTimeMillis(),
    )

    private fun newId() = UUID.randomUUID().toString().take(12)

    private companion object {
        const val MAX_TOOL_ROUNDS = 3
        const val HISTORY_LIMIT = 40
    }
}
