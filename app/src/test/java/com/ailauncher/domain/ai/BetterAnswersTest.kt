package com.ailauncher.domain.ai

import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.AssistantTone
import com.ailauncher.domain.model.ModelSpec
import com.ailauncher.domain.model.ResponseLength
import com.ailauncher.domain.system.ModelAdvice
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The general-knowledge prompt and get_device_info (BETTER_ANSWERS, full version first). */
class BetterAnswersTest {

    @Test
    fun theSwitchPicksThePrompt() {
        // Off (Google Play for now): exactly the original prompt.
        assertThat(SystemPrompts.assistant(AssistantProfile(), betterAnswers = false)).isSameInstanceAs(SystemPrompts.assistant)
        // On: answers general questions and sends phone questions to get_device_info.
        val prompt = SystemPrompts.assistant(AssistantProfile(), betterAnswers = true)
        assertThat(prompt).isSameInstanceAs(SystemPrompts.generalAssistant)
        assertThat(prompt).contains("from your own knowledge. Never say you lack access, information or tools for them.")
        assertThat(prompt).contains("call get_device_info first")
        assertThat(prompt).contains("Tasks for a list go to manage_todo.")
        assertThat(prompt).doesNotContain("cannot browse the web or look up live information")
        assertThat(prompt).doesNotContain("Local time:")
        assertThat(prompt.indexOf("Politely refuse")).isGreaterThan(0)
    }

    @Test
    fun customProfilesWorkWithTheNewPrompt() {
        val prompt = SystemPrompts.assistant(
            AssistantProfile("Nova", ResponseLength.DETAILED, AssistantTone.PLAYFUL, "Use metric units."),
            betterAnswers = true,
        )
        assertThat(prompt).startsWith("You are Nova, a helpful assistant")
        assertThat(prompt).contains("- Be warm and playful")
        assertThat(prompt).contains("- Otherwise answer thoroughly, with steps or examples when they help, without offering phone actions")
        assertThat(prompt).endsWith("rules above):\nUse metric units.")
        assertThat(SystemPrompts.buildGeneralAssistant(AssistantProfile())).isEqualTo(SystemPrompts.generalAssistant)
    }

    @Test
    fun deviceQuestionsHaveAQuickPathOnlyWhenSwitchedOn() {
        val on = QuickCommandParser(betterAnswers = true)
        listOf(
            "battery", "battery level", "What's my battery level?", "how much battery is left", "how much battery do I have left?",
            "phone specs", "what are my phone specs?", "system info", "device info", "how much storage do I have", "how much free space is left",
            "how much RAM do I have",
        ).forEach { assertThat(on.parse(it)).isEqualTo(ToolCommand.GetDeviceInfo) }
        // Not a request for this phone's data.
        listOf("battery saver on", "what is a battery", "how much ram does a gaming pc need").forEach {
            assertThat(on.parse(it)).isNotEqualTo(ToolCommand.GetDeviceInfo)
        }
        assertThat(QuickCommandParser().parse("battery level")).isNull()
    }

    @Test
    fun questionsAboutThisPhoneReadItsDetailsFirst() {
        val on = QuickCommandParser(betterAnswers = true)
        listOf(
            // The question from the owner's screenshot.
            "I am asking about offline llm models, according to my device specifications which LLM model I can use without any issues for best results?",
            "Which AI model can my phone run?",
            "Is my battery running low?",
            "Does my phone have enough RAM to play games smoothly?",
            "what processor does this phone have",
            "Is this device powerful enough for the 4B model?",
            "how much space left on my phone",
        ).forEach { assertThat(on.parse(it)).isEqualTo(ToolCommand.GetDeviceInfo) }
        listOf(
            // General knowledge, or a task for another tool.
            "Is it bad to charge my phone overnight?",
            "What's the difference between RAM and storage?",
            "Which LLM is best for coding?",
            "How do I free up space on my phone?",
            "find the largest videos on my phone",
            "which apps use the most battery on my phone",
            "turn on wifi on my phone",
        ).forEach { assertThat(on.parse(it)).isNotEqualTo(ToolCommand.GetDeviceInfo) }
        assertThat(QuickCommandParser().parse("Which AI model can my phone run?")).isNull()
    }

