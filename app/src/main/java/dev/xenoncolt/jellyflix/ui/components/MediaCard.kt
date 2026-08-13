package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/** Standard rail/grid poster width. Grids override it to fill their cells. */
val PosterWidth = 124.dp

/**
 * 2:3 poster with a type badge and a permanent bottom gradient carrying title,
 * year and score.
 *
 * The web version reveals that overlay on hover; the handoff notes mobile has no
 * hover, so it stays visible. Press gives the 1.06 image scale the web card uses
 * on hover, which is the only affordance a finger gets.
 */
@Composable
fun MediaCard(
    media: MediaSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "posterScale",
    )

    Column(modifier = modifier.width(PosterWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Tokens.Ink800)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            RemoteImage(
                url = Tmdb.poster(media.posterPath, Tmdb.PosterSize.W342),
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
            )

            // Bottom scrim so the title stays legible over a bright poster.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        )
                    )
            )

            if (showBadge) {
                TypeBadge(
                    mediaType = media.mediaType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }

            if (media.voteAverage > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Tokens.Accent500,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = Format.rating(media.voteAverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.labelLarge.fontSize * 1.15f,
                )
                media.year?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.labelSmall,
                        color = Tokens.Mist400,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** `MOVIE` in brand-500, `SERIES` in series-500 — 10px bold uppercase. */
@Composable
fun TypeBadge(
    mediaType: MediaType,
    modifier: Modifier = Modifier,
) {
    Text(
        text = mediaType.label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (mediaType == MediaType.MOVIE) Tokens.Brand500 else Tokens.Series500
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Skeleton with the same footprint as [MediaCard], so nothing jumps on load. */
@Composable
fun MediaCardPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(PosterWidth)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(shimmerBrush())
    )
}
