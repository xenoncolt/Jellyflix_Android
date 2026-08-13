package dev.xenoncolt.jellyflix.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.R
import dev.xenoncolt.jellyflix.data.remote.dto.CompanyTile
import dev.xenoncolt.jellyflix.data.remote.dto.GenreTile
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.ui.components.CompanyRail
import dev.xenoncolt.jellyflix.ui.components.ErrorState
import dev.xenoncolt.jellyflix.ui.components.GenreRail
import dev.xenoncolt.jellyflix.ui.components.HeroPager
import dev.xenoncolt.jellyflix.ui.components.HeroPagerPlaceholder
import dev.xenoncolt.jellyflix.ui.components.MediaRail
import dev.xenoncolt.jellyflix.ui.components.MediaRailPlaceholder
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.nav.GridRoute
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Home: hero carousel then the nine rails from `/api/home`.
 *
 * Everything is a `LazyColumn` item rather than a `Column` inside a scroll, so
 * rails below the fold are never composed until they approach the viewport.
 */
@Composable
fun HomeScreen(
    onMediaClick: (MediaSummary) -> Unit,
    onGridClick: (GridRoute) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    PageBackground(modifier = modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "topbar", contentType = "topbar") {
                HomeTopBar(onSearchClick = onSearchClick, onProfileClick = onProfileClick)
            }

            val data = state.data

            if (state.loading && data == null) {
                item(key = "hero_skeleton") { HeroPagerPlaceholder() }
                items(count = 3, key = { "rail_skeleton_$it" }) { MediaRailPlaceholder() }
                return@LazyColumn
            }

            if (data == null) {
                item(key = "error") {
                    ErrorState(
                        message = state.error?.message ?: "Couldn't load the home screen.",
                        onRetry = { viewModel.load() },
                    )
                }
                return@LazyColumn
            }

            // A refresh failure while content is already on screen is a banner,
            // not a replacement for the page.
            state.error?.let { error ->
                item(key = "error_banner") { ErrorBanner(error.message) }
            }

            item(key = "hero", contentType = "hero") {
                HeroPager(
                    items = data.trending,
                    genreLookup = { ids -> state.genresFor(ids) },
                    onItemClick = onMediaClick,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            rail(
                key = "popular_movies",
                title = "Popular Movies",
                items = data.popularMovies,
                onMediaClick = onMediaClick,
                onSeeAll = {
                    onGridClick(
                        GridRoute(
                            kind = "popular",
                            title = "Popular Movies",
                            type = MediaType.MOVIE.wire,
                        )
                    )
                },
            )

            genreRail(
                key = "movie_genres",
                title = "Movie Genres",
                genres = data.movieGenres,
                onGenreClick = { genre ->
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

            rail(
                key = "upcoming_movies",
                title = "Upcoming Movies",
                items = data.upcomingMovies,
                onMediaClick = onMediaClick,
                onSeeAll = {
                    onGridClick(
                        GridRoute(
                            kind = "upcoming",
                            title = "Upcoming Movies",
                            type = MediaType.MOVIE.wire,
                        )
                    )
                },
            )

            rail(
                key = "popular_series",
                title = "Popular Series",
                items = data.popularSeries,
                onMediaClick = onMediaClick,
                onSeeAll = {
                    onGridClick(
                        GridRoute(
                            kind = "popular",
                            title = "Popular Series",
                            type = MediaType.TV.wire,
                        )
                    )
                },
            )

            genreRail(
                key = "series_genres",
                title = "Series Genres",
                genres = data.seriesGenres,
                onGenreClick = { genre ->
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

            rail(
                key = "upcoming_series",
                title = "Upcoming Series",
                items = data.upcomingSeries,
                onMediaClick = onMediaClick,
                onSeeAll = {
                    onGridClick(
                        GridRoute(
                            kind = "upcoming",
                            title = "Upcoming Series",
                            type = MediaType.TV.wire,
                        )
                    )
                },
            )

            companyRail(
                key = "studios",
                title = "Studios",
                companies = data.studios,
                onCompanyClick = { studio ->
                    onGridClick(
                        GridRoute(
                            kind = "company",
                            title = studio.name,
                            type = MediaType.MOVIE.wire,
                            id = studio.id,
                        )
                    )
                },
            )

            companyRail(
                key = "networks",
                title = "Networks",
                companies = data.networks,
                onCompanyClick = { network ->
                    onGridClick(
                        GridRoute(
                            kind = "network",
                            title = network.name,
                            type = MediaType.TV.wire,
                            id = network.id,
                        )
                    )
                },
            )
        }
    }
}

// --- Lazy list helpers ------------------------------------------------------
// Extracted so each rail is its own lazy item and composes only when scrolled to.

private fun androidx.compose.foundation.lazy.LazyListScope.rail(
    key: String,
    title: String,
    items: List<MediaSummary>,
    onMediaClick: (MediaSummary) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (items.isEmpty()) return
    item(key = key, contentType = "rail") {
        MediaRail(
            title = title,
            items = items,
            onItemClick = onMediaClick,
            onSeeAll = onSeeAll,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.genreRail(
    key: String,
    title: String,
    genres: List<GenreTile>,
    onGenreClick: (GenreTile) -> Unit,
) {
    if (genres.isEmpty()) return
    item(key = key, contentType = "genre_rail") {
        GenreRail(title = title, genres = genres, onGenreClick = onGenreClick)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.companyRail(
    key: String,
    title: String,
    companies: List<CompanyTile>,
    onCompanyClick: (CompanyTile) -> Unit,
) {
    if (companies.isEmpty()) return
    item(key = key, contentType = "company_rail") {
        CompanyRail(title = title, companies = companies, onCompanyClick = onCompanyClick)
    }
}

@Composable
private fun HomeTopBar(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Tokens.Ink800.copy(alpha = 0.7f))
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = "Jellyflix",
                style = MaterialTheme.typography.titleLarge,
                color = Tokens.Mist200,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(onClick = onSearchClick) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Tokens.Mist200)
            }
            CircleIconButton(onClick = onProfileClick) {
                Icon(Icons.Rounded.Person, contentDescription = "Profile", tint = Tokens.Mist200)
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Tokens.Ink800.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = Tokens.Danger500,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.Danger500.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
