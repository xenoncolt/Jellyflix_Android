package dev.xenoncolt.jellyflix.torrent

import androidx.compose.runtime.Immutable
import dev.xenoncolt.jellyflix.data.local.DownloadState

/**
 * Live, in-memory state of one torrent.
 *
 * Deliberately separate from [dev.xenoncolt.jellyflix.data.local.DownloadEntity]:
 * this changes every second and must never touch disk, while the entity is the
 * durable record and is written only on meaningful transitions.
 */
@Immutable
data class TorrentProgress(
    val infoHash: String,
    val state: DownloadState,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val downloadRate: Long,
    val uploadRate: Long,
    val peers: Int,
    val seeds: Int,
) {
    /** Seconds remaining, or null when the rate is too low to extrapolate. */
    val etaSeconds: Long?
        get() {
            val remaining = totalBytes - downloadedBytes
            return if (downloadRate > 1024 && remaining > 0) remaining / downloadRate else null
        }

    companion object {
        fun idle(infoHash: String, state: DownloadState, progress: Float = 0f) = TorrentProgress(
            infoHash = infoHash,
            state = state,
            progress = progress,
            downloadedBytes = 0,
            totalBytes = 0,
            downloadRate = 0,
            uploadRate = 0,
            peers = 0,
            seeds = 0,
        )
    }
}
