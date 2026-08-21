package dev.xenoncolt.jellyflix.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType

/**
 * Lifecycle of a download, as the UI understands it.
 *
 * libtorrent has more states than this (checking, allocating, ...); they are
 * collapsed here because a user does not care about the difference between
 * "allocating" and "checking resume data".
 */
enum class DownloadState {
    Queued,
    CheckingFiles,
    FetchingMetadata,
    Downloading,
    Paused,
    Seeding,
    Completed,
    Failed,
}

/**
 * Persistent record of a torrent, joined to the media it belongs to.
 *
 * Storing the poster path alongside the torrent (rather than re-fetching from
 * the API) is what lets the downloads screen show artwork while offline — the
 * handoff calls this out in section 8.
 *
 * Live progress is *not* written here on every tick; that would mean a disk
 * write per second per torrent. The engine keeps progress in memory and this
 * row is refreshed on meaningful transitions and periodically.
 */
@Immutable
@Entity(
    tableName = "downloads",
    indices = [Index("mediaId"), Index("addedAt")],
)
data class DownloadEntity(
    /** Torrent info hash (v1, hex). Stable across restarts, unlike the API guid. */
    @PrimaryKey val infoHash: String,

    val mediaType: MediaType,
    val mediaId: Int,
    val title: String,
    val posterPath: String?,
    val year: String?,

    /** Null for movies; the season number for a series season pack. */
    val season: Int?,

    /**
     * Set only when a single episode was downloaded rather than a whole season,
     * which is what a season still airing offers — there is no pack for it yet.
     */
    val episode: Int? = null,

    val releaseTitle: String,
    val indexer: String,
    val quality: String?,
    val resolution: String?,
    val codec: String?,

    val totalBytes: Long,
    val downloadedBytes: Long = 0,
    val progress: Float = 0f,

    val state: DownloadState = DownloadState.Queued,
    val errorMessage: String? = null,

    /** Where the payload lands on disk. */
    val savePath: String,

    /** Kept so a download can be re-added after a failure without a new search. */
    val magnetUri: String?,

    val sequential: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val isTerminal: Boolean
        get() = state == DownloadState.Completed || state == DownloadState.Seeding

    val isActive: Boolean
        get() = state == DownloadState.Downloading ||
            state == DownloadState.FetchingMetadata ||
            state == DownloadState.CheckingFiles ||
            state == DownloadState.Queued

    /** "Reacher — S04E01" / "Pioneer One — Season 1" / "Jungle Book" */
    val displayTitle: String
        get() = when {
            season != null && episode != null ->
                "$title — %s".format("S%02dE%02d".format(season, episode))
            season != null -> "$title — Season $season"
            else -> title
        }
}
