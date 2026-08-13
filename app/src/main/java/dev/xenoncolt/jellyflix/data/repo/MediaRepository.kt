package dev.xenoncolt.jellyflix.data.repo

import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.core.runCatchingApi
import dev.xenoncolt.jellyflix.data.remote.JellyflixApi
import dev.xenoncolt.jellyflix.data.remote.dto.DiscoverResponse
import dev.xenoncolt.jellyflix.data.remote.dto.GenresResponse
import dev.xenoncolt.jellyflix.data.remote.dto.HomeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetailResponse
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/** Discovery kinds accepted by `/api/discover` (handoff section 6.1). */
enum class DiscoverKind(val wire: String) {
    Trending("trending"),
    Popular("popular"),
    Upcoming("upcoming"),
    TopRated("top_rated"),
    NowPlaying("now_playing"),
    Genre("genre"),
    Company("company"),
    Network("network"),
    Search("search"),
}

@Singleton
class MediaRepository @Inject constructor(
    private val api: JellyflixApi,
) {
    /**
     * The server caches aggressively (handoff section 4), so there is no benefit
     * to a client-side copy beyond OkHttp's HTTP cache — the home screen costs
     * one round trip per TTL window no matter how many people ask.
     */
    suspend fun home(): Outcome<HomeResponse> = runCatchingApi { api.home() }

    suspend fun detail(type: MediaType, id: Int): Outcome<MediaDetailResponse> =
        runCatchingApi { api.mediaDetail(type.wire, id) }

    suspend fun genres(type: MediaType? = null): Outcome<GenresResponse> =
        runCatchingApi { api.genres(type?.wire) }

    /** Live search suggestions: max 12 results, min 2 characters. */
    suspend fun search(query: String): Outcome<List<MediaSummary>> = runCatchingApi {
        if (query.trim().length < 2) emptyList() else api.search(query.trim()).results
    }

    suspend fun discover(
        kind: DiscoverKind,
        page: Int,
        type: MediaType? = null,
        scope: String? = null,
        window: String? = null,
        id: Int? = null,
        query: String? = null,
    ): Outcome<DiscoverResponse> = runCatchingApi {
        api.discover(
            kind = kind.wire,
            // TMDB caps pagination at 500.
            page = page.coerceIn(1, 500),
            type = type?.wire,
            scope = scope,
            window = window,
            id = id,
            query = query,
        )
    }
}
