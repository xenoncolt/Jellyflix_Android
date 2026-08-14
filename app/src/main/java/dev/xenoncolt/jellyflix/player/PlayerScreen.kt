package dev.xenoncolt.jellyflix.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Full-screen player.
 *
 * The video itself is a Media3 `PlayerView` inside an `AndroidView` — video
 * decoding renders onto a real surface, so there is no Compose equivalent.
 * Everything on top of it is Compose.
 */
@Composable
fun PlayerScreen(
    infoHash: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var orientation by rememberSaveable { mutableStateOf(ScreenOrientation.Auto) }
    var autoRotated by rememberSaveable { mutableStateOf(false) }
    val inPip = rememberInPictureInPicture()

    LaunchedEffect(infoHash) { viewModel.load(infoHash) }

    // Films are landscape, phones are held upright. Turning once, on the first
    // video whose dimensions we learn, saves the user doing it every time —
    // and only once, so a deliberate switch back is not undone a second later.
    LaunchedEffect(player.isLandscapeVideo) {
        if (player.isLandscapeVideo && !autoRotated) {
            autoRotated = true
            orientation = ScreenOrientation.Landscape
        }
    }

    ApplyOrientation(orientation)

    // Rolling on to the next episode is the ViewModel's call, not the engine's.
    LaunchedEffect(player.ended) { if (player.ended) viewModel.onEnded() }

    // Controls fade out while playing, and stay put while paused or being used.
    LaunchedEffect(controlsVisible, player.playing, showTracks, showMore) {
        if (controlsVisible && player.playing && !showTracks && !showMore) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(700)
            gestureHint = null
        }
    }

    // A player that lets the screen sleep is a broken player.
    KeepScreenOn()

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addExternalSubtitle(uri.toString(), uri.lastPathSegment ?: "External")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        // YouTube's rule: which half you hit decides the direction.
                        val forward = offset.x > size.width / 2
                        viewModel.engine.seekBy(if (forward) 10_000 else -10_000)
                        gestureHint = if (forward) "+10s" else "-10s"
                    },
                )
            }
            .pointerInput(Unit) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        if (abs(accumulated) > 8f) {
                            viewModel.engine.seekBy((accumulated * 250).toLong())
                        }
                        accumulated = 0f
                    },
                ) { _, dragAmount ->
                    accumulated += dragAmount
                    gestureHint = formatSeekHint((accumulated * 250).toLong())
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    val activity = context as? Activity ?: return@detectVerticalDragGestures
                    // Left half is brightness, right half is volume — VLC and
                    // YouTube both do this and users expect it.
                    if (change.position.x < size.width / 2) {
                        gestureHint = adjustBrightness(activity, -dragAmount / size.height)
                    } else {
                        gestureHint = adjustVolume(activity, -dragAmount / size.height)
                    }
                }
            },
    ) {
        VideoSurface(
            engine = viewModel.engine,
            fit = player.fit,
            modifier = Modifier.fillMaxSize(),
        )

        if (ui.loading || player.buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Tokens.Brand400,
            )
        }

        if (scrubbing && !inPip) {
            SeekPreviewCard(
                positionMs = scrubPosition.toLong(),
                frameFor = viewModel::previewFrame,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp),
            )
        }

        gestureHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        // A player that can't play has to offer a way out, not just a message.
        (player.error ?: ui.message)?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.Danger500,
                )
                TextButton(onClick = viewModel::openInOtherPlayer) {
                    Text("Play in another app", color = Tokens.Brand300)
                }
            }
        }

        // Controls would cover most of a PiP window, so they stay out of it.
        if (controlsVisible && !inPip) {
            PlayerControls(
                ui = ui,
                player = player,
                scrubbing = scrubbing,
                scrubPosition = scrubPosition,
                orientation = orientation,
                onRotate = {
                    orientation = orientation.next()
                    gestureHint = orientation.label
                },
                onCycleFit = {
                    viewModel.engine.cycleFit()
                    gestureHint = player.fit.next().label
                },
                onPip = {
                    val activity = context as? Activity
                    val entered = activity?.enterPip(player.videoWidth, player.videoHeight) == true
                    if (!entered) gestureHint = "Picture-in-picture unavailable"
                },
                onBack = {
                    viewModel.savePositionNow()
                    onBack()
                },
                onPlayPause = { viewModel.engine.togglePlayPause() },
                onSeekBy = { viewModel.engine.seekBy(it) },
                onScrubStart = {
                    scrubbing = true
                    scrubPosition = player.positionMs.toFloat()
                },
                onScrub = { scrubPosition = it },
                onScrubEnd = {
                    scrubbing = false
                    viewModel.engine.seekTo(scrubPosition.toLong())
                },
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onTracks = { showTracks = true },
                onMore = { showMore = true },
            )
        }

        if (showTracks) {
            TrackSheet(
                audio = player.audioTracks,
                subtitles = player.subtitleTracks,
                onAudioSelected = { viewModel.engine.selectAudioTrack(it) },
                onSubtitleSelected = { viewModel.engine.selectSubtitleTrack(it) },
                onLoadSubtitleFile = {
                    // Every subtitle format arrives as some flavour of text or
                    // octet-stream, so the filter has to stay wide.
                    subtitlePicker.launch(arrayOf("*/*"))
                },
                onDismiss = { showTracks = false },
            )
        }

        if (showMore) {
            MoreSheet(
                ui = ui,
                player = player,
                onSpeed = { viewModel.engine.setSpeed(it) },
                onPlayIndex = viewModel::playIndex,
                onOpenElsewhere = {
                    showMore = false
                    viewModel.openInOtherPlayer()
                },
                onDismiss = { showMore = false },
            )
        }
    }
}

