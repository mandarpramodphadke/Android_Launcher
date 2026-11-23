package com.ailauncher.domain.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serialises JSON exactly like Python's `json.dumps(obj, ensure_ascii=False)`, which is what the
 * Hugging Face `tojson` filter in Qwen3's chat template emits (`", "` and `": "` separators).
 * Matching the training-time formatting byte-for-byte measurably improves tool calling on a
 * 1.7B model.
 */
object PromptJson {

    fun encode(element: JsonElement): String = StringBuilder().also { it.write(element) }.toString()

    private fun StringBuilder.write(element: JsonElement) {
        when (element) {
            JsonNull -> append("null")
            is JsonPrimitive -> if (element.isString) writeString(element.content) else append(element.content)
            is JsonObject -> {
                append('{')
                var first = true
                for ((key, value) in element) {
                    if (!first) append(", ")
                    first = false
                    writeString(key)
                    append(": ")
                    write(value)
                }
                append('}')
            }
            is JsonArray -> {
                append('[')
                element.forEachIndexed { index, value ->
                    if (index > 0) append(", ")
                    write(value)
                }
                append(']')
            }
        }
    }

    private fun StringBuilder.writeString(value: String) {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
