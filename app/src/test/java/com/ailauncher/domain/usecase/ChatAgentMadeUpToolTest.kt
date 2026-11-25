package com.ailauncher.domain.usecase

import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.action.ActionResult
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.model.AssistantFeatures
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.GenerationStats
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.LlmPriority
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.StopReason
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.ToolStatus
import com.ailauncher.domain.repository.ChatRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The real agent with a scripted model: what reaches the chat when the model makes up a tool. */
class ChatAgentMadeUpToolTest {

    private val madeUp = "<tool_call>\n{\"name\": \"write_email\", \"arguments\": {\"to\": \"Landlord\"}}\n</tool_call>"
    private val email = "Dear landlord,\n\nThe kitchen tap is leaking. Could you send someone to fix it?\n\nThanks"

    @Test
    fun aMadeUpToolGetsAnErrorResultAndOnlyTheWrittenAnswerIsKept() = runBlocking {
        val llm = ScriptedLlm(madeUp, email)
        val chat = InMemoryChat()
        agent(llm, chat, betterAnswers = true).send("Write a short email to my landlord saying the kitchen tap is leaking", InferenceMode.FAST_ACTION).join()

        assertThat(llm.prompts).hasSize(2)
        // The second prompt is the first (so the KV cache still matches) plus the made-up call and
        // an error telling the model to answer; only the empty think block of the reply differs.
        val turn = "<|im_start|>assistant\n"
        assertThat(llm.prompts[1]).startsWith(llm.prompts[0].substringBeforeLast(turn) + turn + "<tool_call>")
        assertThat(llm.prompts[1]).contains("{\"name\": \"write_email\", \"arguments\": {\"to\": \"Landlord\"}}")
        assertThat(llm.prompts[1]).contains("There is no write_email tool. Reply to the user directly")
        val reply = chat.messages.last()
        assertThat(reply.content).isEqualTo(email)
        assertThat(reply.toolCalls).isEmpty()
    }

    @Test
    fun withTheSwitchOffTheFailedCallIsShownAsBefore() = runBlocking {
        val llm = ScriptedLlm(madeUp, email)
        val chat = InMemoryChat()
        agent(llm, chat, betterAnswers = false).send("Write a short email to my landlord saying the kitchen tap is leaking", InferenceMode.FAST_ACTION).join()

        assertThat(llm.prompts).hasSize(1)
        val reply = chat.messages.last()
        assertThat(reply.toolCalls.single().status).isEqualTo(ToolStatus.FAILED)
    }

    @Test
    fun aRealToolCallIsNotRetried() = runBlocking {
        val llm = ScriptedLlm("<tool_call>\n{\"name\": \"launch_app\", \"arguments\": {\"appName\": \"Spotify\"}}\n</tool_call>")
        val chat = InMemoryChat()
        agent(llm, chat, betterAnswers = true).send("can you open spotify for me", InferenceMode.FAST_ACTION).join()

        assertThat(llm.prompts).hasSize(1)
        assertThat(chat.messages.last().toolCalls.single().name).isEqualTo("launch_app")
    }

    private fun agent(llm: LlmEngine, chat: ChatRepository, betterAnswers: Boolean) = ChatAgent(
        llm = llm,
        chatRepository = chat,
        actionExecutor = object : ActionExecutor {
            override suspend fun execute(command: ToolCommand) = ActionResult.ok("ok")
        },
        settingsRepository = FakeSettings(),
        folderRepository = FakeFolders(),
        scope = CoroutineScope(Dispatchers.Unconfined),
        features = AssistantFeatures(betterAnswers = betterAnswers),
    )

    /** Answers each prompt with the next scripted output. */
    private class ScriptedLlm(vararg outputs: String) : LlmEngine {
        private val queue = ArrayDeque(outputs.toList())
        val prompts = mutableListOf<String>()
        override val contextSize = 4096
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

    private class InMemoryChat : ChatRepository {
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

    private class FakeSettings : SettingsRepository {
        override val launcherSettings: Flow<LauncherSettings> = flowOf(LauncherSettings())
        override val modelConfig: Flow<ModelConfig> = flowOf(ModelConfig())
        override suspend fun updateLauncherSettings(transform: (LauncherSettings) -> LauncherSettings) = Unit
        override suspend fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) = Unit
    }

    private class FakeFolders : FolderRepository {
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
}
