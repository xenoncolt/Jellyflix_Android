package dev.xenoncolt.jellyflix.ui.releases

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.data.remote.dto.TorrentRelease
import dev.xenoncolt.jellyflix.ui.components.EmptyState
import dev.xenoncolt.jellyflix.ui.components.ErrorState
import dev.xenoncolt.jellyflix.ui.components.FilterChip
import dev.xenoncolt.jellyflix.ui.components.MetaChip
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Release picker, presented as a bottom sheet per the handoff's mobile note.
 *
 * Season packs only for series — the sheet is opened with a season number and
 * the server filters single episodes out, so there is nothing here to get wrong.
 */
@Composable
fun ReleaseSheet(
    media: MediaDetail,
    season: Int?,
    onDismiss: () -> Unit,
    onDownloadStarted: () -> Unit,
    viewModel: ReleaseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(media.id, season) { viewModel.search(media, season) }

    LaunchedEffect(state.startedInfoHash) {
        if (state.startedInfoHash != null) {
            viewModel.consumeStartResult()
            onDownloadStarted()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tokens.Ink850,
        contentColor = Tokens.Mist200,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(bottom = 12.dp),
        ) {
            Header(media = media, season = season, loadingMore = state.loadingMore)

            if (state.availableResolutions.size > 1) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        label = "All",
                        selected = state.resolutionFilter == null,
                        onClick = { viewModel.setResolutionFilter(null) },
                    )
                    state.availableResolutions.forEach { resolution ->
                        FilterChip(
                            label = resolution,
                            selected = state.resolutionFilter == resolution,
                            onClick = { viewModel.setResolutionFilter(resolution) },
                        )
                    }
                }
            }

            state.startError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Tokens.Danger500,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            when {
                state.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 2.5.dp,
                            color = Tokens.Brand400,
                        )
                        Text(
                            text = "Searching indexers…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tokens.Mist400,
                        )
                    }
                }

                state.releases.isEmpty() && state.error != null -> ErrorState(
                    message = state.error?.message ?: "Search failed.",
                    onRetry = { viewModel.search(media, season) },
                )

                state.releases.isEmpty() -> EmptyState(
                    title = "No releases found",
                    message = if (season != null) {
                        "No season packs for season $season. Try again later."
                    } else {
                        "Nothing on the indexers for this title yet."
                    },
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.visibleReleases,
                        key = { it.guid.ifBlank { it.title } },
                        contentType = { "release" },
                    ) { release ->
                        ReleaseRow(
                            release = release,
                            starting = state.startingGuid == release.guid,
                            enabled = state.startingGuid == null,
                            onClick = { viewModel.startDownload(release) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(media: MediaDetail, season: Int?, loadingMore: Boolean) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = if (season != null) "Season $season" else "Choose a release",
            style = MaterialTheme.typography.headlineSmall,
            color = Tokens.Mist200,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Tokens.Mist400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (loadingMore) {
                // Tier 2 still running — say so rather than let the list twitch.
                Text(
                    text = "finding more…",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tokens.Brand300,
                )
            }
        }
    }
}

@Composable
private fun ReleaseRow(
    release: TorrentRelease,
    starting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleSmall,
                color = Tokens.Mist200,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Tokens.Brand400,
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            release.resolution?.let { MetaChip(it, color = Tokens.Mist200) }
            release.quality?.let { MetaChip(it) }
            release.codec?.let { MetaChip(it) }
            MetaChip(Format.bytes(release.size))
            if (release.isSeasonPack) {
                MetaChip("SEASON PACK", color = Tokens.Brand300)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "Seeders",
                    tint = Tokens.Success500,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = Format.compact(release.seeders),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tokens.Success500,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = "Leechers",
                    tint = Tokens.Mist400,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = Format.compact(release.leechers),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tokens.Mist400,
                )
            }
            Text(
                text = release.indexer,
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Mist400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
