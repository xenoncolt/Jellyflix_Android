package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.sp
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Renders the GitHub-flavoured Markdown that release notes are written in.
 *
 * Hand-rolled rather than pulled from a library: this only ever has to read a
 * release body, the app's palette is a custom one a generic renderer would need
 * re-theming to match anyway, and a Markdown dependency would drag its own
 * Compose version into a build that pins everything through the BOM.
 *
 * All the parsing lives in [parseMarkdown]; this maps its output onto styles and
 * nothing more. Supported: headings, bullet/numbered/task lists, fenced and
 * inline code, bold, italic, strikethrough, links, bare URLs, block quotes and
 * rules.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = Tokens.Mist400,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block -> MarkdownBlock(block, style, color) }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock, style: TextStyle, color: Color) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = block.spans.annotated(),
            style = headingStyle(block.level, style),
            color = Tokens.Mist200,
            modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp),
        )

        is MdBlock.Paragraph -> Text(
            text = block.spans.annotated(),
            style = style,
            color = color,
        )

        is MdBlock.ListItem -> Row(
            modifier = Modifier.padding(start = (block.indent * 14).dp),
        ) {
            Text(
                text = block.marker,
                style = style,
                color = if (block.marker == "•") Tokens.Brand300 else color,
                modifier = Modifier.width(if (block.marker.length > 1) 24.dp else 14.dp),
            )
            Text(text = block.spans.annotated(), style = style, color = color)
        }

        is MdBlock.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Tokens.Ink900)
                .padding(10.dp),
        ) {
            // Code must not wrap — a broken line is worse than one you scroll to.
            Text(
                text = block.code,
                style = style.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = Tokens.Mist300,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }

        is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Tokens.Ink600),
            )
            Text(
                text = block.spans.annotated(),
                style = style.copy(fontStyle = FontStyle.Italic),
                color = Tokens.Mist300,
            )
        }

        MdBlock.Rule -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Tokens.Ink600,
        )
    }
}

@Composable
private fun headingStyle(level: Int, base: TextStyle): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleMedium
    2 -> MaterialTheme.typography.titleSmall
    else -> base
}.copy(fontWeight = FontWeight.Bold)

private val LINK_STYLES = TextLinkStyles(
    style = SpanStyle(
        color = Tokens.Brand300,
        textDecoration = TextDecoration.Underline,
    )
)

/** Maps parsed spans onto Compose styling. */
private fun List<MdSpan>.annotated(): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        val spanStyle = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) Tokens.Ink700 else Color.Unspecified,
            color = if (span.code) Tokens.Mist200 else Color.Unspecified,
        )

        if (span.url != null) {
            withLink(LinkAnnotation.Url(url = span.url, styles = LINK_STYLES)) {
                withStyle(spanStyle) { append(span.text) }
            }
        } else {
            withStyle(spanStyle) { append(span.text) }
        }
    }
}
