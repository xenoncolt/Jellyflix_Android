package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.remote.dto.CompanyTile
import dev.xenoncolt.jellyflix.data.remote.dto.GenreTile
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.ui.theme.Tokens
import dev.xenoncolt.jellyflix.ui.theme.genreColor

private val RailPadding = PaddingValues(horizontal = 16.dp)

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onSeeAll != null) Modifier.clickable(onClick = onSeeAll) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Tokens.Mist200,
        )
        if (onSeeAll != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "See all $title",
                tint = Tokens.Mist400,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Horizontal poster rail.
 *
 * `key` is what keeps this smooth: without it, Compose re-creates every item on
 * any list change and the rail visibly stutters when a background refresh lands.
 */
@Composable
fun MediaRail(
    title: String,
    items: List<MediaSummary>,
    onItemClick: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        SectionHeader(title = title, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = RailPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = items,
                key = { it.key },
                contentType = { "poster" },
            ) { media ->
                MediaCard(media = media, onClick = { onItemClick(media) })
            }
        }
    }
}

@Composable
fun MediaRailPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .width(160.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush())
        )
        LazyRow(
            contentPadding = RailPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false,
        ) {
            items(count = 5, key = { it }) { MediaCardPlaceholder() }
        }
    }
}

/**
 * Genre tile: flat per-genre colour with a desaturated backdrop at 45% opacity,
 * name centred (handoff section 9).
 */
@Composable
fun GenreRail(
    title: String,
    genres: List<GenreTile>,
    onGenreClick: (GenreTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return

    Column(modifier = modifier) {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = RailPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = genres, key = { it.id }, contentType = { "genre" }) { genre ->
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(genreColor(genre.id))
                        .clickable { onGenreClick(genre) },
                    contentAlignment = Alignment.Center,
                ) {
                    Tmdb.backdrop(genre.backdropPath, Tmdb.BackdropSize.W780)?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // Desaturated so the genre colour, not the still, reads first.
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix().apply { setToSaturation(0.15f) }
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.45f),
                        )
                    }
                    Text(
                        text = genre.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Studio / network tile. TMDB's logos are dark artwork on transparency, so they
 * are inverted to read on the dark surface — the web build does the same with
 * `brightness-0 invert`.
 */
@Composable
fun CompanyRail(
    title: String,
    companies: List<CompanyTile>,
    onCompanyClick: (CompanyTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (companies.isEmpty()) return

    val invert = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )

    Column(modifier = modifier) {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = RailPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = companies, key = { it.id }, contentType = { "company" }) { company ->
                Box(
                    modifier = Modifier
                        .width(132.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tokens.Ink800)
                        .clickable { onCompanyClick(company) },
                    contentAlignment = Alignment.Center,
                ) {
                    val logo = Tmdb.logo(company.logoPath, Tmdb.LogoSize.W154)
                    if (logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = company.name,
                            contentScale = ContentScale.Fit,
                            colorFilter = invert,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    } else {
                        Text(
                            text = company.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = Tokens.Mist300,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
