package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.remote.dto.Genre
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.ui.theme.Tokens
import kotlinx.coroutines.delay

/**
 * The top-of-home carousel: a wide backdrop card per title with genre line and
 * name, snapping between pages.
 *
 * Auto-advances every 6 seconds, but only while the user is not touching it —
 * a carousel that yanks itself sideways mid-swipe is the most irritating thing
 * a home screen can do.
 */
@Composable
fun HeroPager(
    items: List<MediaSummary>,
    genreLookup: (List<Int>) -> List<Genre>,
    onItemClick: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val pages = remember(items) { items.take(8) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState, pages.size) {
        if (pages.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % pages.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 10.dp,
            // Keeps one page either side warm so a swipe never shows an empty card.
            beyondViewportPageCount = 1,
        ) { page ->
            val media = pages[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(208.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Tokens.Ink800)
                    .clickable { onItemClick(media) },
            ) {
                RemoteImage(
                    url = Tmdb.backdrop(media.backdropPath, Tmdb.BackdropSize.W780)
                        ?: Tmdb.poster(media.posterPath, Tmdb.PosterSize.W500),
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.88f),
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val genres = genreLookup(media.genreIds)
                    if (genres.isNotEmpty()) {
                        Text(
                            text = genres.take(3).joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelMedium,
                            color = Tokens.Mist300,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Tokens.Brand400 else Tokens.Ink600)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroPagerPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(208.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(shimmerBrush())
    )
}
