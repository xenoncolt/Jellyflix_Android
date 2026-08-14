package dev.xenoncolt.jellyflix.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * A selectable audio or subtitle track.
 *
 * The id encodes the group and track index rather than anything Media3 hands
 * out, because a selection has to be expressed as a
 * [TrackSelectionOverride] against a specific group.
 */
@Immutable
data class TrackOption(
    val id: String,
    val name: String,
    val selected: Boolean,
) {
    companion object {
        const val OFF = "__off__"
    }
}

/**
 * How the video is fitted to the screen. Maps onto Media3's resize modes.
 */
enum class VideoFit(val label: String, val resizeMode: Int) {
    /** Letterboxed, original shape. Right almost always. */
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),

    /** Fills the screen by cropping the overhang — no black bars. */
    Crop("Crop", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),

    /** Ignores the source shape and stretches to the surface. */
    Stretch("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),

    FixedWidth("Fit width", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FixedHeight("Fit height", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);

    fun next(): VideoFit = entries[(ordinal + 1) % entries.size]
}

/** A chapter marker read from the container. */
@Immutable
data class Chapter(val startMs: Long, val title: String)

@Immutable
data class PlayerState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val fit: VideoFit = VideoFit.Fit,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val chapters: List<Chapter> = emptyList(),
    val ended: Boolean = false,
    val error: String? = null,
) {
    val isLandscapeVideo: Boolean get() = videoWidth > 0 && videoWidth >= videoHeight
}

/**
 * Playback, on ExoPlayer.
 *
 * The one non-obvious piece is [DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER].
 * Media3 on its own hands audio to the device's decoders, and phones routinely
 * have none for the DTS and TrueHD tracks that come with x265 remuxes — the file
 * plays with no sound. `PREFER` puts the bundled FFmpeg audio renderer ahead of
 * the platform ones, which is what closes that gap and the reason the FFmpeg
 * decoder is a dependency at all.
 *
 * Unlike the libVLC version this replaced, nothing here is manually reference
 * counted, so the guards are about lifecycle (nothing after [release]) rather
 * than about avoiding native crashes.
 */
@OptIn(UnstableApi::class)
class PlaybackEngine(context: Context) {

    private companion object {
        const val TAG = "PlaybackEngine"
    }

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    @Volatile private var released = false

