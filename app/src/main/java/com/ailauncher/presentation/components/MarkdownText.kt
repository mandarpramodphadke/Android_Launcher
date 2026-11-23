package com.ailauncher.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Block-level Markdown supported by the chat renderer. */
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val code: String, val language: String?) : MdBlock
}

/**
 * Lightweight streaming-friendly Markdown renderer: headings, bullet/numbered lists, quotes,
 * fenced code blocks (an unterminated fence renders as code while it streams), **bold**,
 * *italic*, `inline code` and [links](https://…).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = LocalContentColor.current,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Paragraph -> Text(inline(block.text, codeBackground, linkColor), style = style, color = color)
                    is MdBlock.Heading -> Text(
                        inline(block.text, codeBackground, linkColor),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                    )
                    is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 12).dp)) {
                        Text(block.marker, style = style, color = color, modifier = Modifier.width(22.dp))
                        Text(inline(block.text, codeBackground, linkColor), style = style, color = color)
                    }
                    is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier.width(3.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                        )
                        Text(
                            inline(block.text, codeBackground, linkColor),
                            style = style.copy(fontStyle = FontStyle.Italic),
                            color = color.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    is MdBlock.Code -> Surface(
                        color = codeBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*•+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val INLINE = Regex(
    """(\*\*(.+?)\*\*)|(`([^`]+)`)|(\[([^\]]+)]\((https?://[^)\s]+)\))|(\*(?![\s*])(.+?)(?<![\s*])\*)""",
)

fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    var inCode = false
    var codeLanguage: String? = null
    val code = StringBuilder()
    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(code.toString().trimEnd('\n'), codeLanguage)
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
                codeLanguage = line.trimStart().removePrefix("```").trim().ifEmpty { null }
            }
            continue
        }
        if (inCode) {
            code.append(rawLine).append('\n')
            continue
        }
        if (line.isBlank()) {
            flushParagraph()
            continue
        }
        val heading = HEADING.matchEntire(line)
        val bullet = BULLET.matchEntire(line)
        val numbered = NUMBERED.matchEntire(line)
        when {
            heading != null -> {
                flushParagraph()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            bullet != null -> {
                flushParagraph()
                blocks += MdBlock.ListItem("•", bullet.groupValues[2], bullet.groupValues[1].length / 2)
            }
            numbered != null -> {
                flushParagraph()
                blocks += MdBlock.ListItem(numbered.groupValues[2] + ".", numbered.groupValues[3], numbered.groupValues[1].length / 2)
            }
            line.startsWith(">") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.removePrefix(">").trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.toString().trimEnd('\n'), codeLanguage)
    flushParagraph()
    return blocks
}

private fun inline(text: String, codeBackground: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in INLINE.findAll(text)) {
        append(text.substring(last, match.range.first))
        val g = match.groups
        when {
            g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]!!.value) }
            g[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(g[4]!!.value)
            }
            g[6] != null -> withLink(
                LinkAnnotation.Url(
                    g[7]!!.value,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(g[6]!!.value) }
            g[9] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[9]!!.value) }
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
