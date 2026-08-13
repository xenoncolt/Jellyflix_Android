package dev.xenoncolt.jellyflix.data.repo

import android.os.Build
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.core.runCatchingApi
import dev.xenoncolt.jellyflix.data.local.SettingsStore
import dev.xenoncolt.jellyflix.data.remote.JellyflixApi
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRecord
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRequest
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadLogRequest
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadRecord
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.remote.dto.MeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.TokenExchangeRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val api: JellyflixApi,
    private val settings: SettingsStore,
) {
    private val _session = MutableStateFlow<MeResponse?>(null)

    /** Null until the first `/api/me` completes; `user == null` means anonymous. */
    val session: Flow<MeResponse?> = _session.asStateFlow()

    val isSignedIn: Flow<Boolean> = settings.authToken.map { !it.isNullOrBlank() }

    /**
     * Safe to call on launch: the endpoint answers 200 with `user: null` when
     * anonymous rather than 401, so this is not an error path.
     */
    suspend fun refreshSession(): Outcome<MeResponse> = runCatchingApi {
        api.me().also { _session.value = it }
    }

    // --- Bookmarks ----------------------------------------------------------

    suspend fun bookmarks(): Outcome<List<BookmarkRecord>> =
        runCatchingApi { api.bookmarks().bookmarks }

    /**
     * Always sends an explicit `action`. Toggle semantics race badly on mobile:
     * a retried request the app never saw answered would silently undo itself.
     */
    suspend fun setBookmarked(
        type: MediaType,
        id: Int,
        title: String,
        posterPath: String?,
        year: String?,
        bookmarked: Boolean,
    ): Outcome<Boolean> = runCatchingApi {
        api.setBookmark(
            BookmarkRequest(
                mediaType = type.wire,
                mediaId = id,
                title = title,
                posterPath = posterPath,
                year = year,
                action = if (bookmarked) "add" else "remove",
            )
        ).bookmarked
    }

    // --- Download history ---------------------------------------------------

    suspend fun downloadHistory(limit: Int = 200): Outcome<List<DownloadRecord>> =
        runCatchingApi { api.downloadHistory(limit.coerceIn(1, 500)).downloads }

    /**
     * Best-effort: history is a nicety, so a failure here must never block an
     * actual download from starting.
     */
    suspend fun logDownload(request: DownloadLogRequest): Outcome<Unit> = runCatchingApi {
        api.logDownload(request)
        Unit
    }

    // --- PKCE sign-in (handoff 6.5) ----------------------------------------

    /**
     * Builds the authorize URL and stashes the verifier. The caller opens the
     * URL in a Custom Tab — never a WebView, which would defeat the point of
     * PKCE by handing the app the user's Discord session.
     */
    suspend fun beginSignIn(baseUrl: String, scheme: String): String {
        val verifier = Pkce.generateVerifier()
        settings.setPkceVerifier(verifier)
        val challenge = Pkce.challengeOf(verifier)
        return buildString {
            append(baseUrl.trimEnd('/'))
            append("/auth/discord?code_challenge=")
            append(challenge)
            append("&redirect_uri=")
            append("$scheme://auth")
        }
    }

    /** Exchanges the one-time code from the deep link for a 180-day bearer token. */
    suspend fun completeSignIn(code: String): Outcome<Unit> {
        val verifier = settings.pkceVerifier()
            ?: return Outcome.Failure(
                dev.xenoncolt.jellyflix.core.AppError("Sign-in expired. Try again.")
            )

        val result = runCatchingApi {
            api.exchangeCode(
                TokenExchangeRequest(
                    code = code,
                    code_verifier = verifier,
                    device_name = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                )
            )
        }

        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                settings.setAuthToken(result.data.token)
                settings.setPkceVerifier(null)
                refreshSession()
                Outcome.Success(Unit)
            }
        }
    }

    suspend fun signOut() {
        // Tell the server first — after we drop the token we can no longer authenticate.
        runCatchingApi { api.revokeToken() }
        settings.setAuthToken(null)
        _session.value = MeResponse(user = null, stats = null)
    }
}
