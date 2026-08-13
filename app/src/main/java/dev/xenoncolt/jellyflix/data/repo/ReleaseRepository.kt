package dev.xenoncolt.jellyflix.data.repo

import dev.xenoncolt.jellyflix.core.AppError
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.core.runCatchingApi
import dev.xenoncolt.jellyflix.data.remote.JellyflixApi
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.remote.dto.TorrentRelease
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** What the app needs to actually start a torrent. */
sealed interface DownloadSource {
    data class Magnet(val uri: String) : DownloadSource
    data class TorrentFile(val bytes: ByteArray) : DownloadSource {
        // ByteArray in a data class needs these to behave sanely.
        override fun equals(other: Any?): Boolean =
            this === other || (other is TorrentFile && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

@Singleton
class ReleaseRepository @Inject constructor(
    private val api: JellyflixApi,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Tier 1: the 8 fastest trackers, ~4s. Call this first and render it.
     *
     * For series always pass [season] — the server then returns season packs
     * only, which is what this app downloads (never individual episodes).
     */
    suspend fun searchTier1(
        type: MediaType,
        id: Int,
        season: Int? = null,
    ): Outcome<List<TorrentRelease>> = runCatchingApi {
        api.torrents(type = type.wire, id = id, season = season, tier = null).releases
    }

    /**
     * Tier 2: the slower remainder of the allowlist, ~13s. Fetch in the
     * background and merge — for House of the Dragon S01 this took the result
     * count from 32 to 65 (handoff section 7).
     */
    suspend fun searchTier2(
        type: MediaType,
        id: Int,
        season: Int? = null,
    ): Outcome<List<TorrentRelease>> = runCatchingApi {
        api.torrents(type = type.wire, id = id, season = season, tier = 2).releases
    }

    /**
     * Merge two tiers, deduplicating across trackers.
     *
     * The same torrent appears on several indexers under slightly different
     * names, so we merge on info hash where available and fall back to a
     * normalised title, keeping whichever copy reports more seeders.
     */
    fun merge(
        tier1: List<TorrentRelease>,
        tier2: List<TorrentRelease>,
    ): List<TorrentRelease> {
        val byKey = LinkedHashMap<String, TorrentRelease>(tier1.size + tier2.size)
        for (release in tier1 + tier2) {
            val key = release.dedupeKey
            if (key.isBlank()) continue
            val existing = byKey[key]
            if (existing == null || release.seeders > existing.seeders) {
                // Keep the better-seeded copy, but never lose a magnet we already had.
                byKey[key] = if (existing?.hasDirectMagnet == true && !release.hasDirectMagnet) {
                    release.copy(magnetUrl = existing.magnetUrl)
                } else {
                    release
                }
            }
        }
        return byKey.values.sortedWith(
            compareByDescending<TorrentRelease> { it.seeders }
                .thenByDescending { it.size }
        )
    }

    /**
     * Resolve a release into something the torrent engine can add.
     *
     * Order matters, and it is the difference between an instant start and a
     * failure:
     *  1. `release.magnetUrl` — already in the search response for ~88% of
     *     releases, never expires, costs no round trip.
     *  2. `/api/torrent-file` — may answer with bytes *or* with JSON `{magnet}`,
     *     because `hasFile: true` does not guarantee a file. Branch on
     *     Content-Type, not on the status code.
     *  3. `/api/magnet` — last resort when the server can derive one on demand.
     *
     * A 404 anywhere here means the `guid` aged out of the server's search cache
     * (5 min fresh / 60 min stale). The caller must re-run the search rather
     * than show a dead button.
     */
    suspend fun resolve(
        type: MediaType,
        id: Int,
        release: TorrentRelease,
        season: Int? = null,
    ): Outcome<DownloadSource> {
        release.magnetUrl?.takeIf { it.isNotBlank() }?.let {
            return Outcome.Success(DownloadSource.Magnet(it))
        }

        if (release.hasFile) {
            val fromFile = runCatchingApi {
                val response = api.torrentFile(
                    type = type.wire,
                    id = id,
                    guid = release.guid,
                    season = season,
                )
                if (!response.isSuccessful) {
                    throw retrofit2.HttpException(response)
                }
                val body = response.body() ?: error("Empty response")
                val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty()

                if (contentType.contains("json", ignoreCase = true)) {
                    // Indexer publishes only a magnet — the documented JSON case.
                    val magnet = json
                        .decodeFromString<MagnetOnly>(body.string())
                        .magnet
                    if (magnet.isBlank()) error("Empty magnet")
                    DownloadSource.Magnet(magnet)
                } else {
                    DownloadSource.TorrentFile(body.bytes())
                }
            }
            if (fromFile is Outcome.Success) return fromFile
            // A stale guid will not get better by asking /api/magnet next.
            if (fromFile is Outcome.Failure && fromFile.error.isStaleGuid) return fromFile
        }

        if (release.canResolveMagnet) {
            return runCatchingApi {
                val magnet = api.magnet(
                    type = type.wire,
                    id = id,
                    guid = release.guid,
                    season = season,
                ).magnet
                if (magnet.isBlank()) error("Server returned an empty magnet")
                DownloadSource.Magnet(magnet)
            }
        }

        return Outcome.Failure(
            AppError("This release has no downloadable link. Try another one.")
        )
    }

    @kotlinx.serialization.Serializable
    private data class MagnetOnly(val magnet: String = "")
}
