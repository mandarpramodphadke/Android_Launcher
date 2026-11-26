package com.ailauncher.domain.usecase

import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.action.ActionResult
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.memory.MemoryText
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.GenerationStats
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.LlmPriority
import com.ailauncher.domain.model.Memory
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.StopReason
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.MemoryRepository
import com.ailauncher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** Test doubles for running the real [ChatAgent] without a model, Room or Android. */

/** Answers each prompt with the next scripted output and records the prompts. */
internal class ScriptedLlm(vararg outputs: String, override val contextSize: Int = 4096) : LlmEngine {
    private val queue = ArrayDeque(outputs.toList())
    val prompts = mutableListOf<String>()
    override val state: StateFlow<EngineState> = MutableStateFlow(EngineState.Unloaded)
    override fun generate(prompt: String, params: GenerationParams, priority: LlmPriority): Flow<GenerationEvent> = flow {
        prompts += prompt
        emit(GenerationEvent.Token(queue.removeFirst()))
        emit(GenerationEvent.Completed(GenerationStats(StopReason.END_OF_TURN, 0, 0, 0, 0, 0)))
    }
    override fun stop() = Unit
    override suspend fun preload() = Result.success(Unit)
    override suspend fun unload() = Unit
    override suspend fun countTokens(text: String): Int? = null
    override fun onTrimMemory(level: Int) = Unit
}

/** Runs nothing; remembers which commands it was asked to run. */
internal class RecordingExecutor : ActionExecutor {
    val commands = mutableListOf<ToolCommand>()
    override suspend fun execute(command: ToolCommand): ActionResult {
        commands += command
        return ActionResult.ok("ok")
    }
}

internal class InMemoryChat : ChatRepository {
    val messages = mutableListOf<ChatMessage>()
    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = emptyFlow()
    override suspend fun messages(conversationId: String, limit: Int) = messages.toList()
    override suspend fun insert(message: ChatMessage): Long {
        val id = messages.size + 1L
        messages += message.copy(id = id)
        return id
    }
    override suspend fun update(message: ChatMessage) {
        messages.replaceAll { if (it.id == message.id) message else it }
    }
    override suspend fun updateToolInvocation(messageId: Long, invocation: ToolInvocation) {
        messages.replaceAll { m -> if (m.id == messageId) m.copy(toolCalls = m.toolCalls.map { if (it.id == invocation.id) invocation else it }) else m }
    }
    override suspend fun message(id: Long): ChatMessage? = messages.firstOrNull { it.id == id }
    override suspend fun clear(conversationId: String) = messages.clear()
    override suspend fun recoverInterruptedMessages() = Unit
}

/** Settings that a test can change while the agent runs. */
internal class MutableSettings(initial: LauncherSettings = LauncherSettings()) : SettingsRepository {
    val settings = MutableStateFlow(initial)
    override val launcherSettings: Flow<LauncherSettings> = settings
    override val modelConfig: Flow<ModelConfig> = flowOf(ModelConfig())
    override suspend fun updateLauncherSettings(transform: (LauncherSettings) -> LauncherSettings) = settings.update(transform)
    override suspend fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) = Unit
}

internal class NoFolders : FolderRepository {
    override val folders: Flow<List<CustomFolder>> = flowOf(emptyList())
    override suspend fun get(id: Long): CustomFolder? = null
    override suspend fun create(name: String, description: String?, accent: FolderAccent, packageNames: List<String>, rule: FolderRule?) = 1L
    override suspend fun update(id: Long, name: String, description: String?, accent: FolderAccent) = Unit
    override suspend fun setApps(id: Long, packageNames: List<String>) = Unit
    override suspend fun setRule(id: Long, rule: FolderRule?, packageNames: List<String>) = Unit
    override suspend fun delete(id: Long) = Unit
    override fun refreshSmartFolders() = Unit
    override suspend fun findByName(query: String): CustomFolder? = null
}

/** [MemoryRepository] in a list, with the same cleaning and matching rules as the Room one. */
internal class ListMemories : MemoryRepository {
    private val list = MutableStateFlow<List<Memory>>(emptyList())
    private var nextId = 1L
    override val memories: Flow<List<Memory>> = list
    override suspend fun all() = list.value
    override suspend fun remember(text: String): Memory? {
        val cleaned = MemoryText.clean(text) ?: return null
        list.value.firstOrNull { it.text.equals(cleaned, ignoreCase = true) }?.let { return it }
        val memory = Memory(nextId, cleaned, nextId)
        nextId++
        list.update { listOf(memory) + it }
        return memory
    }
    override suspend fun delete(id: Long) = list.update { all -> all.filterNot { it.id == id } }
    override suspend fun forget(query: String): List<Memory> {
        val gone = if (MemoryText.meansEverything(query)) list.value else MemoryText.matching(list.value, query)
        list.update { all -> all - gone.toSet() }
        return gone
    }
    override suspend fun clear() = list.update { emptyList() }
}
