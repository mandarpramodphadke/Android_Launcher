package com.ailauncher.presentation.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parsesBlocks() {
        val blocks = parseMarkdown(
            """
            # Plan
            Here is **today**:

            - Gym at 7
            - Call *mom*
            2. Second
            > quoted

            ```kotlin
            val x = 1
            ```
            """.trimIndent(),
        )
        assertThat(blocks).containsExactly(
            MdBlock.Heading(1, "Plan"),
            MdBlock.Paragraph("Here is **today**:"),
            MdBlock.ListItem("•", "Gym at 7", 0),
            MdBlock.ListItem("•", "Call *mom*", 0),
            MdBlock.ListItem("2.", "Second", 0),
            MdBlock.Quote("quoted"),
            MdBlock.Code("val x = 1", "kotlin"),
        ).inOrder()
    }

    @Test
    fun unterminatedFenceRendersAsCodeWhileStreaming() {
        val blocks = parseMarkdown("Sure:\n```\nprint('hi')")
        assertThat(blocks.last()).isEqualTo(MdBlock.Code("print('hi')", null))
    }
}
