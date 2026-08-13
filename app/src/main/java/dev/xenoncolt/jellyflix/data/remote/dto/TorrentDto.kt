package dev.xenoncolt.jellyflix.data.remote.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** MOBILE-HANDOFF section 6.2 / 6.3. */
@Immutable
@Serializable
data class TorrentRelease(
    /** 16-char digest. Only valid while the server's search cache holds it. */
    val guid: String = "",
    val title: String = "",
    val indexer: String = "",
    val size: Long = 0,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val publishDate: String? = null,
    /** Real magnet URI when the indexer exposed an info hash (~88% of results). */
    val magnetUrl: String? = null,
    val canResolveMagnet: Boolean = false,
    val hasFile: Boolean = false,
    val quality: String? = null,      // BluRay | WEB-DL | WEBRip | HDTV | DVD | CAM
    val resolution: String? = null,   // 2160p | 1080p | 720p | 480p
    val codec: String? = null,        // x265 | x264 | AV1 | XviD
    val season: Int? = null,
    val episode: Int? = null,
    val isSeasonPack: Boolean = false,
) {
    /**
     * Dedupe identity across the two search tiers. Prefer the info hash carried
     * by the magnet — the same torrent shows up on several indexers under
     * slightly different names (handoff section 7).
     */
    val dedupeKey: String
        get() = infoHash ?: title.lowercase().filter { it.isLetterOrDigit() }

    val infoHash: String?
        get() = magnetUrl
            ?.substringAfter("xt=urn:btih:", "")
            ?.substringBefore('&')
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()

    /** True when we can start a download with no further network round trip. */
    val hasDirectMagnet: Boolean get() = !magnetUrl.isNullOrBlank()
}

@Serializable
data class TorrentsResponse(
    val releases: List<TorrentRelease> = emptyList(),
)

@Serializable
data class MagnetResponse(
    val magnet: String = "",
)
