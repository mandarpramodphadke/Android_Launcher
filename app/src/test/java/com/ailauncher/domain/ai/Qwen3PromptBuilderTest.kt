package com.ailauncher.domain.ai

import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Golden tests: `qwen3_chat_template_fixtures.json` was rendered from the Jinja chat template
 * embedded in Qwen3-1.7B's GGUF with the same jinja2 environment Hugging Face transformers uses.
 * The Kotlin port must reproduce it byte for byte.
 */
class Qwen3PromptBuilderTest {

    private val fixtures: JsonArray = run {
        val text = javaClass.classLoader!!.getResourceAsStream("qwen3_chat_template_fixtures.json")!!
            .bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonArray
    }

    @Test
    fun matchesReferenceJinjaTemplate() {
        check(fixtures.size >= 6)
        for (fixture in fixtures.map { it.jsonObject }) {
            val name = fixture["name"]!!.jsonPrimitive.content
            val messages = fixture["messages"]!!.jsonArray.map { toPromptMessage(it.jsonObject) }
            val tools = fixture["tools"]!!.jsonArray.map { it.jsonObject }
            val actual = Qwen3PromptBuilder.build(
                messages = messages,
                tools = tools,
                enableThinking = fixture["enableThinking"]!!.jsonPrimitive.boolean,
            )
            assertWithMessage("fixture '$name'").that(actual).isEqualTo(fixture["expected"]!!.jsonPrimitive.content)
        }
    }

    private fun toPromptMessage(obj: JsonObject): PromptMessage {
        val role = when (obj["role"]!!.jsonPrimitive.content) {
            "system" -> PromptMessage.Role.SYSTEM
            "user" -> PromptMessage.Role.USER
            "assistant" -> PromptMessage.Role.ASSISTANT
            else -> PromptMessage.Role.TOOL
        }
        val calls = (obj["tool_calls"] as? JsonArray)?.map { call ->
            val function = call.jsonObject["function"]!!.jsonObject
            PromptToolCall(function["name"]!!.jsonPrimitive.content, function["arguments"]!!.jsonObject)
        }.orEmpty()
        return PromptMessage(
            role = role,
            content = obj["content"]!!.jsonPrimitive.content,
            reasoning = obj["reasoning_content"]?.jsonPrimitive?.contentOrNull,
            toolCalls = calls,
        )
    }
}
