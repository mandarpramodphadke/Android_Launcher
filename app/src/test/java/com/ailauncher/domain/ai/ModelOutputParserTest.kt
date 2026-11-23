package com.ailauncher.domain.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ModelOutputParserTest {

    @Test
    fun parsesHermesToolCallFromFastMode() {
        // Verbatim output of Qwen3-1.7B Q4_K_M in the host harness.
        val raw = "<tool_call>\n{\"name\": \"set_alarm\", \"arguments\": {\"hour\": 6, \"minute\": 45}}\n</tool_call>"
        val parsed = ModelOutputParser.parse(raw)
        assertThat(parsed.answer).isEmpty()
        assertThat(parsed.thinking).isNull()
        assertThat(parsed.toolCalls).hasSize(1)
        assertThat(parsed.toolCalls[0].name).isEqualTo("set_alarm")
        assertThat(parsed.toolCalls[0].arguments["hour"]!!.jsonPrimitive.int).isEqualTo(6)
    }

    @Test
    fun splitsThinkingFromAnswer() {
        val raw = "<think>\nCheck 17 x 23.\n</think>\n\n391 is not prime."
        val parsed = ModelOutputParser.parse(raw)
        assertThat(parsed.thinking).isEqualTo("Check 17 x 23.")
        assertThat(parsed.thinkingComplete).isTrue()
        assertThat(parsed.answer).isEqualTo("391 is not prime.")
    }

    @Test
    fun streamingThinkingIsIncompleteAndHidesPartialCloseTag() {
        val parsed = ModelOutputParser.parse("<think>\nLet me see</th")
        assertThat(parsed.thinkingComplete).isFalse()
        assertThat(parsed.thinking).isEqualTo("Let me see")
        assertThat(parsed.answer).isEmpty()
    }

    @Test
    fun emptyThinkBlockOfFastModeIsDropped() {
        val parsed = ModelOutputParser.parse("<think>\n\n</think>\n\nHello!")
        assertThat(parsed.thinking).isNull()
        assertThat(parsed.answer).isEqualTo("Hello!")
    }

    @Test
    fun closingTagOnlyTreatsPrefixAsThinking() {
        val parsed = ModelOutputParser.parse("reasoning here\n</think>\n\nAnswer")
        assertThat(parsed.thinking).isEqualTo("reasoning here")
        assertThat(parsed.answer).isEqualTo("Answer")
    }

    @Test
    fun toolCallInProgressIsHiddenFromAnswer() {
        val parsed = ModelOutputParser.parse("Sure!\n<tool_call>\n{\"name\": \"launch_")
        assertThat(parsed.answer).isEqualTo("Sure!")
        assertThat(parsed.toolCallInProgress).isTrue()
        assertThat(parsed.toolCalls).isEmpty()
    }

    @Test
    fun partialOpeningTagIsNotShown() {
        assertThat(ModelOutputParser.parse("Opening it <tool_ca").answer).isEqualTo("Opening it")
    }

    @Test
    fun answerTextAroundToolCallsIsKept() {
        val raw = "Setting it now.\n<tool_call>\n{\"name\": \"set_timer\", \"arguments\": {\"seconds\": 300}}\n</tool_call>"
        val parsed = ModelOutputParser.parse(raw)
        assertThat(parsed.answer).isEqualTo("Setting it now.")
        assertThat(parsed.toolCalls.single().name).isEqualTo("set_timer")
    }

    @Test
    fun lenientJsonVariantsAreAccepted() {
        assertThat(ModelOutputParser.parseToolCallJson("```json\n{\"name\": \"launch_app\", \"arguments\": {\"appName\": \"Maps\"},}\n```")?.name)
            .isEqualTo("launch_app")
        val wrapped = ModelOutputParser.parseToolCallJson("{\"function\": {\"name\": \"sort_apps\", \"parameters\": {}}}")
        assertThat(wrapped?.name).isEqualTo("sort_apps")
        val stringArgs = ModelOutputParser.parseToolCallJson("{\"name\": \"set_timer\", \"arguments\": \"{\\\"seconds\\\": 60}\"}")
        assertThat(stringArgs!!.arguments["seconds"]!!.jsonPrimitive.int).isEqualTo(60)
        val singleQuotes = ModelOutputParser.parseToolCallJson("{'name': 'launch_app', 'arguments': {'appName': 'Spotify'}}")
        assertThat(singleQuotes?.name).isEqualTo("launch_app")
    }

    @Test
    fun bareJsonToolCallWithoutTagsIsRecovered() {
        val parsed = ModelOutputParser.parse("{\"name\": \"launch_app\", \"arguments\": {\"appName\": \"Camera\"}}")
        assertThat(parsed.toolCalls.single().name).isEqualTo("launch_app")
        assertThat(parsed.answer).isEmpty()
    }

    @Test
    fun garbageIsNotAToolCall() {
        assertThat(ModelOutputParser.parseToolCallJson("not json")).isNull()
        assertThat(ModelOutputParser.parseToolCallJson("{\"arguments\": {}}")).isNull()
    }
}
