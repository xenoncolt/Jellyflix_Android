package dev.xenoncolt.jellyflix.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.components.SectionHeader
import dev.xenoncolt.jellyflix.ui.nav.GridRoute
import dev.xenoncolt.jellyflix.ui.theme.Tokens
import dev.xenoncolt.jellyflix.ui.theme.genreColor

/**
 * Browse: the entry points that do not fit on the home rails — the full Movies
 * and Series grids, trending, and the complete genre list.
 */
@Composable
fun BrowseScreen(
    onGridClick: (GridRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PageBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Browse",
                    style = MaterialTheme.typography.displaySmall,
                    color = Tokens.Mist200,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }

            item(key = "entries") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EntryTile(
                        label = "Movies",
                        icon = Icons.Rounded.Movie,
                        color = Tokens.Brand500,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onGridClick(
                                GridRoute(
                                    kind = "popular",
                                    title = "Movies",
                                    type = MediaType.MOVIE.wire,
                                )
                            )
                        },
                    )
                    EntryTile(
                        label = "Series",
                        icon = Icons.Rounded.Tv,
                        color = Tokens.Series500,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onGridClick(
                                GridRoute(
                                    kind = "popular",
                                    title = "Series",
                                    type = MediaType.TV.wire,
                                )
                            )
                        },
                    )
                    EntryTile(
                        label = "Trending",
                        icon = Icons.Rounded.LocalFireDepartment,
                        color = Tokens.Accent500,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onGridClick(GridRoute(kind = "trending", title = "Trending"))
                        },
                    )
                }
            }

            if (state.movieGenres.isNotEmpty()) {
                item(key = "movie_genres_header") { SectionHeader(title = "Movie genres") }
                item(key = "movie_genres") {
                    GenreGrid(
                        genres = state.movieGenres,
                        onClick = { genre ->
                            onGridClick(
                                GridRoute(
                                    kind = "genre",
                                    title = genre.name,
                                    type = MediaType.MOVIE.wire,
                                    id = genre.id,
                                )
                            )
                        },
                    )
                }
            }

            if (state.seriesGenres.isNotEmpty()) {
                item(key = "series_genres_header") { SectionHeader(title = "Series genres") }
                item(key = "series_genres") {
                    GenreGrid(
                        genres = state.seriesGenres,
                        onClick = { genre ->
                            onGridClick(
                                GridRoute(
                                    kind = "genre",
                                    title = genre.name,
                                    type = MediaType.TV.wire,
                                    id = genre.id,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreGrid(
    genres: List<dev.xenoncolt.jellyflix.data.remote.dto.Genre>,
    onClick: (dev.xenoncolt.jellyflix.data.remote.dto.Genre) -> Unit,
) {
    // Height is derived rather than measured: a nested lazy grid inside a lazy
    // column needs a bounded height, and these lists are short and known.
    val rows = (genres.size + 1) / 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.height((rows * 56).dp.coerceAtMost(600.dp)),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(items = genres, key = { it.id }, contentType = { "genre" }) { genre ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(genreColor(genre.id).copy(alpha = 0.85f))
                    .clickable { onClick(genre) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EntryTile(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Tokens.Mist200,
        )
    }
}
