package com.ailauncher.domain.usecase

import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.action.ActionResult
import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.ai.SystemPrompts
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.ai.ToolNames
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.LlmPriority
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.ToolStatus
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Verifies the prompt the assistant actually sends (system prompt, tools, per-turn time context,
 * /no_think soft switch, tool responses). Also exports the prompts to
 * `app/build/prompt-fixtures/` so they can be replayed against the real GGUF with
 * tools/llm-host-harness.
 */
class ChatAgentPromptTest {

    private val agent = agentWithContext(4096)

    private fun agentWithContext(nCtx: Int) = ChatAgent(
        llm = FakeLlm(nCtx),
        chatRepository = FakeChatRepository(),
        actionExecutor = object : ActionExecutor {
            override suspend fun execute(command: ToolCommand) = ActionResult.ok("ok")
        },
        settingsRepository = FakeSettings(),
        folderRepository = FakeFolders(),
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val sentAt = ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun message(id: Long, role: ChatRole, content: String, mode: InferenceMode = InferenceMode.FAST_ACTION, tools: List<ToolInvocation> = emptyList()) =
        ChatMessage(id, "default", role, content, null, tools, MessageStatus.COMPLETE, mode, null, sentAt)

    @Test
    fun fastModePromptHasToolsTimeContextAndNoThinkSwitch() {
        val prompt = agent.buildPrompt(listOf(message(1, ChatRole.USER, "Wake me up at 6:45 tomorrow")), InferenceMode.FAST_ACTION)
        assertThat(prompt).startsWith("<|im_start|>system\n" + SystemPrompts.assistant + "\n\n# Tools")
        ToolDefinitions().names.forEach { assertThat(prompt).contains("\"name\": \"$it\"") }
        assertThat(prompt).contains("Wake me up at 6:45 tomorrow\n\n[Local time: Monday, 23 November 2026, 10:15] /no_think<|im_end|>")
        assertThat(prompt).endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n")
        export("fast_alarm", prompt)
    }

    @Test
    fun reasoningModeLeavesThinkingOn() {
        val prompt = agent.buildPrompt(listOf(message(1, ChatRole.USER, "Plan my week", InferenceMode.REASONING)), InferenceMode.REASONING)
        assertThat(prompt).doesNotContain("/no_think")
        assertThat(prompt).endsWith("<|im_start|>assistant\n")
        export("reasoning_plan", prompt)
    }

    @Test
    fun executedToolsAreFedBackAsToolResponses() {
        val agenda = ToolInvocation(
            id = "t1",
            name = ToolNames.GET_TODAY_AGENDA,
            arguments = buildJsonObject {},
            status = ToolStatus.SUCCEEDED,
            resultData = buildJsonObject {
                put("date", "Monday, 23 November 2026")
                put("events", "Standup 10:30, Dentist 16:00")
            },
        )
        val history = listOf(
            message(1, ChatRole.USER, "Summarize my day"),
            message(2, ChatRole.ASSISTANT, "", tools = listOf(agenda)),
        )
        val prompt = agent.buildPrompt(history, InferenceMode.FAST_ACTION)
        assertThat(prompt).contains("<tool_call>\n{\"name\": \"get_today_agenda\", \"arguments\": {}}\n</tool_call><|im_end|>")
        assertThat(prompt).contains("<|im_start|>user\n<tool_response>\n{\"date\": \"Monday, 23 November 2026\", \"events\": \"Standup 10:30, Dentist 16:00\"}\n</tool_response><|im_end|>")
        export("agenda_followup", prompt)
    }

    @Test
    fun exportsOtherReplayPrompts() {
        export("fast_launch", agent.buildPrompt(listOf(message(1, ChatRole.USER, "can you open spotify for me")), InferenceMode.FAST_ACTION))
        export("fast_reminder", agent.buildPrompt(listOf(message(1, ChatRole.USER, "remind me to call mom tomorrow evening at 7")), InferenceMode.FAST_ACTION))
        export("fast_chat", agent.buildPrompt(listOf(message(1, ChatRole.USER, "What is the boiling point of water in Fahrenheit?")), InferenceMode.FAST_ACTION))
        export("fast_flashlight", agent.buildPrompt(listOf(message(1, ChatRole.USER, "it's dark in here, I need some light")), InferenceMode.FAST_ACTION))
    }

    /** Prompts for the v2 tools, phrased so the deterministic quick-command parser does not catch them. */
    @Test
    fun exportsLauncherToolReplayPrompts() {
        val prompts = mapOf(
            "tool_todo" to "I need to pick up the dry cleaning tomorrow at 5pm, put it on my errands list as high priority",
            "tool_planner" to "block 2 hours tomorrow at 3pm for deep work",
            "tool_day_plan" to "when am I free tomorrow?",
            "tool_unused_apps" to "which apps haven't I touched in about two months?",
            "tool_largest_files" to "my storage is full, show me the 10 biggest videos",
            "tool_downloads" to "show me the documents I downloaded since last monday",
            "tool_stale_files" to "find photos I haven't touched in half a year",
            "tool_notifications" to "anything important in my notifications today?",
            "tool_contact" to "tell mom on whatsapp that I'll be 10 minutes late",
            "tool_call" to "please ring Alex Johnson",
        )
        prompts.forEach { (name, text) -> export(name, agent.buildPrompt(listOf(message(1, ChatRole.USER, text)), InferenceMode.FAST_ACTION)) }
    }

    @Test
    fun smallContextsGetTheCompactToolSet() {
        val small = agentWithContext(2048).buildPrompt(listOf(message(1, ChatRole.USER, "add milk to my list")), InferenceMode.FAST_ACTION)
        assertThat(small).contains("\"name\": \"manage_todo\"")
        assertThat(small).doesNotContain("\"name\": \"query_files_by_activity\"")
        assertThat(small).doesNotContain("\"name\": \"contact_action\"")
        val full = agent.buildPrompt(listOf(message(1, ChatRole.USER, "add milk to my list")), InferenceMode.FAST_ACTION)
        assertThat(full.length).isGreaterThan(small.length)
        // The static prefixes, for measuring their token cost with llm_host_harness.
        export("prefix_full", full.substringBefore("<|im_start|>user"))
        export("prefix_compact", small.substringBefore("<|im_start|>user"))
    }

    @Test
    fun folderContextOnlyOnTheLatestUserTurn() {
        val folders = SystemPrompts.folderContext(listOf("Work" to "apps for my job", "Travel" to null))
        assertThat(folders).isEqualTo("[Custom folders: Work (apps for my job), Travel]")
        val history = listOf(
            message(1, ChatRole.USER, "hi"),
            message(2, ChatRole.ASSISTANT, "Hello!"),
            message(3, ChatRole.USER, "open my work folder"),
        )
        val prompt = agent.buildPrompt(history, InferenceMode.FAST_ACTION, folders)
        assertThat(prompt).contains("hi\n\n[Local time: Monday, 23 November 2026, 10:15] /no_think<|im_end|>")
        assertThat(prompt).contains("open my work folder\n\n[Local time: Monday, 23 November 2026, 10:15] $folders /no_think<|im_end|>")
        // The static prefix (system prompt + tools) is identical with and without folder context.
        val plain = agent.buildPrompt(history, InferenceMode.FAST_ACTION)
        val prefix = plain.substringBefore("<|im_start|>user")
        assertThat(prompt).startsWith(prefix)
        export("tool_folder", prompt)
    }

    private fun export(name: String, prompt: String) {
        val dir = File("build/prompt-fixtures").apply { mkdirs() }
        File(dir, "$name.txt").writeText(prompt)
    }

    private class ToolDefinitions {
        val names = listOf(
            ToolNames.SET_ALARM, ToolNames.SET_TIMER, ToolNames.CREATE_REMINDER, ToolNames.LAUNCH_APP,
            ToolNames.TOGGLE_SETTING, ToolNames.SORT_APPS, ToolNames.GET_TODAY_AGENDA,
            ToolNames.MANAGE_TODO, ToolNames.MANAGE_PLANNER, ToolNames.LIST_UNUSED_APPS, ToolNames.FIND_LARGEST_FILES,
            ToolNames.QUERY_FILES_BY_ACTIVITY, ToolNames.SUMMARIZE_NOTIFICATIONS, ToolNames.CONTACT_ACTION,
        )
    }

    private class FakeFolders : FolderRepository {
        override val folders: Flow<List<CustomFolder>> = flowOf(emptyList())
        override suspend fun get(id: Long): CustomFolder? = null
        override suspend fun create(name: String, description: String?, accent: FolderAccent, packageNames: List<String>) = 1L
        override suspend fun update(id: Long, name: String, description: String?, accent: FolderAccent) = Unit
        override suspend fun setApps(id: Long, packageNames: List<String>) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun findByName(query: String): CustomFolder? = null
    }

    private class FakeLlm(override val contextSize: Int) : LlmEngine {
        override val state: StateFlow<EngineState> = MutableStateFlow(EngineState.Unloaded)
        override fun generate(prompt: String, params: GenerationParams, priority: LlmPriority): Flow<GenerationEvent> = emptyFlow()
        override fun stop() = Unit
        override suspend fun preload() = Result.success(Unit)
        override suspend fun unload() = Unit
        override suspend fun countTokens(text: String): Int? = null
        override fun onTrimMemory(level: Int) = Unit
    }

    private class FakeChatRepository : ChatRepository {
        override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = emptyFlow()
        override suspend fun messages(conversationId: String, limit: Int) = emptyList<ChatMessage>()
        override suspend fun insert(message: ChatMessage) = 1L
        override suspend fun update(message: ChatMessage) = Unit
        override suspend fun updateToolInvocation(messageId: Long, invocation: ToolInvocation) = Unit
        override suspend fun message(id: Long): ChatMessage? = null
        override suspend fun clear(conversationId: String) = Unit
        override suspend fun recoverInterruptedMessages() = Unit
    }

    private class FakeSettings : SettingsRepository {
        override val launcherSettings: Flow<LauncherSettings> = flowOf(LauncherSettings())
        override val modelConfig: Flow<ModelConfig> = flowOf(ModelConfig())
        override suspend fun updateLauncherSettings(transform: (LauncherSettings) -> LauncherSettings) = Unit
        override suspend fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) = Unit
    }
}
