package dev.xenoncolt.jellyflix.data.remote

import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRequest
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkToggleResponse
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarksResponse
import dev.xenoncolt.jellyflix.data.remote.dto.DiscoverResponse
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadLogRequest
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadsResponse
import dev.xenoncolt.jellyflix.data.remote.dto.GenresResponse
import dev.xenoncolt.jellyflix.data.remote.dto.HomeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.MagnetResponse
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetailResponse
import dev.xenoncolt.jellyflix.data.remote.dto.MeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.RevokeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.SearchResponse
import dev.xenoncolt.jellyflix.data.remote.dto.GoogleIdTokenRequest
import dev.xenoncolt.jellyflix.data.remote.dto.OkResponse
import dev.xenoncolt.jellyflix.data.remote.dto.PasswordLoginRequest
import dev.xenoncolt.jellyflix.data.remote.dto.TokenExchangeRequest
import dev.xenoncolt.jellyflix.data.remote.dto.UnlinkRequest
import dev.xenoncolt.jellyflix.data.remote.dto.UsernameAvailability
import dev.xenoncolt.jellyflix.data.remote.dto.UsernameRequest
import dev.xenoncolt.jellyflix.data.remote.dto.UsernameResponse
import dev.xenoncolt.jellyflix.data.remote.dto.TokenExchangeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.TorrentsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The movhubs HTTP API (MOBILE-HANDOFF section 6).
 *
 * This is the *only* host the app talks to for data. TMDB, TVDB and Prowlarr are
 * deliberately not reachable from here: Prowlarr is LAN-only, and any API key
 * compiled into an APK is public (handoff section 5).
 */
interface JellyflixApi {

    // --- 6.1 Discovery ------------------------------------------------------

    /** The whole home screen in one call — nine rails, one round trip. */
    @GET("api/home")
    suspend fun home(): HomeResponse

    @GET("api/genres")
    suspend fun genres(@Query("type") type: String? = null): GenresResponse

    @GET("api/search")
    suspend fun search(@Query("q") query: String): SearchResponse

    @GET("api/discover")
    suspend fun discover(
        @Query("kind") kind: String,
        @Query("page") page: Int,
        @Query("type") type: String? = null,
        @Query("scope") scope: String? = null,
        @Query("window") window: String? = null,
        @Query("id") id: Int? = null,
        @Query("q") query: String? = null,
    ): DiscoverResponse

    // --- 6.1b Detail --------------------------------------------------------

    @GET("api/media/{type}/{id}")
    suspend fun mediaDetail(
        @Path("type") type: String,
        @Path("id") id: Int,
    ): MediaDetailResponse

    // --- 6.2 Releases -------------------------------------------------------

    /**
     * Two-tier search. Call without [tier] first (~4s), then again with
     * `tier = 2` in the background (~13s) and merge the results.
     *
     * For series, passing [season] returns season packs only — single episodes
     * are dropped server-side, which is exactly what this app wants.
     */
    @GET("api/torrents")
    suspend fun torrents(
        @Query("type") type: String,
        @Query("id") id: Int,
        @Query("season") season: Int? = null,
        @Query("tier") tier: Int? = null,
    ): TorrentsResponse

    // --- 6.3 Getting the actual download ------------------------------------

    @GET("api/magnet")
    suspend fun magnet(
        @Query("type") type: String,
        @Query("id") id: Int,
        @Query("guid") guid: String,
        @Query("season") season: Int? = null,
    ): MagnetResponse

    /**
     * Returns either `application/x-bittorrent` bytes or JSON `{ magnet }` —
     * `hasFile: true` does not guarantee a file. Branch on Content-Type, which
     * is why this is a raw [Response] rather than a typed body.
     */
    @Streaming
    @GET("api/torrent-file")
    suspend fun torrentFile(
        @Query("type") type: String,
        @Query("id") id: Int,
        @Query("guid") guid: String,
        @Query("season") season: Int? = null,
    ): Response<ResponseBody>

    // --- 6.4 Account --------------------------------------------------------

    @GET("api/me")
    suspend fun me(): MeResponse

    @GET("api/bookmarks")
    suspend fun bookmarks(): BookmarksResponse

    @POST("api/bookmarks")
    suspend fun setBookmark(@Body body: BookmarkRequest): BookmarkToggleResponse

    @DELETE("api/bookmarks")
    suspend fun deleteBookmark(
        @Query("mediaType") mediaType: String,
        @Query("mediaId") mediaId: Int,
    ): BookmarkToggleResponse

    @GET("api/downloads")
    suspend fun downloadHistory(@Query("limit") limit: Int? = null): DownloadsResponse

    @POST("api/downloads")
    suspend fun logDownload(@Body body: DownloadLogRequest): Response<ResponseBody>

    // --- 6.5 Native sign-in (PKCE) ------------------------------------------

    @POST("api/auth/exchange")
    suspend fun exchangeCode(@Body body: TokenExchangeRequest): TokenExchangeResponse

    /**
     * Password sign-in. 401 for any bad credential, 429 when throttled — eight
     * failures in fifteen minutes locks both the identifier and the caller's
     * address, so the message must be surfaced rather than retried.
     */
    @POST("api/auth/login")
    suspend fun login(@Body body: PasswordLoginRequest): TokenExchangeResponse

    /**
     * Native Google sign-in. Not part of the handoff yet — the server needs an
     * endpoint that verifies a Google ID token and mints a movhubs token.
     */
    @POST("api/auth/google")
    suspend fun exchangeGoogleIdToken(@Body body: GoogleIdTokenRequest): TokenExchangeResponse

    @POST("api/auth/revoke")
    suspend fun revokeToken(): RevokeResponse

    /** Availability check for the username prompt. Debounce it (handoff 8.4). */
    @GET("api/auth/username")
    suspend fun checkUsername(@Query("username") username: String): UsernameAvailability

    /** 409 when taken, 400 when it breaks the format rules. */
    @POST("api/auth/username")
    suspend fun setUsername(@Body body: UsernameRequest): UsernameResponse

    /** 400 when the provider isn't connected, or is the last one on the account. */
    @POST("api/auth/unlink")
    suspend fun unlinkProvider(@Body body: UnlinkRequest): OkResponse
}
