package com.ailauncher.domain.ai

import kotlinx.serialization.json.JsonObject

/** One turn handed to [Qwen3PromptBuilder]. */
data class PromptMessage(
    val role: Role,
    val content: String,
    /** `reasoning_content`: the model's `<think>` text for this assistant turn, if any. */
    val reasoning: String? = null,
    val toolCalls: List<PromptToolCall> = emptyList(),
) {
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

data class PromptToolCall(val name: String, val arguments: JsonObject)

/**
 * Kotlin port of the Jinja chat template embedded in Qwen3 GGUF files (ChatML with Hermes-style
 * tool calling and hybrid thinking). The output is tokenised by llama.cpp with special-token
 * parsing enabled, so `<|im_start|>` / `<|im_end|>` become control tokens.
 *
 * Behaviour mirrored from the template:
 *  * tools are listed inside `<tools></tools>` in the system turn, one JSON schema per line;
 *  * `reasoning_content` is only re-emitted for assistant turns after the last real user query
 *    (multi-step tool use); earlier turns keep their final answers only;
 *  * consecutive tool results are grouped into one `user` turn of `<tool_response>` blocks;
 *  * `enable_thinking = false` pre-fills an empty `<think>\n\n</think>\n\n` block, which is the
 *    hard switch behind Fast Action Mode.
 */
object Qwen3PromptBuilder {
    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"

    /** Qwen3 soft switch appended to user turns in Fast Action Mode. */
    const val NO_THINK = "/no_think"

    private const val TOOL_RESPONSE_START = "<tool_response>"
    private const val TOOL_RESPONSE_END = "</tool_response>"

    fun build(
        messages: List<PromptMessage>,
        tools: List<JsonObject> = emptyList(),
        enableThinking: Boolean = true,
        addGenerationPrompt: Boolean = true,
    ): String {
        val sb = StringBuilder()
        val first = messages.firstOrNull()

        if (tools.isNotEmpty()) {
            sb.append(IM_START).append("system\n")
            if (first?.role == PromptMessage.Role.SYSTEM) sb.append(first.content).append("\n\n")
            sb.append(
                "# Tools\n\nYou may call one or more functions to assist with the user query.\n\n" +
                    "You are provided with function signatures within <tools></tools> XML tags:\n<tools>",
            )
            tools.forEach { sb.append('\n').append(PromptJson.encode(it)) }
            sb.append(
                "\n</tools>\n\nFor each function call, return a json object with function name and arguments " +
                    "within <tool_call></tool_call> XML tags:\n<tool_call>\n" +
                    "{\"name\": <function-name>, \"arguments\": <args-json-object>}\n</tool_call>",
            )
            sb.append(IM_END).append('\n')
        } else if (first?.role == PromptMessage.Role.SYSTEM) {
            sb.append(IM_START).append("system\n").append(first.content).append(IM_END).append('\n')
        }

        val lastQueryIndex = messages.indices.reversed().firstOrNull { i ->
            val m = messages[i]
            m.role == PromptMessage.Role.USER &&
                !(m.content.startsWith(TOOL_RESPONSE_START) && m.content.endsWith(TOOL_RESPONSE_END))
        } ?: messages.lastIndex

        messages.forEachIndexed { index, message ->
            when (message.role) {
                PromptMessage.Role.SYSTEM -> if (index != 0) {
                    sb.append(IM_START).append("system\n").append(message.content).append(IM_END).append('\n')
                }

                PromptMessage.Role.USER ->
                    sb.append(IM_START).append("user\n").append(message.content).append(IM_END).append('\n')

                PromptMessage.Role.ASSISTANT -> appendAssistant(sb, message, index, lastQueryIndex, messages.lastIndex)

                PromptMessage.Role.TOOL -> {
                    if (index == 0 || messages[index - 1].role != PromptMessage.Role.TOOL) {
                        sb.append(IM_START).append("user")
                    }
                    sb.append('\n').append(TOOL_RESPONSE_START).append('\n')
                        .append(message.content)
                        .append('\n').append(TOOL_RESPONSE_END)
                    if (index == messages.lastIndex || messages[index + 1].role != PromptMessage.Role.TOOL) {
                        sb.append(IM_END).append('\n')
                    }
                }
            }
        }

        if (addGenerationPrompt) {
            sb.append(IM_START).append("assistant\n")
            if (!enableThinking) sb.append("<think>\n\n</think>\n\n")
        }
        return sb.toString()
    }

    private fun appendAssistant(
        sb: StringBuilder,
        message: PromptMessage,
        index: Int,
        lastQueryIndex: Int,
        lastIndex: Int,
    ) {
        var content = message.content
        var reasoning = message.reasoning ?: ""
        if (message.reasoning == null && "</think>" in content) {
            val beforeClose = content.substringBefore("</think>")
            content = content.substringAfterLast("</think>").trimStart('\n')
            reasoning = beforeClose.trimEnd('\n').substringAfterLast("<think>").trimStart('\n')
        }

        sb.append(IM_START).append("assistant\n")
        val isLast = index == lastIndex
        if (index > lastQueryIndex && (isLast || reasoning.isNotEmpty())) {
            sb.append("<think>\n").append(reasoning.trim('\n')).append("\n</think>\n\n").append(content.trimStart('\n'))
        } else {
            sb.append(content)
        }

        message.toolCalls.forEachIndexed { i, call ->
            if ((i == 0 && content.isNotEmpty()) || i > 0) sb.append('\n')
            sb.append("<tool_call>\n{\"name\": \"").append(call.name).append("\", \"arguments\": ")
                .append(PromptJson.encode(call.arguments))
                .append("}\n</tool_call>")
        }
        sb.append(IM_END).append('\n')
    }
}
