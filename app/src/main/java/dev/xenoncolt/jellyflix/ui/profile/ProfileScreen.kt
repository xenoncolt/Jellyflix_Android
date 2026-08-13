package dev.xenoncolt.jellyflix.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.ui.components.MediaCard
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.components.RemoteImage
import dev.xenoncolt.jellyflix.ui.components.SectionHeader
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Profile. Login is optional everywhere in movhubs — it only unlocks bookmarks
 * and download history — so the signed-out state is a first-class screen, not
 * an error.
 */
@Composable
fun ProfileScreen(
    onMediaClick: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PageBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "header") {
                ProfileHeader(
                    state = state,
                    onSignIn = viewModel::beginSignIn,
                    onSignOut = viewModel::signOut,
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Tokens.Danger500,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            state.stats?.let { stats ->
                item(key = "stats") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatTile("Downloads", stats.downloadCount.toString(), Modifier.weight(1f))
                        StatTile("Bookmarks", stats.bookmarkCount.toString(), Modifier.weight(1f))
                        StatTile("Titles", stats.distinctTitles.toString(), Modifier.weight(1f))
                        StatTile("Total", Format.bytes(stats.totalBytes), Modifier.weight(1f))
                    }
                }
            }

            if (state.bookmarks.isNotEmpty()) {
                item(key = "bookmarks_header") { SectionHeader(title = "Bookmarks") }
                item(key = "bookmarks") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = state.bookmarks,
                            key = { it.key },
                            contentType = { "poster" },
                        ) { bookmark ->
                            val summary = MediaSummary(
                                id = bookmark.mediaId,
                                mediaType = bookmark.mediaType,
                                title = bookmark.title,
                                posterPath = bookmark.posterPath,
                                year = bookmark.year,
                            )
                            MediaCard(media = summary, onClick = { onMediaClick(summary) })
                        }
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item(key = "history_header") { SectionHeader(title = "Download history") }
                items(
                    items = state.history,
                    key = { "${it.mediaType.wire}_${it.mediaId}_${it.releaseTitle}" },
                    contentType = { "history" },
                ) { record ->
                    HistoryRow(record.title, record.releaseTitle, record.size, record.posterPath)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Tokens.Ink800),
            contentAlignment = Alignment.Center,
        ) {
            if (state.user?.avatarUrl != null) {
                RemoteImage(
                    url = state.user.avatarUrl,
                    contentDescription = state.user.displayName,
                    modifier = Modifier.size(84.dp),
                    showFallbackIcon = false,
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Tokens.Ink500,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        when {
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Tokens.Brand400,
            )

            state.user != null -> {
                Text(
                    text = state.user.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Tokens.Mist200,
                )
                OutlinedButton(onClick = onSignOut) { Text("Sign out") }
            }

            else -> {
                Text(
                    text = "Sign in to sync",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Tokens.Mist200,
                )
                Text(
                    text = "Optional. Downloads work fine without an account — " +
                        "signing in only adds bookmarks and history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.Mist400,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onSignIn,
                    enabled = !state.signingIn,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tokens.Brand500,
                        contentColor = Color.White,
                    ),
                ) {
                    if (state.signingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text("Continue with Discord", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Tokens.Mist200,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Tokens.Mist400,
        )
    }
}

@Composable
private fun HistoryRow(
    title: String,
    releaseTitle: String,
    size: Long,
    posterPath: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = Tmdb.poster(posterPath, Tmdb.PosterSize.W185),
            contentDescription = title,
            modifier = Modifier
                .size(width = 40.dp, height = 60.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Tokens.Mist200,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = releaseTitle,
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Mist400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = Format.bytes(size),
            style = MaterialTheme.typography.labelMedium,
            color = Tokens.Mist400,
        )
    }
}
