package dev.xenoncolt.jellyflix.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.ui.components.ErrorState
import dev.xenoncolt.jellyflix.ui.components.FilterChip
import dev.xenoncolt.jellyflix.ui.components.MediaCard
import dev.xenoncolt.jellyflix.ui.components.MediaCardPlaceholder
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.theme.Tokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Infinite grid backing every "see all" destination.
 *
 * Pagination triggers on `derivedStateOf` over the last visible index rather
 * than in the item composable: reading scroll position directly in composition
 * would recompose the whole grid on every pixel of scroll.
 */
@Composable
fun GridScreen(
    onMediaClick: (MediaSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GridViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            // ~2 rows of runway, matching the web build's 800px trigger.
            total > 0 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(gridState, state.canLoadMore) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadNextPage() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Tokens.Mist200,
                    navigationIconContentColor = Tokens.Mist200,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        PageBackground {
            Column(modifier = Modifier.padding(padding)) {
                if (state.showSortTabs || state.showTrendingFilters) {
                    FilterBar(
                        state = state,
                        onSort = viewModel::selectSort,
                        onMediaType = viewModel::selectMediaType,
                        onWindow = viewModel::selectTrendingWindow,
                    )
                }

                when {
                    state.loading -> LoadingGrid()

                    state.items.isEmpty() && state.error != null -> ErrorState(
                        message = state.error?.message ?: "Couldn't load results.",
                        onRetry = viewModel::retry,
                    )

                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = state.items,
                            key = { it.key },
                            contentType = { "poster" },
                        ) { media ->
                            MediaCard(
                                media = media,
                                onClick = { onMediaClick(media) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (state.loadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        strokeWidth = 2.5.dp,
                                        color = Tokens.Brand400,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    state: GridUiState,
    onSort: (GridSort) -> Unit,
    onMediaType: (MediaType?) -> Unit,
    onWindow: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.showSortTabs) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GridSort.entries.forEach { sort ->
                    // "In Cinemas" has no meaning for series.
                    if (sort == GridSort.NowPlaying && state.mediaType == MediaType.TV) {
                        return@forEach
                    }
                    FilterChip(
                        label = sort.label,
                        selected = state.sort == sort,
                        onClick = { onSort(sort) },
                    )
                }
            }
        }

        if (state.showTrendingFilters) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    label = "All",
                    selected = state.mediaType == null,
                    onClick = { onMediaType(null) },
                )
                FilterChip(
                    label = "Movies",
                    selected = state.mediaType == MediaType.MOVIE,
                    onClick = { onMediaType(MediaType.MOVIE) },
                )
                FilterChip(
                    label = "Series",
                    selected = state.mediaType == MediaType.TV,
                    onClick = { onMediaType(MediaType.TV) },
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    label = "Daily",
                    selected = state.trendingWindow == "day",
                    onClick = { onWindow("day") },
                )
                FilterChip(
                    label = "Weekly",
                    selected = state.trendingWindow == "week",
                    onClick = { onWindow("week") },
                )
            }
        }
    }
}

@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        items(count = 12, key = { "skeleton_$it" }) {
            MediaCardPlaceholder(modifier = Modifier.fillMaxWidth())
        }
    }
}