/**
 * Hosts the video surface.
 *
 * Media3's [PlayerView] rather than a bare SurfaceView, because it also renders
 * the subtitle layer and handles the aspect-ratio maths. Its own controls are
 * switched off — everything on top is Compose.
 *
 * The player instance outlives this view across a rotation, so `update` only
 * re-points the view rather than recreating anything.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    engine: PlaybackEngine,
    fit: VideoFit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = engine.player
            }
        },
        onRelease = { view -> view.player = null },
    ) { view ->
        // Cheap and idempotent, unlike the libVLC attach this replaced — which
        // had to be guarded because it ran on every recomposition.
        if (view.player !== engine.player) view.player = engine.player
        view.resizeMode = fit.resizeMode
    }
}

@Composable
private fun PlayerControls(
    ui: PlayerUiState,
    player: PlayerState,
    scrubbing: Boolean,
    scrubPosition: Float,
    orientation: ScreenOrientation,
    onRotate: () -> Unit,
    onCycleFit: () -> Unit,
    onPip: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTracks: () -> Unit,
    onMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                text = ui.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Quick actions, in the order they get reached for.
            IconButton(onClick = onCycleFit) {
                Icon(
                    imageVector = Icons.Rounded.AspectRatio,
                    contentDescription = "Aspect ratio: ${player.fit.label}",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onRotate) {
                Icon(
                    imageVector = when (orientation) {
                        ScreenOrientation.Auto -> Icons.Rounded.ScreenRotation
                        ScreenOrientation.Landscape -> Icons.Rounded.StayCurrentLandscape
                        ScreenOrientation.Portrait -> Icons.Rounded.StayCurrentPortrait
                    },
                    contentDescription = "Rotation: ${orientation.label}",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onPip) {
                Icon(
                    Icons.Rounded.PictureInPictureAlt,
                    contentDescription = "Picture in picture",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onTracks) {
                Icon(Icons.Rounded.ClosedCaption, "Audio and subtitles", tint = Color.White)
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, "More", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.hasPrevious) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White)
                }
            }
            IconButton(onClick = { onSeekBy(-10_000) }) {
                Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White)
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (player.playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = { onSeekBy(10_000) }) {
                Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White)
            }
            if (ui.hasNext) {
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val position = if (scrubbing) scrubPosition else player.positionMs.toFloat()
            val duration = player.durationMs.coerceAtLeast(1).toFloat()

            // Chapter ticks sit on the track itself, so they line up with the
            // slider rather than floating above it.
            if (player.chapters.size > 1) {
                ChapterTicks(
                    chapters = player.chapters,
                    durationMs = player.durationMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                )
            }

            Slider(
                value = position.coerceIn(0f, duration),
                onValueChange = {
                    if (!scrubbing) onScrubStart()
                    onScrub(it)
                },
                onValueChangeFinished = onScrubEnd,
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = Tokens.Brand400,
                    activeTrackColor = Tokens.Brand500,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = Format.duration(position.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Text(
                    text = Format.duration(player.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Chapter boundaries drawn along the timeline.
 *
 * Chapters come from the container itself — see [MatroskaChapters] — rather than
 * from a server, so they appear only for files that actually carry them, which
 * in practice means most Blu-ray remuxes and few web rips.
 */
@Composable
private fun ChapterTicks(
    chapters: List<Chapter>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) return

    Canvas(modifier = modifier.height(3.dp)) {
        chapters.forEach { chapter ->
            // The first chapter starts at zero, where a tick is just noise.
            if (chapter.startMs <= 0) return@forEach
            val fraction = (chapter.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = 2.dp.toPx(),
                center = Offset(x = size.width * fraction, y = size.height / 2),
            )
        }
    }
}

/**
 * The frame under the scrubber while dragging.
 *
 * Extraction is asynchronous and snapped to a coarse grid, so this shows the
 * last frame it managed to decode rather than blocking the drag on a decode.
 */
@Composable
private fun SeekPreviewCard(
    positionMs: Long,
    frameFor: suspend (Long) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(positionMs / 10_000) {
        frame = frameFor(positionMs)
    }

    val image = frame ?: return

    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier
            .width(160.dp)
            .aspectRatio(SeekPreview.ASPECT)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentScale = ContentScale.Crop,
    )
}

/** Holds a wake lock on the window for as long as the player is on screen. */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun formatSeekHint(deltaMs: Long): String {
    val seconds = deltaMs / 1000
    return if (seconds >= 0) "+${seconds}s" else "${seconds}s"
}

private fun adjustBrightness(activity: Activity, delta: Float): String {
    val attributes = activity.window.attributes
    val current = attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
    val next = (current + delta).coerceIn(0.01f, 1f)
    attributes.screenBrightness = next
    activity.window.attributes = attributes
    return "Brightness ${(next * 100).toInt()}%"
}

private fun adjustVolume(activity: Activity, delta: Float): String {
    val audio = activity.getSystemService(android.content.Context.AUDIO_SERVICE)
        as? android.media.AudioManager ?: return ""
    val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    val current = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    val next = (current + (delta * max)).toInt().coerceIn(0, max)
    audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
    return "Volume ${(next * 100) / max}%"
}
