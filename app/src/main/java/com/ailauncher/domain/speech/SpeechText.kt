package com.ailauncher.domain.speech

/** Turns a Markdown reply into plain sentences for text-to-speech (SPOKEN_REPLIES). Pure. */
object SpeechText {

    private val FENCED_CODE = Regex("""```.*?(?:```|$)""", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_CODE = Regex("""`([^`]*)`""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val URL = Regex("""https?://\S+""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val LIST_MARKER = Regex("""^(?:[-*+•]|\d{1,3}[.)])\s+""")
    private val EMPHASIS = Regex("""(\*\*|__|~~)(.+?)\1""")
    private val SINGLE_EMPHASIS = Regex("""(?<![\w*])[*_](\S(?:.*?\S)?)[*_](?![\w*])""")
    private val TABLE_RULE = Regex("""^\|?\s*:?-{2,}.*$""")
    private val SPACES = Regex("""\s+""")
    private val SENTENCE_END = Regex("""(?<=[.!?])\s+""")

    /** Plain text to read aloud: no code blocks, links read by their text, list items as sentences. */
    fun from(markdown: String): String {
        val withoutCode = markdown.replace(FENCED_CODE, "\n(The code is on the screen.)\n")
        val lines = withoutCode.lines().mapNotNull { raw ->
            var line = raw.trim()
            if (line.isEmpty() || TABLE_RULE.matches(line)) return@mapNotNull null
            line = line.replace(HEADING, "").replace(LIST_MARKER, "")
            if (line.startsWith("|")) line = line.trim('|').split('|').joinToString(", ") { it.trim() }
            line = line.replace(IMAGE, "$1").replace(LINK, "$1").replace(URL, "a link")
                .replace(INLINE_CODE, "$1").replace(EMPHASIS, "$2").replace(SINGLE_EMPHASIS, "$1")
                .replace(">", "").trim()
            if (line.isEmpty()) return@mapNotNull null
            // Each heading, list item or line ends as a sentence, so the voice pauses between them.
            if (endsWithoutPunctuation(line)) "$line." else line
        }
        return lines.joinToString(" ").replace(SPACES, " ").trim()
    }

    private fun endsWithoutPunctuation(line: String): Boolean {
        val last = line.last()
        if (last.isLetterOrDigit()) return true
        // "(see below)" needs a full stop; "(The code is on the screen.)" already has one.
        if (last == ')' || last == '"') return line.length < 2 || line[line.length - 2] !in ".!?"
        return false
    }

    /** [text] in pieces of at most [maxLength] characters, split between sentences where possible. */
    fun chunks(text: String, maxLength: Int = DEFAULT_CHUNK): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) result += current.toString().trim()
            current.setLength(0)
        }
        text.split(SENTENCE_END).filter { it.isNotBlank() }.forEach { sentence ->
            var rest = sentence.trim()
            while (rest.length > maxLength) {
                flush()
                val cut = rest.lastIndexOf(' ', maxLength).takeIf { it > maxLength / 2 } ?: maxLength
                result += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (current.length + rest.length + 1 > maxLength) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(rest)
        }
        flush()
        return result
    }

    /** Well under TextToSpeech.getMaxSpeechInputLength() (4000), so the first words start sooner. */
    const val DEFAULT_CHUNK = 600
}
