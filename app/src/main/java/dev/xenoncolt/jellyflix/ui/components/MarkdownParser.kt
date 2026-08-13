package dev.xenoncolt.jellyflix.ui.components

/**
 * Turns the GitHub-flavoured Markdown of a release body into blocks and spans.
 *
 * Deliberately free of any Compose type. Parsing is where the fiddly cases live
 * — unclosed fences, emphasis markers that never close, underscores inside
 * identifiers — and keeping it as plain Kotlin means all of that is covered by
 * ordinary JVM tests instead of needing a device. [MarkdownText] does nothing
 * but map the result onto styles.
 *
 * Anything unrecognised is emitted as plain text rather than dropped, so an
 * unsupported construct costs its formatting and nothing else.
 */

internal sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class ListItem(val marker: String, val spans: List<MdSpan>, val indent: Int) : MdBlock
    data class Code(val code: String) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data object Rule : MdBlock
}

/**
 * A run of text and the styling that applies to it.
 *
 * Flat rather than a tree: nesting is resolved while parsing by handing the
 * inherited styles down, so `**bold with `code`**` arrives as two spans that
 * both carry bold. Rendering then never has to walk anything.
 */
internal data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

// --- Blocks -----------------------------------------------------------------

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^([-*+])\s+(.*)$""")
private val ORDERED = Regex("""^(\d{1,9})[.)]\s+(.*)$""")
private val TASK = Regex("""^\[([ xX])]\s+(.*)$""")
private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

internal fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    // Consecutive plain lines are one paragraph; Markdown only breaks on a blank
    // line, and joining them keeps wrapping sane at a dialog's width.
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(parseInline(paragraph.toString()))
            paragraph.clear()
        }
    }

    var index = 0
    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.trim()

        // Fenced code is taken verbatim, so nothing inside is parsed as Markdown.
        if (line.startsWith("```") || line.startsWith("~~~")) {
            flushParagraph()
            val fence = line.take(3)
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[index])
                index++
            }
            // Steps over the closing fence, or off the end for an unclosed block.
            index++
            blocks += MdBlock.Code(code.toString().trimEnd())
            continue
        }

        when {
            line.isEmpty() -> flushParagraph()

            RULE.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }

            HEADING.matches(line) -> {
                flushParagraph()
                val (hashes, text) = HEADING.find(line)!!.destructured
                // Trailing hashes are decoration in the closed-ATX form.
                blocks += MdBlock.Heading(hashes.length, parseInline(text.trimEnd('#').trim()))
            }

            line.startsWith(">") -> {
                flushParagraph()
                blocks += MdBlock.Quote(parseInline(line.removePrefix(">").trim()))
            }

            BULLET.matches(line) || ORDERED.matches(line) -> {
                flushParagraph()
                blocks += listItem(raw, line)
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
        index++
    }

    flushParagraph()
    return blocks
}

private fun listItem(raw: String, line: String): MdBlock.ListItem {
    // Two spaces per level in most release notes, four in the spec; halving
    // handles both without the indent exploding on the latter.
    val indent = (raw.length - raw.trimStart().length) / 2

    ORDERED.find(line)?.let { match ->
        val (number, text) = match.destructured
        return MdBlock.ListItem("$number.", parseInline(text), indent)
    }

    val (_, text) = BULLET.find(line)!!.destructured

    // A bullet whose content opens with [ ] or [x] is a task list item.
    TASK.find(text)?.let { match ->
        val (box, label) = match.destructured
        val marker = if (box.equals("x", ignoreCase = true)) "☑" else "☐"
        return MdBlock.ListItem(marker, parseInline(label), indent)
    }

    return MdBlock.ListItem("•", parseInline(text), indent)
}

// --- Inline -----------------------------------------------------------------

private const val AUTOLINK_TRAILING = ".,;:!?)]}\"'"

private class Emphasis(val marker: String, val apply: (Style) -> Style) {
    /**
     * Underscores only open emphasis at a word boundary, so identifiers like
     * `max_download_kbps` survive intact. GitHub behaves the same way.
     */
    fun opens(text: String, at: Int): Boolean =
        !marker.startsWith("_") || at == 0 || !text[at - 1].isLetterOrDigit()
}

private data class Style(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val url: String? = null,
)

/** Longest markers first, so `***` is not read as `**` followed by `*`. */
private val EMPHASIS = listOf(
    Emphasis("***") { it.copy(bold = true, italic = true) },
    Emphasis("___") { it.copy(bold = true, italic = true) },
    Emphasis("**") { it.copy(bold = true) },
    Emphasis("__") { it.copy(bold = true) },
    Emphasis("~~") { it.copy(strikethrough = true) },
    Emphasis("*") { it.copy(italic = true) },
    Emphasis("_") { it.copy(italic = true) },
)

internal fun parseInline(source: String): List<MdSpan> =
    mutableListOf<MdSpan>().also { scan(source, Style(), it) }

/**
 * Scans inline markers left to right.
 *
 * An unmatched marker is emitted as the literal character it is, which is what
 * keeps "2 * 3" and snake_case identifiers intact instead of swallowing the rest
 * of the line into emphasis that never closes.
 */
private fun scan(text: String, style: Style, out: MutableList<MdSpan>) {
    val literal = StringBuilder()

    fun flush() {
        if (literal.isEmpty()) return
        out += MdSpan(
            text = literal.toString(),
            bold = style.bold,
            italic = style.italic,
            strikethrough = style.strikethrough,
            url = style.url,
        )
        literal.clear()
    }

    var i = 0
    while (i < text.length) {
        val char = text[i]

        // A backslash escapes whatever follows it.
        if (char == '\\' && i + 1 < text.length) {
            literal.append(text[i + 1])
            i += 2
            continue
        }

        // Code spans beat every other marker: their content is literal.
        if (char == '`') {
            val close = text.indexOf('`', i + 1)
            if (close > i) {
                flush()
                out += MdSpan(
                    text = text.substring(i + 1, close),
                    bold = style.bold,
                    italic = style.italic,
                    strikethrough = style.strikethrough,
                    code = true,
                    url = style.url,
                )
                i = close + 1
                continue
            }
        }

        // Images have no place in a dialog; keep the alt text, drop the rest.
        if (char == '!' && i + 1 < text.length && text[i + 1] == '[') {
            val image = readLink(text, i + 1)
            if (image != null) {
                literal.append(image.label)
                i = image.end
                continue
            }
        }

        if (char == '[') {
            val link = readLink(text, i)
            if (link != null) {
                flush()
                scan(link.label, style.copy(url = link.url), out)
                i = link.end
                continue
            }
        }

        // Bare URLs, which release notes are full of ("Full Changelog: …").
        if (style.url == null && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
            var end = i
            while (end < text.length && !text[end].isWhitespace()) end++
            // Sentence punctuation is not part of the link.
            while (end > i && text[end - 1] in AUTOLINK_TRAILING) end--
            val url = text.substring(i, end)
            flush()
            out += MdSpan(
                text = url,
                bold = style.bold,
                italic = style.italic,
                strikethrough = style.strikethrough,
                url = url,
            )
            i = end
            continue
        }

        val emphasis = EMPHASIS.firstOrNull { text.startsWith(it.marker, i) && it.opens(text, i) }
        if (emphasis != null) {
            val close = text.indexOf(emphasis.marker, i + emphasis.marker.length)
            if (close > i) {
                flush()
                scan(
                    text.substring(i + emphasis.marker.length, close),
                    emphasis.apply(style),
                    out,
                )
                i = close + emphasis.marker.length
                continue
            }
        }

        literal.append(char)
        i++
    }

    flush()
}

private class MdLink(val label: String, val url: String, val end: Int)

/** Reads `[label](url)` starting at the bracket, or null if it isn't one. */
private fun readLink(text: String, start: Int): MdLink? {
    var depth = 0
    var labelEnd = -1

    // Track nesting so a label containing brackets closes on the right one.
    for (index in start until text.length) {
        when (text[index]) {
            '[' -> depth++
            ']' -> {
                depth--
                if (depth == 0) {
                    labelEnd = index
                    break
                }
            }
        }
    }

    if (labelEnd < 0 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null

    val urlEnd = text.indexOf(')', labelEnd + 2)
    if (urlEnd < 0) return null

    // A title after the URL — [x](url "title") — is not shown anywhere.
    val url = text.substring(labelEnd + 2, urlEnd).substringBefore(' ').trim()
    if (url.isEmpty()) return null

    return MdLink(
        label = text.substring(start + 1, labelEnd),
        url = url,
        end = urlEnd + 1,
    )
}