    @Test
    fun freeTimeQuestionsOpenThatDaysPlanOnlyWhenSwitchedOn() {
        val monday = ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, ZoneId.of("UTC"))
        val on = QuickCommandParser(betterAnswers = true) { monday }
        fun plan(day: LocalDate) = ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, day)
        assertThat(on.parse("when am I free tomorrow?")).isEqualTo(plan(LocalDate.of(2026, 11, 24)))
        assertThat(on.parse("Am I free today")).isEqualTo(plan(LocalDate.of(2026, 11, 23)))
        assertThat(on.parse("Do I have any free time on Friday afternoon?")).isEqualTo(plan(LocalDate.of(2026, 11, 27)))
        assertThat(on.parse("when do I have free time")).isEqualTo(plan(LocalDate.of(2026, 11, 23)))
        // A time or a person is a question for the model.
        assertThat(on.parse("am I free at 3pm tomorrow?")).isNull()
        assertThat(on.parse("is Sam free tomorrow?")).isNull()
        assertThat(QuickCommandParser { monday }.parse("when am I free tomorrow?")).isNull()
    }

    @Test
    fun theToolIsOfferedRoutedAndSurvivesBrokenJson() {
        fun names(ctx: Int, on: Boolean) = ToolDefinitions.forContext(ctx, on).map { it["function"]!!.let { f -> (f as kotlinx.serialization.json.JsonObject)["name"]!!.jsonPrimitive.content } }
        assertThat(names(4096, false)).doesNotContain(ToolNames.GET_DEVICE_INFO)
        assertThat(names(4096, true)).containsAtLeast(ToolNames.GET_TODAY_AGENDA, ToolNames.GET_DEVICE_INFO).inOrder()
        assertThat(names(2048, true)).contains(ToolNames.GET_DEVICE_INFO)
        assertThat(ToolDefinitions.forContext(4096)).isEqualTo(ToolDefinitions.forContext(4096, deviceInfo = false))

        val router = ToolRouter()
        listOf("get_device_info", "device_info", "system_info").forEach { name ->
            val routed = router.route(RawToolCall(name, kotlinx.serialization.json.JsonObject(emptyMap())))
            assertThat((routed as ToolRouting.Valid).command).isEqualTo(ToolCommand.GetDeviceInfo)
        }
        // Verbatim Qwen3-1.7B output: the arguments object isn't valid JSON.
        val parsed = ModelOutputParser.parse("<tool_call>\n{\"name\": \"get_device_info\", \"arguments\": {\"\"}}\n</tool_call>")
        assertThat(parsed.toolCalls.map { it.name }).containsExactly("get_device_info")
        // Salvaged arguments still parse when only the outer object is broken.
        val partly = ModelOutputParser.parseToolCallJson("{\"name\": \"set_timer\", \"arguments\": {\"seconds\": 300}, }}")
        assertThat(partly?.name).isEqualTo("set_timer")
    }

    @Test
    fun modelAdviceFollowsTheRam() {
        val gib = 1024L * 1024 * 1024
        val catalog = listOf(
            ModelSpec("small", "Qwen3 0.6B", "a", "", 400_000_000, null, "", recommendedRamBytes = 3 * gib),
            ModelSpec("mid", "Qwen3 1.7B", "b", "", 1_100_000_000, null, "", recommendedRamBytes = 4 * gib),
            ModelSpec("large", "Qwen3 4B", "c", "", 2_500_000_000, null, "", recommendedRamBytes = 7 * gib),
        )
        fun verdicts(ram: Long, free: Long? = null, active: String? = null) =
            ModelAdvice.forDevice(catalog, ram, free, active).associate { it.spec.id to it.verdict }

        assertThat(verdicts(12 * gib)).containsExactly("small", "good", "mid", "good", "large", "best")
        assertThat(verdicts(6 * gib)).containsExactly("small", "good", "mid", "best", "large", "tight")
        assertThat(verdicts(4 * gib)).containsExactly("small", "good", "mid", "best", "large", "too big")
        assertThat(verdicts(3 * gib)).containsExactly("small", "best", "mid", "too big", "large", "too big")
        val fits = ModelAdvice.forDevice(catalog, 8 * gib, freeStorageBytes = 1_000_000_000, activeId = "mid")
        assertThat(fits.single { it.spec.id == "mid" }.inUse).isTrue()
        assertThat(fits.single { it.spec.id == "large" }.enoughStorage).isFalse()
        assertThat(fits.single { it.spec.id == "small" }.enoughStorage).isTrue()
    }
}
