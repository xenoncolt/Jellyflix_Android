package dev.xenoncolt.jellyflix.ui.detail

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.local.DownloadEntity
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.remote.dto.SeasonSummary
import dev.xenoncolt.jellyflix.ui.components.ErrorState
import dev.xenoncolt.jellyflix.ui.components.MediaCard
import dev.xenoncolt.jellyflix.ui.components.MetaChip
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.components.RemoteImage
import dev.xenoncolt.jellyflix.ui.components.SectionHeader
import dev.xenoncolt.jellyflix.ui.components.ShimmerBox
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Detail screen.
 *
 * Movies get a single action button and series get a season list, each row
 * covering a whole season pack — individual episodes are deliberately not
 * offered, since the server's season search already drops them.
 *
 * The action turns into Play once the download has finished, which is the whole
 * point of shipping a player: offering "Download again" on something already on
 * disk was never what anyone wanted next.
 */
@Composable
fun DetailScreen(
    onMediaClick: (MediaSummary) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onDownloadRequest: (media: MediaDetail, season: Int?) -> Unit,
    onPlay: (infoHash: String) -> Unit,
    onSignInRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    if (state.requiresAuthForBookmark) {
        viewModel.consumeAuthPrompt()
        onSignInRequest()
    }

    PageBackground(modifier = modifier) {
        val media = state.media

        when {
            state.loading && media == null -> DetailSkeleton()

            media == null -> Column(Modifier.statusBarsPadding()) {
                NavButtons(onBack = onBack, onHome = onHome)
                ErrorState(
                    message = state.error?.message ?: "Couldn't load this title.",
                    onRetry = viewModel::load,
                )
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item(key = "hero") {
                    DetailHero(
                        media = media,
                        bookmarked = state.bookmarked,
                        onBack = onBack,
                        onHome = onHome,
                        onBookmark = viewModel::toggleBookmark,
                        onDownload = { onDownloadRequest(media, null) },
                        onPlay = onPlay,
                        showDownloadButton = media.mediaType == MediaType.MOVIE,
                        movieDownload = downloads.firstOrNull { it.season == null },
                    )
                }

                if (media.overview.isNotBlank()) {
                    item(key = "overview") {
                        Overview(media = media)
                    }
                }

                if (media.mediaType == MediaType.TV && media.seasons.isNotEmpty()) {
                    item(key = "seasons_header") {
                        SectionHeader(title = "Seasons")
                    }
                    items(
                        items = media.seasons.filter { it.seasonNumber > 0 },
                        key = { "season_${it.seasonNumber}" },
                        contentType = { "season" },
                    ) { season ->
                        SeasonRow(
                            season = season,
                            download = downloads.firstOrNull { it.season == season.seasonNumber },
                            onDownload = { onDownloadRequest(media, season.seasonNumber) },
                            onPlay = onPlay,
                        )
                    }
                }

                if (media.cast.isNotEmpty()) {
                    item(key = "cast") { CastRail(media) }
                }

                item(key = "info") { InfoPanel(media) }

                if (media.recommendations.isNotEmpty()) {
                    item(key = "recommendations") {
                        RelatedRail("Recommended", media.recommendations, onMediaClick)
                    }
                }

                if (media.similar.isNotEmpty()) {
                    item(key = "similar") {
                        RelatedRail("More like this", media.similar, onMediaClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    media: MediaDetail,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
    onDownload: () -> Unit,
    onPlay: (String) -> Unit,
    showDownloadButton: Boolean,
    movieDownload: DownloadEntity?,
) {
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            RemoteImage(
                url = Tmdb.backdrop(media.backdropPath, Tmdb.BackdropSize.W1280),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
            )

            // Fade the backdrop into the page rather than cutting it off.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.5f to Color.Transparent,
                            1f to Tokens.Ink950,
                        )
                    )
            )

            Box(Modifier.statusBarsPadding()) {
                NavButtons(onBack = onBack, onHome = onHome)
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )

            // Metadata strip: 1942 · 104 mins · NR · ★ 6.3
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                media.year?.let { MetaText(it) }
                Format.runtime(media.runtime)?.let { MetaText(it) }
                media.certification?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
                media.seasonCount?.takeIf { media.mediaType == MediaType.TV && it > 0 }?.let {
                    MetaText(if (it == 1) "1 season" else "$it seasons")
                }
                if (media.voteAverage > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Tokens.Accent500,
                            modifier = Modifier.size(15.dp),
                        )
                        MetaText(Format.rating(media.voteAverage))
                    }
                }
            }

            if (media.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    media.genres.forEach { MetaChip(it.name) }
                }
            }

            // Action row. For a movie this is the primary action; for a series
            // the download lives per-season further down the page.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showDownloadButton) {
                    // Once it is on disk, watching it is the obvious next thing
                    // to do — downloading it a second time is not. Re-downloading
                    // stays available from the release picker.
                    // Null unless it is on disk and ready, which makes it both
                    // the condition and the thing to play.
                    val playable = movieDownload?.takeIf { it.isTerminal }
                    val finished = playable != null

                    Button(
                        onClick = {
                            if (playable != null) onPlay(playable.infoHash) else onDownload()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (finished) Tokens.Success500 else Tokens.Brand500,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = if (finished) {
                                Icons.Rounded.PlayArrow
                            } else {
                                Icons.Rounded.Download
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = when {
                                finished -> "Play"
                                movieDownload == null -> "Download"
                                else -> "Downloading…"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                CircleAction(
                    selected = bookmarked,
                    onClick = onBookmark,
                    modifier = if (showDownloadButton) Modifier else Modifier,
                ) {
                    Icon(
                        imageVector = if (bookmarked) {
                            Icons.Rounded.Bookmark
                        } else {
                            Icons.Rounded.BookmarkBorder
                        },
                        contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                        tint = if (bookmarked) Tokens.Accent500 else Tokens.Mist200,
                    )
                }
            }

            movieDownload?.takeIf { !it.isTerminal }?.let { entity ->
                LinearProgressIndicator(
                    progress = { entity.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp)),
                    color = Tokens.Brand400,
                    trackColor = Tokens.Ink700,
                )
            }

            media.tagline?.takeIf { it.isNotBlank() }?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = Tokens.Mist400,
                )
            }
        }
    }
}

