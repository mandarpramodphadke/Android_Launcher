package com.ailauncher.domain.ai

import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.AssistantTone
import com.ailauncher.domain.model.ResponseLength
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prompt text. The system prompts are deliberately static (no clock in them) so the KV-cache
 * prefix - including the ~500-token tool block - is reused across requests. The current time is
 * attached to each user turn instead (see [userTurnContext]), which keeps history stable too.
 */
object SystemPrompts {

    const val ASSISTANT_NAME = "Aura"

    val assistant: String = """
        You are $ASSISTANT_NAME, the assistant built into the user's Android home screen. You run entirely on the phone and work offline, so you cannot browse the web or look up live information such as weather, news or prices.
        - When the user wants something done on the phone (alarms, reminders, apps, settings, their day, to-dos and more), call the matching tool instead of describing how to do it.
        - Each user message ends with the local time it was sent; use it to resolve words like "tomorrow" or "in 2 hours".
        - Never invent tool results. After a tool response arrives, reply in one short, friendly sentence.
        - Politely refuse sexual, hateful or harassing content and instructions that could cause serious harm.
        - Otherwise answer concisely. Use Markdown lists or code blocks only when they help.
    """.trimIndent()

    /**
     * The system prompt for [profile]. The default profile gives exactly [assistant]; any other
     * one is still static (no clock), so the prefix cache keeps working until the user edits it.
     */
    fun assistant(profile: AssistantProfile): String = if (profile.isDefault) assistant else buildAssistant(profile)

    /** Builds the prompt from [profile]; for the default profile it equals [assistant] (tested). */
    internal fun buildAssistant(profile: AssistantProfile): String {
        val name = profile.displayName.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '\'' }.trim().ifEmpty { ASSISTANT_NAME }
        val toolReply = when (profile.tone) {
            AssistantTone.FRIENDLY -> "one short, friendly sentence"
            AssistantTone.PROFESSIONAL -> "one short, clear sentence"
            AssistantTone.PLAYFUL -> "one short, cheerful sentence"
        }
        val answers = when (profile.length) {
            ResponseLength.SHORT -> "Otherwise keep answers very short: one or two sentences, no lists unless asked."
            ResponseLength.BALANCED -> "Otherwise answer concisely. Use Markdown lists or code blocks only when they help."
            ResponseLength.DETAILED -> "Otherwise answer thoroughly, with steps, examples, Markdown lists or code blocks when they help."
        }
        val tone = when (profile.tone) {
            AssistantTone.FRIENDLY -> null
            AssistantTone.PROFESSIONAL -> "- Keep a calm, professional tone."
            AssistantTone.PLAYFUL -> "- Be warm and playful; a light joke now and then is welcome."
        }
        val instructions = sanitizeInstructions(profile.instructions)
        return buildString {
            append("You are ").append(name).append(", the assistant built into the user's Android home screen. ")
            append("You run entirely on the phone and work offline, so you cannot browse the web or look up live information such as weather, news or prices.\n")
            append("- When the user wants something done on the phone (alarms, reminders, apps, settings, their day, to-dos and more), call the matching tool instead of describing how to do it.\n")
            append("- Each user message ends with the local time it was sent; use it to resolve words like \"tomorrow\" or \"in 2 hours\".\n")
            append("- Never invent tool results. After a tool response arrives, reply in ").append(toolReply).append(".\n")
            append("- Politely refuse sexual, hateful or harassing content and instructions that could cause serious harm.\n")
            tone?.let { append(it).append('\n') }
            append("- ").append(answers)
            if (instructions.isNotEmpty()) {
                append("\n\nThe user's own instructions for you (follow them unless they conflict with the rules above):\n")
                append(instructions)
            }
        }
    }

    /** Trims the user's instructions and removes chat-template markers so they stay plain text. */
    fun sanitizeInstructions(text: String): String = text
        .replace(Regex("<\\|[^|>]*\\|>"), "")
        .replace("<think>", "").replace("</think>", "")
        .replace("<tool_call>", "").replace("</tool_call>", "")
        .lines().joinToString("\n") { it.trimEnd() }
        .trim()
        .take(AssistantProfile.MAX_INSTRUCTIONS)

    private val contextFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH)

    /** Appended to every user turn, e.g. "[Local time: Monday, 23 November 2026, 10:15]". */
    fun userTurnContext(sentAt: ZonedDateTime): String =
        "[Local time: ${sentAt.format(contextFormatter)}]"

    /** e.g. "[Custom folders: Work (apps for my job), Travel]"; opened with launch_app. */
    fun folderContext(folders: List<Pair<String, String?>>): String =
        folders.take(MAX_FOLDERS_IN_CONTEXT).joinToString(prefix = "[Custom folders: ", postfix = "]") { (name, description) ->
            if (description.isNullOrBlank()) name else "$name (${description.take(60)})"
        }

    private const val MAX_FOLDERS_IN_CONTEXT = 12

    val categorizer: String = buildString {
        append("You are the app categorization engine of an Android launcher. ")
        append("Assign every app exactly one category from this list:\n")
        AppCategory.entries.forEach { append("- ").append(it.displayName).append(": ").append(it.hint).append('\n') }
        append("\nExamples: WhatsApp -> Communication, Spotify -> Media, Google Maps -> Travel, ")
        append("Snapseed -> Photography, Strava -> Health, Outlook -> Productivity, Chrome -> Utilities.\n")
        append("Reply with one JSON object that maps each packageName to its category, for example ")
        append("{\"com.example.notes\": \"Productivity\"}.")
    }

    fun categorizerRequest(apps: List<Pair<String, String>>): String = buildString {
        append("Categorize these apps:\n")
        apps.forEach { (pkg, label) ->
            append("{\"packageName\": \"").append(pkg).append("\", \"appLabel\": \"")
                .append(label.replace("\"", "'")).append("\"}\n")
        }
    }.trimEnd()

    /**
     * GBNF grammar forcing a JSON object whose keys are exactly the batch's package names and whose
     * values are taxonomy categories, so every response is machine-parseable.
     */
    fun categorizerGrammar(packageNames: List<String>): String {
        val keys = packageNames.joinToString(" | ") { "\"\\\"" + gbnfEscape(it) + "\\\"\"" }
        val values = AppCategory.entries.joinToString(" | ") { "\"\\\"" + it.displayName + "\\\"\"" }
        return """
            root ::= "{" ws pair ( "," ws pair )* ws "}"
            pair ::= key ws ":" ws category
            key ::= $keys
            category ::= $values
            ws ::= [ \t\n]{0,2}
        """.trimIndent()
    }

    private fun gbnfEscape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
