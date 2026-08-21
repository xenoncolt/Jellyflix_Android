package dev.xenoncolt.jellyflix.player

import androidx.compose.runtime.Immutable
import dev.xenoncolt.jellyflix.data.local.PlaybackDao
import dev.xenoncolt.jellyflix.data.local.PlaybackPositionEntity
import dev.xenoncolt.jellyflix.torrent.TorrentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One playable item: a video file, plus whatever the user last did with it. */
@Immutable
data class PlayableItem(
    val file: File,
    val title: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val path: String get() = file.absolutePath

    /** Offer to resume only when there is something meaningful to resume to. */
    val canResume: Boolean get() = positionMs > RESUME_FLOOR_MS

    private companion object {
        const val RESUME_FLOOR_MS = 10_000L
    }
}

/** A subtitle file sitting next to the video, or one the user picked. */
@Immutable
data class SubtitleFile(
    val uri: String,
    val label: String,
)

/**
 * What the player needs from disk: which files are worth playing, which subtitle
 * files sit beside them, and where the user got to.
 *
 * Deliberately separate from [TorrentRepository]: a file stays playable long
 * after its torrent has been removed from the session, and the player should not
 * care whether libtorrent still knows about it.
 */
@Singleton
class PlaybackRepository @Inject constructor(
    private val torrents: TorrentRepository,
    private val playbackDao: PlaybackDao,
) {
    /** Outlives any one screen, so a final save can't be cancelled with it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "avi", "m4v", "mov", "webm", "ts", "m2ts", "flv", "wmv", "mpg", "mpeg",
        )
        val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "idx")

        /**
         * Skips the sample clips scene releases ship. They are real video files
         * in the same folder, so extension alone would offer one as an episode.
         */
        const val MIN_VIDEO_BYTES = 50L * 1024 * 1024
    }

    /**
     * Every video in a download, largest first for a movie and in name order for
     * a pack.
     *
     * Season packs are the reason this returns a list at all: one torrent holds
     * a dozen episodes, and the player needs a playlist rather than a guess.
     */
    suspend fun playableItems(infoHash: String): List<PlayableItem> =
        withContext(Dispatchers.IO) {
            val videos = torrents.filesOnDisk(infoHash)
                .filter { it.extension.lowercase() in VIDEO_EXTENSIONS }
                .filter { it.length() >= MIN_VIDEO_BYTES }
                .sortedBy { it.name.lowercase() }

            if (videos.isEmpty()) return@withContext emptyList()

            val saved = playbackDao.forDownload(infoHash).associateBy { it.filePath }

            videos.map { file ->
                val position = saved[file.absolutePath]
                PlayableItem(
                    file = file,
                    title = prettyName(file),
                    // A finished file starts over rather than reopening on the
                    // closing credits.
                    positionMs = position?.takeIf { !it.isFinished }?.positionMs ?: 0,
                    durationMs = position?.durationMs ?: 0,
                )
            }
        }

    /**
     * Subtitle files shipped inside the torrent.
     *
     * Releases commonly drop `Movie.srt` beside the video or a `Subs/` folder
     * next to it, so the whole download tree is searched rather than one
     * directory. Subtitles embedded *in* the MKV are a different thing entirely
     * — the player reads those straight off the media.
     */
    suspend fun sidecarSubtitles(infoHash: String): List<SubtitleFile> =
        withContext(Dispatchers.IO) {
            torrents.filesOnDisk(infoHash)
                .filter { it.extension.lowercase() in SUBTITLE_EXTENSIONS }
                .sortedBy { it.name.lowercase() }
                .map { SubtitleFile(uri = it.toURI().toString(), label = prettyName(it)) }
        }

    suspend fun savePosition(infoHash: String, file: File, positionMs: Long, durationMs: Long) {
        withContext(Dispatchers.IO) {
            playbackDao.upsert(
                PlaybackPositionEntity(
                    filePath = file.absolutePath,
                    infoHash = infoHash,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            )
        }
    }

    /**
     * Same write, but not tied to the caller's lifetime.
     *
     * The last save happens as the player ViewModel is cleared, and by then its
     * scope is already cancelled — a coroutine launched there would never run,
     * silently losing the resume point every single time the user leaves.
     */
    fun savePositionDetached(infoHash: String, file: File, positionMs: Long, durationMs: Long) {
        scope.launch { savePosition(infoHash, file, positionMs, durationMs) }
    }

    /**
     * Where a download was left off, without touching the filesystem.
     *
     * [playableItems] answers the same question but walks the save directory to
     * do it, which is far too much work for a list row. This reads the one row
     * Room already has.
     */
    suspend fun resumeFor(infoHash: String): PlaybackPositionEntity? =
        withContext(Dispatchers.IO) {
            playbackDao.forDownload(infoHash)
                .filter { !it.isFinished }
                .maxByOrNull { it.updatedAt }
        }

    suspend fun positionFor(file: File): Long = withContext(Dispatchers.IO) {
        playbackDao.find(file.absolutePath)
            ?.takeIf { !it.isFinished }
            ?.positionMs
            ?: 0
    }

    /** Release names are unreadable; make them a bit less so for the playlist. */
    private fun prettyName(file: File): String =
        file.nameWithoutExtension
            .replace('.', ' ')
            .replace('_', ' ')
            .trim()
}
