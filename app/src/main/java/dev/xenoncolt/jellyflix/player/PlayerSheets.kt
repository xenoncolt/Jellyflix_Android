package dev.xenoncolt.jellyflix.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xenoncolt.jellyflix.ui.theme.Tokens

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Audio and subtitle tracks.
 *
 * Both lists come from the player's own track list, so an MKV's embedded tracks
 * and any `.srt` loaded alongside it appear together — once Media3 has resolved
 * them there is no difference between the two.
 */
@Composable
fun TrackSheet(
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    onAudioSelected: (String) -> Unit,
    onSubtitleSelected: (String) -> Unit,
    onLoadSubtitleFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tokens.Ink850) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SheetHeading("Audio")
            if (audio.isEmpty()) {
                EmptyRow("This file reports no audio tracks.")
            } else {
                audio.forEach { track ->
                    TrackRow(track) { onAudioSelected(track.id) }
                }
            }

            SheetHeading("Subtitles")
            subtitles.forEach { track ->
                TrackRow(track) { onSubtitleSelected(track.id) }
            }
            if (subtitles.isEmpty()) {
                EmptyRow("No subtitles in this file or its folder.")
            }

            TextButton(onClick = onLoadSubtitleFile) {
                Text("Load subtitle file…", color = Tokens.Brand300)
            }
        }
    }
}

/**
 * Speed, the episode list, and the hand-off to another player for anything this
 * one gets wrong.
 *
 * Subtitle and audio delay used to live here and no longer do: ExoPlayer has no
 * equivalent to libVLC's sync offsets, and a control that silently did nothing
 * would be worse than its absence.
 */
@Composable
fun MoreSheet(
    ui: PlayerUiState,
    player: PlayerState,
    onSpeed: (Float) -> Unit,
    onPlayIndex: (Int) -> Unit,
    onOpenElsewhere: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tokens.Ink850) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            item("speed") {
                SheetHeading("Playback speed")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SPEEDS.forEach { speed ->
                        AssistChip(
                            onClick = { onSpeed(speed) },
                            label = { Text(if (speed == 1f) "Normal" else "${speed}x") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (player.speed == speed) {
                                    Tokens.Brand500
                                } else {
                                    Tokens.Ink700
                                },
                                labelColor = Color.White,
                            ),
                        )
                    }
                }
            }

            // Only worth showing for a season pack; a film is a playlist of one.
            if (ui.playlist.size > 1) {
                item("episodes") { SheetHeading("In this download") }
                itemsIndexed(ui.playlist, key = { _, item -> item.path }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayIndex(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == ui.currentIndex) Tokens.Brand300 else Tokens.Mist200,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.canResume) {
                            Text(
                                text = "Resume",
                                style = MaterialTheme.typography.labelSmall,
                                color = Tokens.Mist400,
                            )
                        }
                    }
                }
            }

            item("elsewhere") {
                SheetHeading("Something not right?")
                TextButton(onClick = onOpenElsewhere) {
                    Text("Play in another app…", color = Tokens.Brand300)
                }
            }
        }
    }
}

@Composable
private fun DelayRow(valueMs: Long, onChange: (Long) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { onChange(valueMs - 250) }) { Text("−250ms") }
        Text(
            text = "${valueMs}ms",
            style = MaterialTheme.typography.bodyMedium,
            color = Tokens.Mist200,
        )
        TextButton(onClick = { onChange(valueMs + 250) }) { Text("+250ms") }
        if (valueMs != 0L) {
            TextButton(onClick = { onChange(0) }) { Text("Reset", color = Tokens.Mist400) }
        }
    }
}

@Composable
private fun TrackRow(track: TrackOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (track.selected) Tokens.Brand300 else Tokens.Mist200,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (track.selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Tokens.Brand300)
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Tokens.Mist200,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Tokens.Mist400,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
