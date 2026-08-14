package dev.xenoncolt.jellyflix.ui.downloads

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.data.local.DownloadState
import androidx.compose.ui.platform.LocalContext
import dev.xenoncolt.jellyflix.torrent.DownloadUi
import dev.xenoncolt.jellyflix.torrent.StorageLocations
import dev.xenoncolt.jellyflix.ui.permissions.AppPermission
import dev.xenoncolt.jellyflix.ui.permissions.AppPermissions
import dev.xenoncolt.jellyflix.ui.components.EmptyState
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.components.RemoteImage
import dev.xenoncolt.jellyflix.ui.theme.Tokens

@Composable
fun DownloadsScreen(
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val sequential by viewModel.sequential.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val location by viewModel.downloadLocation.collectAsStateWithLifecycle()

    var pendingRemoval by remember { mutableStateOf<DownloadUi?>(null) }
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.setDownloadLocation(uri) }

    // The picked folder is a plain path libtorrent writes to with POSIX calls,
    // and the picker's own SAF grant does nothing for that. Below API 30 the
    // legacy permission is what grants it; ask, then open the picker either way —
    // a refusal just means the write probe rejects the folder and says so.
    val legacyStoragePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { folderPicker.launch(null) }

    // From API 30 the equivalent is All files access, which has no runtime
    // prompt — it can only be granted in Settings, so we send the user there and
    // continue to the picker when they come back.
    val fullStorageAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { folderPicker.launch(null) }

    val pickFolder = {
        when {
            StorageLocations.hasFullStorageAccess(context) ->
                // Null start URI just opens the picker at its default.
                folderPicker.launch(null)

            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

            else -> {
                // Some OEM builds are missing the per-app page, so walk the
                // fallbacks and stop at the first one that opens.
                AppPermissions.settingsIntents(context, AppPermission.Storage)
                    .any { runCatching { fullStorageAccess.launch(it) }.isSuccess }
                Unit
            }
        }
    }

    PageBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.displaySmall,
                    color = Tokens.Mist200,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }

            item(key = "settings") {
                SettingsCard(
                    wifiOnly = wifiOnly,
                    sequential = sequential,
                    location = location,
                    onWifiOnly = viewModel::setWifiOnly,
                    onSequential = viewModel::setSequential,
                    onPickLocation = pickFolder,
                    onResetLocation = viewModel::resetDownloadLocation,
                )
            }

            if (downloads.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing downloading",
                        message = "Pick a movie or a season and it'll show up here.",
                        icon = Icons.Rounded.Download,
                    )
                }
            } else {
                items(
                    items = downloads,
                    key = { it.key },
                    contentType = { "download" },
                ) { download ->
                    DownloadRow(
                        download = download,
                        onPause = { viewModel.pause(download.entity.infoHash) },
                        onResume = { viewModel.resume(download.entity.infoHash) },
                        // Finished downloads open in the built-in player now;
                        // handing off to another app moved into the player's
                        // own menu, where it can carry the current file with it.
                        onOpen = { onPlay(download.entity.infoHash) },
                        onRemove = { pendingRemoval = download },
                    )
                }
            }
        }

        message?.let { text ->
            AlertDialog(
                onDismissRequest = viewModel::consumeMessage,
                confirmButton = {
                    TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                },
                text = { Text(text) },
                containerColor = Tokens.Ink850,
            )
        }

        pendingRemoval?.let { target ->
            RemoveDialog(
                title = target.entity.displayTitle,
                onDismiss = { pendingRemoval = null },
                onConfirm = { deleteFiles ->
                    viewModel.remove(target.entity.infoHash, deleteFiles)
                    pendingRemoval = null
                },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val entity = download.entity
    val finished = download.state == DownloadState.Completed ||
        download.state == DownloadState.Seeding

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .clickable(enabled = finished, onClick = onOpen)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemoteImage(
            url = Tmdb.poster(entity.posterPath, Tmdb.PosterSize.W185),
            contentDescription = entity.title,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = entity.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Tokens.Mist200,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = listOfNotNull(entity.resolution, entity.quality, entity.indexer)
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Mist400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!finished) {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp)),
                    color = Tokens.Brand400,
                    trackColor = Tokens.Ink700,
                )
            }

            Text(
                text = statusLine(download),
                style = MaterialTheme.typography.labelMedium,
                color = when (download.state) {
                    DownloadState.Failed -> Tokens.Danger500
                    DownloadState.Completed, DownloadState.Seeding -> Tokens.Success500
                    else -> Tokens.Mist400
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                finished -> IconCircle(onClick = onOpen) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Open",
                        tint = Tokens.Success500,
                    )
                }

                download.state == DownloadState.Paused -> IconCircle(onClick = onResume) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Resume",
                        tint = Tokens.Mist200,
                    )
                }

                download.state == DownloadState.Failed -> IconCircle(onClick = onResume) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = Tokens.Mist200)
                }

                else -> IconCircle(onClick = onPause) {
                    Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = Tokens.Mist200)
                }
            }

            IconCircle(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Tokens.Mist400)
            }
        }
    }
}

