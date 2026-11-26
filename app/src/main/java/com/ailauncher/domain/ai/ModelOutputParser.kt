package com.ailauncher.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A tool call extracted from model output, before validation by [ToolRouter]. */
data class RawToolCall(val name: String, val arguments: JsonObject)

/** Structured view of (possibly still streaming) model output. */
data class ParsedOutput(
    /** `<think>` content; null when the model did not think (or thought nothing). */
    val thinking: String?,
    /** False while the model is still inside its `<think>` block. */
    val thinkingComplete: Boolean,
    /** The user-visible answer with thinking and tool-call markup removed. */
    val answer: String,
    val toolCalls: List<RawToolCall>,
    /** A `<tool_call>` has been opened but not closed yet (streaming). */
    val toolCallInProgress: Boolean,
)

/**
 * Splits Qwen3 output into thinking, answer and Hermes-style `<tool_call>` blocks.
 *
 * It is a pure function of the text generated so far, so the streaming UI simply re-parses the
 * accumulated output on each token; partial tags at the end of the stream ("<tool_ca") are hidden
 * until they resolve.
 */
object ModelOutputParser {
    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"
    private const val CALL_OPEN = "<tool_call>"
    private const val CALL_CLOSE = "</tool_call>"
    private val HIDDEN_TAGS = listOf(THINK_OPEN, THINK_CLOSE, CALL_OPEN, CALL_CLOSE, "<|im_end|>", "<|endoftext|>")

    private val SALVAGE_NAME = Regex(""""name"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"""")
    private val SALVAGE_ARGS = Regex(""""(?:arguments|parameters)"\s*:\s*\{""")

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
        allowComments = true
    }

    /** A JSON call wrapped in `<tool>` or `<tools>` tags instead of `<tool_call>` (seen from Qwen3-1.7B). */
    private val TOOL_TAG_CALL = Regex("""<(tools?)>\s*(\{.*?\})\s*</\1>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * @param lenient also recover calls the model wrote badly (the BETTER_ANSWERS switch): broken
     * JSON keeps its tool name, and a call wrapped in `<tool>` tags counts as a call.
     */
    fun parse(raw: String, lenient: Boolean = false): ParsedOutput {
        var text = raw
        var thinking: String? = null
        var thinkingComplete = true

        val open = text.indexOf(THINK_OPEN)
        val close = text.indexOf(THINK_CLOSE)
        if (open >= 0 && text.substring(0, open).isBlank() && (close < 0 || open < close)) {
            if (close < 0) {
                thinking = stripPartialTag(text.substring(open + THINK_OPEN.length)).trimStart('\n')
                thinkingComplete = false
                text = ""
            } else {
                thinking = text.substring(open + THINK_OPEN.length, close).trim()
                text = text.substring(close + THINK_CLOSE.length)
            }
        } else if (close >= 0 && open < 0) {
            // Templates that pre-fill "<think>" make the model emit only the closing tag.
            thinking = text.substring(0, close).trim()
            text = text.substring(close + THINK_CLOSE.length)
        }
        if (thinking != null && thinking.isEmpty() && thinkingComplete) thinking = null

        val calls = mutableListOf<RawToolCall>()
        val answer = StringBuilder()
        var cursor = 0
        var inProgress = false
        while (cursor < text.length) {
            val start = text.indexOf(CALL_OPEN, cursor)
            if (start < 0) {
                answer.append(text, cursor, text.length)
                break
            }
            answer.append(text, cursor, start)
            val end = text.indexOf(CALL_CLOSE, start + CALL_OPEN.length)
            if (end < 0) {
                inProgress = true
                break
            }
            parseToolCallJson(text.substring(start + CALL_OPEN.length, end), lenient)?.let(calls::add)
            cursor = end + CALL_CLOSE.length
        }

        var visible = stripPartialTag(answer.toString())
        HIDDEN_TAGS.forEach { visible = visible.replace(it, "") }
        visible = visible.trim()

        // Fallback: the model answered with a bare JSON tool call and forgot the tags.
        if (calls.isEmpty() && !inProgress && visible.startsWith("{") && visible.endsWith("}")) {
            parseToolCallJson(visible, lenient)?.let {
                calls += it
                visible = ""
            }
        }
        if (lenient && calls.isEmpty() && !inProgress) {
            TOOL_TAG_CALL.find(visible)?.let { m ->
                parseToolCallJson(m.groupValues[2], lenient = true)?.let {
                    calls += it
                    visible = visible.removeRange(m.range).trim()
                }
            }
        }

        return ParsedOutput(
            thinking = thinking,
            thinkingComplete = thinkingComplete,
            answer = visible,
            toolCalls = calls,
            toolCallInProgress = inProgress,
        )
    }

    /**
     * Parses `{"name": ..., "arguments": {...}}` leniently: code fences, a `function` wrapper,
     * `parameters` instead of `arguments`, double-encoded argument strings and single quotes.
     */
    fun parseToolCallJson(body: String, lenient: Boolean = false): RawToolCall? {
        val cleaned = body.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```")
            .trim()
        if (cleaned.isEmpty()) return null
        // Lenient mode happily reads {'name': ...} with the quotes as part of the key, so a
        // successful parse is not enough: try each candidate until one yields a tool name.
        for (candidate in listOf(cleaned, cleaned.replace('\'', '"'))) {
            val root = decodeObject(candidate) ?: continue
            toToolCall(root)?.let { return it }
        }
        return if (lenient) salvageToolCall(cleaned) else null
    }

    /**
     * Last resort for JSON the model broke, e.g. `{"name": "get_device_info", "arguments": {""}}`
     * (seen from Qwen3-1.7B): keep the tool name, and the arguments when they still parse on their
     * own. The router then validates the call as usual.
     */
    private fun salvageToolCall(text: String): RawToolCall? {
        val name = SALVAGE_NAME.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val args = SALVAGE_ARGS.find(text)?.let { balancedObject(text, it.range.last) }?.let(::decodeObject) ?: JsonObject(emptyMap())
        return RawToolCall(name, args)
    }

    /** The `{...}` starting at [open], braces inside strings ignored; null if it never closes. */
    private fun balancedObject(text: String, open: Int): String? {
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> if (--depth == 0) return text.substring(open, i + 1)
            }
            i++
        }
        return null
    }

    private fun toToolCall(root: JsonObject): RawToolCall? {
        val target = (root["function"] as? JsonObject) ?: root
        val name = (target["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val argsElement: JsonElement? = target["arguments"] ?: target["parameters"] ?: target["args"]
        val args = when (argsElement) {
            is JsonObject -> argsElement
            is JsonPrimitive -> argsElement.contentOrNull?.let(::decodeObject) ?: JsonObject(emptyMap())
            else -> JsonObject(emptyMap())
        }
        return RawToolCall(name, args)
    }

    private fun decodeObject(text: String): JsonObject? = runCatching {
        lenientJson.parseToJsonElement(text).jsonObject
    }.getOrNull()

    /** Removes a trailing fragment that could still grow into one of the hidden tags. */
    private fun stripPartialTag(text: String): String {
        val lt = text.lastIndexOf('<')
        if (lt < 0) return text
        val tail = text.substring(lt)
        if (tail.length > 1 && HIDDEN_TAGS.any { it.startsWith(tail) && it != tail }) {
            return text.substring(0, lt)
        }
        if (tail == "<") return text.substring(0, lt)
        return text
    }

    /** Convenience for reading a string argument. */
    fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    internal fun JsonElement.primitiveOrNull(): JsonPrimitive? = runCatching { jsonPrimitive }.getOrNull()
}
