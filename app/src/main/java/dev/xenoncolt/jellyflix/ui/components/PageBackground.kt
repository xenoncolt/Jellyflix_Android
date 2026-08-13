package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import dev.xenoncolt.jellyflix.ui.theme.pageHighlightBrush
import dev.xenoncolt.jellyflix.ui.theme.pageBackgroundBrush

/**
 * The layered page gradient from handoff section 9.
 *
 * Drawn in `drawBehind` rather than as stacked Boxes: two brushes on one draw
 * scope cost a single layer and never invalidate while the content above scrolls.
 */
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = pageBackgroundBrush(size))
                drawRect(brush = pageHighlightBrush(size))
            },
        content = content,
    )
}
