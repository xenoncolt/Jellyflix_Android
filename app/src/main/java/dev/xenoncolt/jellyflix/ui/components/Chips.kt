package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/** Small metadata chip: `HD`, `H.264`, `1080p`, `BluRay`, `2.4 GB`. */
@Composable
fun MetaChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Tokens.Mist300,
    background: Color = Tokens.Ink700,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/** Selectable filter chip used by the grid sort tabs and genre filters. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else Tokens.Mist400,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Tokens.Brand500 else Tokens.Ink700.copy(alpha = 0.7f))
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else Tokens.Divider,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