@Composable
private fun Overview(media: MediaDetail) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = media.overview,
            style = MaterialTheme.typography.bodyLarge,
            color = Tokens.Mist300,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )

        media.director?.let { director ->
            LabelledValue("Director", director)
        }
        media.writers.takeIf { it.isNotEmpty() }?.let { writers ->
            LabelledValue("Writers", writers.take(3).joinToString(", "))
        }
    }
}

/**
 * One season = one download. Tapping opens the release picker already scoped to
 * this season, which is what makes "never download episodes individually" the
 * path of least resistance rather than a rule the user has to remember.
 */
@Composable
private fun SeasonRow(
    season: SeasonSummary,
    download: DownloadEntity?,
    onDownload: () -> Unit,
    onPlay: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemoteImage(
            url = Tmdb.poster(season.posterPath, Tmdb.PosterSize.W185),
            contentDescription = season.name,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = season.name.ifBlank { "Season ${season.seasonNumber}" },
                style = MaterialTheme.typography.titleMedium,
                color = Tokens.Mist200,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (season.episodeCount > 0) append("${season.episodeCount} episodes")
                    Format.date(season.airDate)?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it.takeLast(4))
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Mist400,
            )

            if (download != null && !download.isTerminal) {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp)),
                    color = Tokens.Brand400,
                    trackColor = Tokens.Ink700,
                )
            }
        }

        val playable = download?.takeIf { it.isTerminal }
        val finished = playable != null

        Button(
            onClick = { if (playable != null) onPlay(playable.infoHash) else onDownload() },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    download == null -> Tokens.Brand500
                    download.isTerminal -> Tokens.Success500
                    else -> Tokens.Ink700
                },
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (finished) Icons.Rounded.PlayArrow else Icons.Rounded.Download,
                contentDescription = if (finished) {
                    "Play season ${season.seasonNumber}"
                } else {
                    "Download season ${season.seasonNumber}"
                },
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = when {
                    download == null -> "Season"
                    download.isTerminal -> "Play"
                    else -> "${(download.progress * 100).toInt()}%"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun CastRail(media: MediaDetail) {
    Column {
        SectionHeader(title = "Cast & Crew")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = media.cast.take(24),
                key = { "${it.id}_${it.character}" },
                contentType = { "cast" },
            ) { person ->
                Column(
                    modifier = Modifier.width(88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RemoteImage(
                        url = Tmdb.profile(person.profilePath),
                        contentDescription = person.name,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Tokens.Mist200,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (person.character.isNotBlank()) {
                        Text(
                            text = person.character,
                            style = MaterialTheme.typography.labelMedium,
                            color = Tokens.Mist400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedRail(
    title: String,
    items: List<MediaSummary>,
    onMediaClick: (MediaSummary) -> Unit,
) {
    Column {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = items, key = { it.key }, contentType = { "poster" }) { media ->
                MediaCard(media = media, onClick = { onMediaClick(media) })
            }
        }
    }
}

@Composable
private fun InfoPanel(media: MediaDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleLarge,
            color = Tokens.Mist200,
        )
        media.status?.let { LabelledValue("Status", it) }
        Format.date(media.releaseDate)?.let { LabelledValue("Released", it) }
        Format.date(media.nextAirDate)?.let { LabelledValue("Next episode", it) }
        media.originalLanguage?.let { LabelledValue("Language", it.uppercase()) }
        media.productionCountries.takeIf { it.isNotEmpty() }?.let {
            LabelledValue("Countries", it.joinToString(", "))
        }
        media.studios.takeIf { it.isNotEmpty() }?.let {
            LabelledValue("Studios", it.joinToString(", ") { studio -> studio.name })
        }
        media.networks.takeIf { it.isNotEmpty() }?.let {
            LabelledValue("Network", it.joinToString(", ") { network -> network.name })
        }
        Format.money(media.budget)?.let { LabelledValue("Budget", it) }
        Format.money(media.revenue)?.let { LabelledValue("Revenue", it) }
        media.keywords.takeIf { it.isNotEmpty() }?.let {
            LabelledValue("Keywords", it.take(8).joinToString(", "))
        }
    }
}

// --- Small pieces -----------------------------------------------------------

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Tokens.Mist300,
    )
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelLarge,
            color = Tokens.Mist400,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = Tokens.Mist200,
        )
    }
}

@Composable
private fun NavButtons(onBack: () -> Unit, onHome: () -> Unit) {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScrimIconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        ScrimIconButton(onClick = onHome) {
            Icon(Icons.Rounded.Home, contentDescription = "Home", tint = Color.White)
        }
    }
}

@Composable
private fun ScrimIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun CircleAction(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (selected) Tokens.Ink700 else Tokens.Ink800)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun DetailSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
            shape = RoundedCornerShape(0.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShimmerBox(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(30.dp)
            )
            ShimmerBox(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(16.dp)
            )
            ShimmerBox(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
            )
            repeat(3) {
                ShimmerBox(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                )
            }
        }
    }
}
