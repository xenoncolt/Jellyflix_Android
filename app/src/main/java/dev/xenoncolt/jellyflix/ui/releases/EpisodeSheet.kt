package dev.xenoncolt.jellyflix.ui.releases

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.data.local.DownloadState
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.remote.dto.EpisodeSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.ui.components.RemoteImage
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Episodes of a season that is still airing.
 *
 * Only reached when a season has started but isn't finished, because that is the
 * one case with no season pack to download. Episodes that haven't aired are
 * never listed — the server refuses them with a 409, and offering a row that
 * can only fail is worse than not offering it.
 */
@Composable
fun EpisodeSheet(
    media: MediaDetail,
    seasonNumber: Int,
    onDismiss: () -> Unit,
    onEpisodePicked: (Int) -> Unit,
    onPlay: (infoHash: String) -> Unit,
    viewModel: EpisodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(media.id, seasonNumber) { viewModel.load(media.id, seasonNumber) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tokens.Ink850,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = state.detail?.name?.ifBlank { null } ?: "Season $seasonNumber",
                style = MaterialTheme.typography.titleLarge,
                color = Tokens.Mist200,
            )

            state.detail?.let { detail ->
                Text(
                    text = "${detail.airedEpisodes} of ${detail.episodeCount} aired · " +
                        "pick an episode to download",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tokens.Mist400,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
            }

            when {
                state.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Tokens.Brand400)
                }

                state.error != null -> Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.Danger500,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = state.airedEpisodes,
                        key = { it.episodeNumber },
                        contentType = { "episode" },
                    ) { episode ->
                        val download = state.downloads[episode.episodeNumber]

                        EpisodeRow(
                            episode = episode,
                            download = download,
                            onClick = {
                                // Once it is on the device, playing it is the
                                // obvious next thing — not downloading it again.
                                if (download?.ready == true) {
                                    onPlay(download.infoHash)
                                } else if (download?.inProgress != true) {
                                    onEpisodePicked(episode.episodeNumber)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeSummary,
    download: EpisodeDownload?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(140.dp)) {
            RemoteImage(
                url = Tmdb.backdrop(episode.stillPath, Tmdb.BackdropSize.W780),
                contentDescription = episode.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )

            when {
                // A play affordance on the still itself, so a downloaded episode
                // reads as watchable at a glance rather than needing the label.
                download?.ready == true -> Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                download?.inProgress == true -> LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Tokens.Brand400,
                    trackColor = Tokens.Ink700,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${episode.episodeNumber}. ${episode.name.ifBlank { episode.code }}",
                style = MaterialTheme.typography.titleMedium,
                color = Tokens.Mist200,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            EpisodeStatus(episode = episode, download = download)

            if (episode.overview.isNotBlank()) {
                FadingOverview(text = episode.overview)
            }
        }
    }
}

/**
 * The metadata line, which doubles as the download status.
 *
 * The scene code stays visible while there is nothing on disk — it is literally
 * what the release search queries — and gives way to progress or a resume
 * position once there is.
 */
@Composable
private fun EpisodeStatus(episode: EpisodeSummary, download: EpisodeDownload?) {
    val (text, color) = when {
        download?.resumable == true ->
            Format.remaining(download.remainingMs) to Tokens.Success500

        download?.ready == true -> "Downloaded · tap to play" to Tokens.Success500

        download?.inProgress == true ->
            "Downloading ${(download.progress * 100).toInt()}%" to Tokens.Brand300

        download?.state == DownloadState.Failed -> "Download failed · tap to retry" to Tokens.Danger500

        else -> buildString {
            append(episode.code)
            Format.date(episode.airDate)?.let { append(" · ").append(it) }
            episode.runtime?.takeIf { it > 0 }?.let { append(" · ").append("${it}m") }
        } to Tokens.Mist400
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}
/**
 * Overview text that fades out at the bottom instead of ending in an ellipsis.
 *
 * A hard "…" makes every row end on a stub of a word; fading reads as "there is
 * more here" without drawing attention to where it was cut.
 */
@Composable
private fun FadingOverview(text: String) {
    Box {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Tokens.Mist400,
            maxLines = 3,
            // Clipped rather than ellipsised — the gradient is the truncation.
            overflow = TextOverflow.Clip,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Tokens.Ink850),
                    )
                ),
        )
    }
}
