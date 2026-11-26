package com.ailauncher.domain.usecase

import com.ailauncher.domain.model.AssistantFeatures
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.SpokenReplies
import com.ailauncher.domain.speech.SpeechOutput
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The real agent with SPOKEN_REPLIES: which replies are read aloud, and how. */
class ChatAgentSpeechTest {

    private class RecordingSpeech : SpeechOutput {
        val spoken = mutableListOf<String>()
        var stops = 0
        override val speaking: StateFlow<Boolean> = MutableStateFlow(false)
        override fun speak(text: String) {
            spoken += text
        }
        override fun stop() {
            stops++
        }
    }

    private val speech = RecordingSpeech()

    private fun agent(llm: ScriptedLlm, mode: SpokenReplies, inBuild: Boolean = true) = ChatAgent(
        llm = llm,
        chatRepository = InMemoryChat(),
        actionExecutor = RecordingExecutor(),
        settingsRepository = MutableSettings(LauncherSettings(spokenReplies = mode)),
        folderRepository = NoFolders(),
        scope = CoroutineScope(Dispatchers.Unconfined),
        features = AssistantFeatures(betterAnswers = true, spokenReplies = inBuild),
        speech = speech,
    )

    @Test
    fun afterVoiceReadsOnlyTheRepliesToSpokenMessages() = runBlocking {
        val agent = agent(ScriptedLlm("**Paris** is the capital of France.", "It is 8,849 m tall."), SpokenReplies.AFTER_VOICE)
        agent.send("What's the capital of France?", InferenceMode.FAST_ACTION, spoken = true).join()
        agent.send("How tall is Everest?", InferenceMode.FAST_ACTION).join()
        assertThat(speech.spoken).containsExactly("Paris is the capital of France.")
        // Each new message first stops what was being read.
        assertThat(speech.stops).isEqualTo(2)
    }

    @Test
    fun alwaysReadsEveryReplyAndCardsAreReadAsTheirOutcome() = runBlocking {
        val agent = agent(ScriptedLlm("Sure!"), SpokenReplies.ALWAYS)
        agent.send("Say something", InferenceMode.FAST_ACTION).join()
        // A quick command makes a card and no text: its outcome is read.
        agent.send("open spotify", InferenceMode.FAST_ACTION).join()
        assertThat(speech.spoken.first()).isEqualTo("Sure!")
        assertThat(speech.spoken).hasSize(2)
        assertThat(speech.spoken[1]).contains("Tap Confirm to go ahead.")
    }

    @Test
    fun neverWhenOffOrNotInTheBuild() = runBlocking {
        agent(ScriptedLlm("Hello."), SpokenReplies.OFF).send("Hi", InferenceMode.FAST_ACTION, spoken = true).join()
        agent(ScriptedLlm("Hello."), SpokenReplies.ALWAYS, inBuild = false).send("Hi", InferenceMode.FAST_ACTION, spoken = true).join()
        assertThat(speech.spoken).isEmpty()
    }
}