    /**
     * Built eagerly — ExoPlayer construction is cheap and does not touch native
     * code the way libVLC did, so there is nothing here that can take the
     * process down.
     */
    val player: ExoPlayer = ExoPlayer.Builder(
        appContext,
        DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            // Lets FFmpeg take over when the device's own decoder rejects a
            // stream mid-playback rather than failing the whole playback.
            .setEnableDecoderFallback(true),
    ).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(playing = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED,
                    durationMs = knownDuration(),
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                _state.value = _state.value.copy(
                    audioTracks = trackOptions(tracks, C.TRACK_TYPE_AUDIO, withOff = false),
                    subtitleTracks = trackOptions(tracks, C.TRACK_TYPE_TEXT, withOff = true),
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.value = _state.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _state.value = _state.value.copy(ended = false)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error", error)
                _state.value = _state.value.copy(
                    playing = false,
                    buffering = false,
                    error = "Couldn't play this file (${error.errorCodeName}). " +
                        "Try \"Play in another app\".",
                )
            }
        })

        // Published for PlaybackService, which the system constructs itself and
        // so cannot be handed the player any other way.
        PlaybackHolder.player = this
    }

    // --- Playback -----------------------------------------------------------

    /**
     * @param subtitles sidecar files, added as selectable text tracks alongside
     *        anything embedded in the container.
     */
    fun play(file: File, startAtMs: Long = 0, subtitles: List<SubtitleFile> = emptyList()) {
        if (released) return

        _state.value = PlayerState(buffering = true, fit = _state.value.fit)

        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setSubtitleConfigurations(
                subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                        .setMimeType(mimeTypeFor(subtitle.uri))
                        .setLabel(subtitle.label)
                        // SELECTION_FLAG_DEFAULT would force it on; the user
                        // picks from the track sheet instead.
                        .build()
                }
            )
            .build()

        player.setMediaItem(item, startAtMs.coerceAtLeast(0))
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (released) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        if (!released) player.pause()
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val duration = knownDuration()
        player.seekTo(positionMs.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE))
        _state.value = _state.value.copy(positionMs = player.currentPosition)
    }

    fun seekBy(deltaMs: Long) = seekTo(currentPositionMs + deltaMs)

    fun setSpeed(speed: Float) {
        if (released) return
        player.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    /** Called by the poll loop, since ExoPlayer has no per-tick position event. */
    fun syncPosition() {
        if (released) return
        _state.value = _state.value.copy(
            positionMs = player.currentPosition,
            durationMs = knownDuration(),
        )
    }

    val currentPositionMs: Long get() = if (released) 0 else player.currentPosition

    val currentDurationMs: Long get() = knownDuration()

    private fun knownDuration(): Long =
        if (released) 0 else player.duration.takeIf { it != C.TIME_UNSET } ?: 0

    // --- Tracks -------------------------------------------------------------

    fun setFit(fit: VideoFit) {
        _state.value = _state.value.copy(fit = fit)
    }

    fun cycleFit() = setFit(_state.value.fit.next())

    fun selectAudioTrack(id: String) = selectTrack(C.TRACK_TYPE_AUDIO, id)

    fun selectSubtitleTrack(id: String) = selectTrack(C.TRACK_TYPE_TEXT, id)

    /**
     * Applies a selection.
     *
     * Media3 has no "current track" setter — you constrain the selector with an
     * override for the group you want, and turning a type off is a disable flag
     * rather than a track named "Off".
     */
    private fun selectTrack(type: Int, id: String) {
        if (released) return

        if (id == TrackOption.OFF) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(type, true)
                .build()
            return
        }

        val (groupIndex, trackIndex) = decodeId(id) ?: return
        val group = player.currentTracks.groups
            .filter { it.type == type }
            .getOrNull(groupIndex)
            ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    private fun trackOptions(tracks: Tracks, type: Int, withOff: Boolean): List<TrackOption> {
        val groups = tracks.groups.filter { it.type == type }

        val options = groups.flatMapIndexed { groupIndex, group ->
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                TrackOption(
                    id = encodeId(groupIndex, trackIndex),
                    name = trackLabel(format.label, format.language, format.codecs),
                    selected = group.isTrackSelected(trackIndex),
                )
            }
        }

        if (!withOff || options.isEmpty()) return options

        return listOf(
            TrackOption(TrackOption.OFF, "Off", options.none { it.selected })
        ) + options
    }

    private fun trackLabel(label: String?, language: String?, codecs: String?): String =
        label?.takeIf { it.isNotBlank() }
            ?: language?.takeIf { it.isNotBlank() && it != "und" }
                ?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
            ?: codecs?.takeIf { it.isNotBlank() }
            ?: "Track"

    private fun encodeId(groupIndex: Int, trackIndex: Int) = "$groupIndex:$trackIndex"

    private fun decodeId(id: String): Pair<Int, Int>? {
        val parts = id.split(':')
        val group = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val track = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return group to track
    }

    /**
     * Subtitle format is decided by extension.
     *
     * Media3 will not sniff a sidecar file — an unset or wrong MIME type means
     * the track is silently ignored, so this has to be right.
     */
    private fun mimeTypeFor(uri: String): String = when (uri.substringAfterLast('.').lowercase()) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    fun setChapters(chapters: List<Chapter>) {
        _state.value = _state.value.copy(chapters = chapters)
    }

    // --- Teardown -----------------------------------------------------------

    fun release() {
        if (released) return
        released = true
        // Cleared before the player goes, so the service can never hand out a
        // released instance.
        PlaybackHolder.player = null
        runCatching { player.release() }
        Log.d(TAG, "Released")
    }
}
