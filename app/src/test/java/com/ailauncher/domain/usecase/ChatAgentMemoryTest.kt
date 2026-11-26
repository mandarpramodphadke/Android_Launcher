package com.ailauncher.domain.usecase

import com.ailauncher.domain.ai.MemoryAction
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.model.AssistantFeatures
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ToolStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** The real agent with ASSISTANT_MEMORY: what reaches the prompt, and what "remember …" does. */
class ChatAgentMemoryTest {

    private val memories = ListMemories()
    private val settings = MutableSettings()
    private val executor = RecordingExecutor()

    private fun agent(llm: ScriptedLlm = ScriptedLlm(), memoryInBuild: Boolean = true) = ChatAgent(
        llm = llm,
        chatRepository = InMemoryChat(),
        actionExecutor = executor,
        settingsRepository = settings,
        folderRepository = NoFolders(),
        scope = CoroutineScope(Dispatchers.Unconfined),
        features = AssistantFeatures(betterAnswers = true, memory = memoryInBuild),
        memoryRepository = memories,
    )

    private val sentAt = ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun ask(text: String) = listOf(ChatMessage(1, "default", ChatRole.USER, text, null, emptyList(), MessageStatus.COMPLETE, InferenceMode.FAST_ACTION, null, sentAt))

    /** Prompts with saved memories, for replay against the real model with tools/llm-host-harness. */
    @Test
    fun exportsMemoryPrompts() = runBlocking {
        val agent = agent()
        listOf("I'm vegetarian", "My wife's birthday is 4 March", "My car is parked on level 3 of the mall", "My daughter Anya is 8").forEach { memories.remember(it) }
        val dir = File("build/prompt-fixtures").apply { mkdirs() }
        mapOf(
            "birthday" to "When is my wife's birthday?",
            "parking" to "Where did I park?",
            "meal" to "Give me a simple 3-day meal plan",
            "gift" to "What should I get my daughter for her birthday?",
            "ram" to "What's the difference between RAM and storage on a phone?",
            "alarm" to "set an alarm for 6:45 tomorrow morning",
            "todo" to "add pick up dry cleaning to my errands list",
            "save_own_words" to "I take my coffee with oat milk, keep that in mind for later",
            "save_dont_forget" to "My gym locker is number 112. Please don't forget it.",
            "no_save" to "I had a long day at work",
        ).forEach { (name, text) -> File(dir, "memory_$name.txt").writeText(agent.buildPrompt(ask(text), InferenceMode.FAST_ACTION)) }
    }

    @Test
    fun savedMemoriesReachThePromptAndTheToolIsOffered() = runBlocking {
        val agent = agent()
        memories.remember("my wife's birthday is 4 March")
        memories.remember("locker code is 4512")
        val prompt = agent.buildPrompt(ask("When is my wife's birthday?"), InferenceMode.FAST_ACTION)
        assertThat(prompt).contains(
            "What the user asked you to remember (use it when it helps; don't bring it up otherwise):\n- Locker code is 4512\n- My wife's birthday is 4 March\n\n# Tools",
        )
        assertThat(prompt).contains("\"name\": \"manage_memory\"")
        // What the customize sheet shows is what the model gets.
        assertThat(prompt).startsWith("<|im_start|>system\n" + agent.systemPrompt(LauncherSettings().assistant) + "\n\n# Tools")

        // Switched off in Settings: no memories, no tool.
        settings.updateLauncherSettings { it.copy(assistantMemory = false) }
        val off = agent.buildPrompt(ask("When is my wife's birthday?"), InferenceMode.FAST_ACTION)
        assertThat(off).doesNotContain("asked you to remember")
        assertThat(off).doesNotContain("manage_memory")
    }

    @Test
    fun rememberSavesAtOnceWithoutTheModelAndForgetAsksFirst() = runBlocking {
        val llm = ScriptedLlm()
        val chat = InMemoryChat()
        val agent = ChatAgent(
            llm = llm,
            chatRepository = chat,
            actionExecutor = executor,
            settingsRepository = settings,
            folderRepository = NoFolders(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            features = AssistantFeatures(betterAnswers = true, memory = true),
            memoryRepository = memories,
        )
        agent.send("Remember that my car is parked on level 3", InferenceMode.FAST_ACTION).join()
        assertThat(llm.prompts).isEmpty()
        assertThat(executor.commands).containsExactly(ToolCommand.ManageMemory(MemoryAction.SAVE, "my car is parked on level 3"))
        assertThat(chat.messages.last().toolCalls.single().status).isEqualTo(ToolStatus.SUCCEEDED)

        agent.send("forget my car", InferenceMode.FAST_ACTION).join()
        assertThat(chat.messages.last().toolCalls.single().status).isEqualTo(ToolStatus.AWAITING_CONFIRMATION)
        assertThat(executor.commands).hasSize(1)
    }

    @Test
    fun withMemorySwitchedOffRememberGoesToTheModel() = runBlocking {
        settings.updateLauncherSettings { it.copy(assistantMemory = false) }
        val llm = ScriptedLlm("Sure.")
        agent(llm).send("Remember that my car is parked on level 3", InferenceMode.FAST_ACTION).join()
        assertThat(llm.prompts).hasSize(1)
        assertThat(executor.commands).isEmpty()
    }

    @Test
    fun theModelCannotSaveMemoriesWhenTheyAreOff() = runBlocking {
        settings.updateLauncherSettings { it.copy(assistantMemory = false) }
        val llm = ScriptedLlm(
            "<tool_call>\n{\"name\": \"manage_memory\", \"arguments\": {\"action\": \"save\", \"text\": \"likes jazz\"}}\n</tool_call>",
            "Noted.",
        )
        val chat = InMemoryChat()
        ChatAgent(
            llm = llm,
            chatRepository = chat,
            actionExecutor = executor,
            settingsRepository = settings,
            folderRepository = NoFolders(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            features = AssistantFeatures(betterAnswers = true, memory = true),
            memoryRepository = memories,
        ).send("I really like jazz", InferenceMode.FAST_ACTION).join()
        assertThat(executor.commands).isEmpty()
        assertThat(memories.all()).isEmpty()
        // Answered like a made-up tool: the model replies in text, and no failed card is left.
        assertThat(chat.messages.last().content).isEqualTo("Noted.")
        assertThat(chat.messages.last().toolCalls).isEmpty()
    }
}
