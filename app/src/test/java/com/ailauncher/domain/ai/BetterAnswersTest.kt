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
        assertThat(prompt).contains("come from get_device_info: call it, never guess them")
        assertThat(prompt).contains("Tasks for a list go to manage_todo;")
        // Phone rules come before the general-knowledge rule (measured: the other order made the
        // model promise to check the schedule instead of calling the tool).
        assertThat(prompt.indexOf("call the matching tool")).isLessThan(prompt.indexOf("from your own knowledge"))
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
        // With a time, the day's plan is read and the model answers from it; other people's time isn't ours.
        assertThat(on.parse("am I free at 3pm tomorrow?")).isEqualTo(plan(LocalDate.of(2026, 11, 24)))
        assertThat(on.parse("is Sam free tomorrow?")).isNull()
        assertThat(QuickCommandParser { monday }.parse("when am I free tomorrow?")).isNull()
    }

    @Test
    fun scheduleQuestionsReadTheDayOnlyWhenSwitchedOn() {
        val monday = ZonedDateTime.of(2026, 11, 23, 10, 15, 0, 0, ZoneId.of("UTC"))
        val on = QuickCommandParser(betterAnswers = true) { monday }
        fun plan(day: Int) = ToolCommand.ManagePlanner(PlannerAction.GET_DAY_PLAN, LocalDate.of(2026, 11, day))
        mapOf(
            "Do I have any meetings tomorrow?" to plan(24),
            "what do I have on Friday?" to plan(27),
            "What do I have tomorrow" to plan(24),
            "what's on tomorrow?" to plan(24),
            "Is there anything on my calendar on Thursday?" to plan(26),
            "What's my schedule for tomorrow?" to plan(24),
            "am I busy this afternoon?" to plan(23),
            "What's on my calendar?" to ToolCommand.GetTodayAgenda,
            "do I have any appointments today?" to ToolCommand.GetTodayAgenda,
        ).forEach { (text, command) -> assertThat(on.parse(text)).isEqualTo(command) }
        listOf(
            // Changes to the calendar, to-dos, and general questions stay with the model.
            "add a meeting tomorrow at 3pm",
            "schedule a call with Sam on Friday",
            "remind me tomorrow about my appointment",
            "cancel my meetings tomorrow",
            "what do I have to do today?",
            "Do I have time to learn Python?",
            "what is a calendar year?",
        ).forEach { assertThat(on.parse(it)).isNull() }
        assertThat(QuickCommandParser { monday }.parse("Do I have any meetings tomorrow?")).isNull()
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
        // Verbatim Qwen3-1.7B outputs: the arguments object isn't valid JSON, or the call is wrapped
        // in <tool> tags. Recovered only in lenient mode (the switch); the Play version is unchanged.
        listOf(
            "<tool_call>\n{\"name\": \"get_device_info\", \"arguments\": {\"\"}}\n</tool_call>",
            "<tool_call>\n{\"name\": \"get_device_info\", \"arguments\": {\"}}\n</tool_call>",
            "<tool>\n{\"name\": \"get_device_info\", \"arguments\": {\"\"}}\n</tool>",
            // Text first, then the call in <tools> tags.
            "Let me check.\n\n<tools>\n{\"name\": \"get_device_info\", \"arguments\": {}}\n</tools>",
        ).forEach { output ->
            val lenient = ModelOutputParser.parse(output, lenient = true)
            assertThat(lenient.toolCalls.map { it.name }).containsExactly("get_device_info")
            assertThat(lenient.answer).doesNotContain("get_device_info")
            assertThat(ModelOutputParser.parse(output).toolCalls).isEmpty()
        }
        // Salvaged arguments still parse when only the outer object is broken.
        val partly = ModelOutputParser.parseToolCallJson("{\"name\": \"set_timer\", \"arguments\": {\"seconds\": 300}, }}", lenient = true)
        assertThat(partly?.name).isEqualTo("set_timer")
        assertThat(partly?.arguments?.get("seconds")?.jsonPrimitive?.content).isEqualTo("300")
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