private fun statusLine(download: DownloadUi): String {
    val entity = download.entity
    return when (download.state) {
        DownloadState.Completed, DownloadState.Seeding ->
            "Done · ${Format.bytes(download.totalBytes.takeIf { it > 0 } ?: entity.totalBytes)}"

        DownloadState.Paused ->
            "Paused · ${(download.progress * 100).toInt()}% of " +
                Format.bytes(download.totalBytes)

        DownloadState.Failed -> entity.errorMessage ?: "Failed"

        DownloadState.FetchingMetadata -> "Fetching metadata…"

        DownloadState.CheckingFiles -> "Checking existing files…"

        DownloadState.Queued -> "Queued"

        DownloadState.Downloading -> buildString {
            append(Format.speed(download.downloadRate))
            append(" · ")
            append(Format.bytes(download.downloadedBytes))
            append(" / ")
            append(Format.bytes(download.totalBytes))
            download.etaSeconds?.let { Format.eta(it) }?.let {
                append(" · ")
                append(it)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    wifiOnly: Boolean,
    sequential: Boolean,
    location: String,
    onWifiOnly: (Boolean) -> Unit,
    onSequential: (Boolean) -> Unit,
    onPickLocation: () -> Unit,
    onResetLocation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        ToggleRow(
            title = "Wi-Fi only",
            subtitle = "Season packs can run to tens of gigabytes",
            checked = wifiOnly,
            onCheckedChange = onWifiOnly,
        )
        ToggleRow(
            title = "Sequential download",
            subtitle = "Download in order so you can watch before it finishes",
            checked = sequential,
            onCheckedChange = onSequential,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickLocation)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download location",
                    style = MaterialTheme.typography.titleMedium,
                    color = Tokens.Mist200,
                )
                Text(
                    text = location,
                    style = MaterialTheme.typography.labelMedium,
                    color = Tokens.Mist400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = "Choose download folder",
                tint = Tokens.Brand300,
            )
        }

        if (location != "App storage (default)") {
            Text(
                text = "Reset to default",
                style = MaterialTheme.typography.labelLarge,
                color = Tokens.Brand300,
                modifier = Modifier
                    .clickable(onClick = onResetLocation)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Tokens.Mist200,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Mist400,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Tokens.Brand500,
                uncheckedThumbColor = Tokens.Mist400,
                uncheckedTrackColor = Tokens.Ink700,
                uncheckedBorderColor = Tokens.Ink600,
            ),
        )
    }
}

@Composable
private fun RemoveDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (deleteFiles: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove download?") },
        text = { Text("\"$title\" will be removed from the list.") },
        confirmButton = {
            TextButton(onClick = { onConfirm(true) }) {
                Text("Remove and delete files", color = Tokens.Danger500)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onConfirm(false) }) { Text("Keep files") }
            }
        },
        containerColor = Tokens.Ink850,
        titleContentColor = Tokens.Mist200,
        textContentColor = Tokens.Mist400,
    )
}

@Composable
private fun IconCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Tokens.Ink700)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
