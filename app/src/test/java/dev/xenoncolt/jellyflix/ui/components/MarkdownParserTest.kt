package dev.xenoncolt.jellyflix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Markdown a GitHub release body actually contains, plus the
 * malformed cases that decide whether an unclosed marker costs its formatting or
 * eats the rest of the notes.
 */
class MarkdownParserTest {

    private fun textOf(spans: List<MdSpan>) = spans.joinToString("") { it.text }

    // --- Blocks -------------------------------------------------------------

    @Test
    fun `parses headings at every level`() {
        val blocks = parseMarkdown("# One\n## Fixes\n### Deep")

        assertEquals(3, blocks.size)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals(2, (blocks[1] as MdBlock.Heading).level)
        assertEquals("Fixes", textOf((blocks[1] as MdBlock.Heading).spans))
        assertEquals(3, (blocks[2] as MdBlock.Heading).level)
    }

    @Test
    fun `hash without a space is not a heading`() {
        // "#42" is an issue reference, not a heading.
        val blocks = parseMarkdown("#42 was fixed")
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun `closed atx headings drop their trailing hashes`() {
        val heading = parseMarkdown("## Changes ##").single() as MdBlock.Heading
        assertEquals("Changes", textOf(heading.spans))
    }

    @Test
    fun `fenced code is taken verbatim`() {
        val blocks = parseMarkdown(
            """
            ## Changes
            ```kotlin
            val x = **not bold**
            # not a heading
            ```
            done
            """.trimIndent()
        )

        val code = blocks.filterIsInstance<MdBlock.Code>().single()
        assertEquals("val x = **not bold**\n# not a heading", code.code)
        assertTrue(blocks.last() is MdBlock.Paragraph)
    }

    @Test
    fun `unclosed fence runs to the end instead of losing the rest`() {
        val code = parseMarkdown("```\nstill code\nmore code").single() as MdBlock.Code
        assertEquals("still code\nmore code", code.code)
    }

    @Test
    fun `parses bullet ordered and task items`() {
        val blocks = parseMarkdown(
            """
            - first
            * second
            1. third
            2) fourth
            - [x] done
            - [ ] todo
            """.trimIndent()
        ).filterIsInstance<MdBlock.ListItem>()

        assertEquals(6, blocks.size)
        assertEquals("•", blocks[0].marker)
        assertEquals("second", textOf(blocks[1].spans))
        assertEquals("1.", blocks[2].marker)
        assertEquals("2.", blocks[3].marker)
        assertEquals("☑", blocks[4].marker)
        assertEquals("☐", blocks[5].marker)
        assertEquals("todo", textOf(blocks[5].spans))
    }

    @Test
    fun `nested list items report their depth`() {
        val items = parseMarkdown("- top\n  - nested\n    - deeper")
            .filterIsInstance<MdBlock.ListItem>()

        assertEquals(listOf(0, 1, 2), items.map { it.indent })
    }

    @Test
    fun `consecutive lines join into one paragraph and blank lines split them`() {
        val blocks = parseMarkdown("one\ntwo\n\nthree")

        assertEquals(2, blocks.size)
        assertEquals("one two", textOf((blocks[0] as MdBlock.Paragraph).spans))
        assertEquals("three", textOf((blocks[1] as MdBlock.Paragraph).spans))
    }

    @Test
    fun `parses rules and quotes`() {
        val blocks = parseMarkdown("> heads up\n\n---")

        assertEquals("heads up", textOf((blocks[0] as MdBlock.Quote).spans))
        assertEquals(MdBlock.Rule, blocks[1])
    }

    @Test
    fun `handles windows line endings`() {
        val blocks = parseMarkdown("## Fixes\r\n- one\r\n")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.ListItem)
    }

    @Test
    fun `empty input produces nothing`() {
        assertTrue(parseMarkdown("").isEmpty())
    }

    // --- Inline -------------------------------------------------------------

    @Test
    fun `parses bold italic and strikethrough`() {
        val spans = parseInline("**b** *i* ~~s~~ ***bi***")

        assertTrue(spans.single { it.text == "b" }.bold)
        assertTrue(spans.single { it.text == "i" }.italic)
        assertTrue(spans.single { it.text == "s" }.strikethrough)
        val both = spans.single { it.text == "bi" }
        assertTrue(both.bold && both.italic)
    }

    @Test
    fun `inline code is literal and marked`() {
        val spans = parseInline("run `val x = **y**` now")
        val code = spans.single { it.code }

        assertEquals("val x = **y**", code.text)
    }

    @Test
    fun `nesting inherits the outer style`() {
        val spans = parseInline("**bold with *italic* inside**")
        assertTrue(spans.all { it.bold })
        assertTrue(spans.single { it.text == "italic" }.italic)
    }

    @Test
    fun `unmatched markers stay literal`() {
        // The regression this guards: an unclosed marker used to swallow the
        // rest of the line into a span that never closed.
        assertEquals("2 * 3 = 6", textOf(parseInline("2 * 3 = 6")))
        assertEquals("**oops", textOf(parseInline("**oops")))
        assertTrue(parseInline("**oops").none { it.bold })
    }

    @Test
    fun `underscores inside identifiers are not emphasis`() {
        val spans = parseInline("set max_download_kbps to 0")

        assertEquals("set max_download_kbps to 0", textOf(spans))
        assertTrue(spans.none { it.italic })
    }

    @Test
    fun `parses links and keeps the label`() {
        val link = parseInline("see [the notes](https://example.com/x) here")
            .single { it.url != null }

        assertEquals("the notes", link.text)
        assertEquals("https://example.com/x", link.url)
    }

    @Test
    fun `link titles are dropped`() {
        val link = parseInline("""[x](https://e.com "title")""").single { it.url != null }
        assertEquals("https://e.com", link.url)
    }

    @Test
    fun `bare urls become links without trailing punctuation`() {
        val link = parseInline("Full Changelog: https://github.com/a/b/compare/v1...v2.")
            .single { it.url != null }

        assertEquals("https://github.com/a/b/compare/v1...v2", link.url)
    }

    @Test
    fun `images keep their alt text only`() {
        val spans = parseInline("![a badge](https://img.example/x.png) after")

        assertEquals("a badge after", textOf(spans))
        assertNull(spans.firstOrNull { it.url != null })
    }

    @Test
    fun `escapes are honoured`() {
        val spans = parseInline("""not \*\*bold\*\*""")

        assertEquals("not **bold**", textOf(spans))
        assertTrue(spans.none { it.bold })
    }

    @Test
    fun `renders a realistic github release body`() {
        val blocks = parseMarkdown(
            """
            ## What's Changed
            * Fixed the `add_torrent` crash by @xenoncolt in https://github.com/a/b/pull/12
            * **Storage:** ask for All files access

            ## Fixes
            - [x] permissions
            - [ ] tables

            **Full Changelog**: https://github.com/a/b/compare/v1.0.0...v1.1.0
            """.trimIndent()
        )

        assertEquals(2, blocks.filterIsInstance<MdBlock.Heading>().size)
        assertEquals(4, blocks.filterIsInstance<MdBlock.ListItem>().size)

        // Every link in the body survives parsing.
        val urls = blocks.flatMap { block ->
            when (block) {
                is MdBlock.Paragraph -> block.spans
                is MdBlock.ListItem -> block.spans
                else -> emptyList()
            }
        }.mapNotNull { it.url }

        assertEquals(
            listOf(
                "https://github.com/a/b/pull/12",
                "https://github.com/a/b/compare/v1.0.0...v1.1.0",
            ),
            urls,
        )
    }
}
