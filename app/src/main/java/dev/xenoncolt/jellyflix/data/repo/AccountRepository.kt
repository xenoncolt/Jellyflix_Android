package dev.xenoncolt.jellyflix.data.repo

import android.os.Build
import dev.xenoncolt.jellyflix.core.GoogleIdToken
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.core.runCatchingApi
import dev.xenoncolt.jellyflix.data.local.SettingsStore
import dev.xenoncolt.jellyflix.data.remote.JellyflixApi
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRecord
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRequest
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadLogRequest
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadRecord
import dev.xenoncolt.jellyflix.data.remote.dto.GoogleIdTokenRequest
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.remote.dto.MeResponse
import dev.xenoncolt.jellyflix.data.remote.dto.PasswordLoginRequest
import dev.xenoncolt.jellyflix.data.remote.dto.TokenExchangeRequest
import dev.xenoncolt.jellyflix.data.remote.dto.UnlinkRequest
import dev.xenoncolt.jellyflix.data.remote.dto.UsernameAvailability
import dev.xenoncolt.jellyflix.data.remote.dto.UsernameRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The OAuth providers the server offers. Both use the same PKCE flow. */
enum class AuthProvider(val path: String, val label: String) {
    DISCORD("discord", "Discord"),
    GOOGLE("google", "Google"),
}

@Singleton
class AccountRepository @Inject constructor(
    private val api: JellyflixApi,
    private val settings: SettingsStore,
) {
    private val _session = MutableStateFlow<MeResponse?>(null)

    /** Null until the first `/api/me` completes; `user == null` means anonymous. */
    val session: Flow<MeResponse?> = _session.asStateFlow()

    /**
     * Failures from the sign-in deep link, which arrives at the activity rather
     * than at any screen.
     *
     * `movhubs://auth` can come back with `?error=denied|exchange` instead of a
     * code, and the exchange itself can fail. Neither has a ViewModel to report
     * to — whichever screen is listening picks it up here.
     */
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun reportAuthError(message: String?) {
        _authError.value = message
    }

    fun clearAuthError() {
        _authError.value = null
    }

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
     * PKCE by handing the app the user's provider session, and which Google
     * blocks outright.
     *
     * The flow is byte-for-byte identical for both providers; only the path
     * segment changes (handoff 8.2).
     */
    suspend fun beginSignIn(
        baseUrl: String,
        scheme: String,
        provider: AuthProvider,
    ): String {
        val verifier = Pkce.generateVerifier()
        settings.setPkceVerifier(verifier)
        val challenge = Pkce.challengeOf(verifier)
        return buildString {
            append(baseUrl.trimEnd('/'))
            append("/auth/${provider.path}?code_challenge=")
            append(challenge)
            append("&redirect_uri=")
            append("$scheme://auth")
        }
    }

    /**
     * Signs in with a password set on the website, which is the one path that
     * needs no browser at all (handoff 8.1).
     *
     * No PKCE verifier is involved, so nothing is stashed beforehand and there
     * is nothing to clean up on failure.
     */
    suspend fun signInWithPassword(identifier: String, password: String): Outcome<Unit> {
        val result = runCatchingApi {
            api.login(
                PasswordLoginRequest(
                    identifier = identifier.trim(),
                    password = password,
                    device_name = deviceName(),
                )
            )
        }

        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                settings.setAuthToken(result.data.token)
                refreshSession()
                Outcome.Success(Unit)
            }
        }
    }

    /**
     * Completes a native Google sign-in.
     *
     * The app never trusts the ID token itself — it is just a signed assertion
     * from Google, and only the server can check the signature, that `aud` is
     * our own web client, and that the nonce matches. What comes back is the
     * same movhubs token every other sign-in path produces.
     */
    suspend fun signInWithGoogleIdToken(token: GoogleIdToken): Outcome<Unit> {
        val result = runCatchingApi {
            api.exchangeGoogleIdToken(
                GoogleIdTokenRequest(
                    id_token = token.idToken,
                    nonce = token.nonce,
                    device_name = deviceName(),
                )
            )
        }

        return when (result) {
            is Outcome.Failure -> {
                reportAuthError(result.error.message)
                result
            }

            is Outcome.Success -> {
                settings.setAuthToken(result.data.token)
                clearAuthError()
                refreshSession()
                Outcome.Success(Unit)
            }
        }
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    // --- Username and linked providers (handoff 8.4) -------------------------

    /** Availability check for the username prompt. Callers should debounce. */
    suspend fun checkUsername(candidate: String): Outcome<UsernameAvailability> =
        runCatchingApi { api.checkUsername(candidate.trim()) }

    /**
     * Claims a username. 409 means taken, 400 means the format is wrong; both
     * arrive as a server message worth showing as-is.
     */
    suspend fun setUsername(username: String): Outcome<String> {
        val result = runCatchingApi { api.setUsername(UsernameRequest(username.trim())) }
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                // Clears usernamePending for everything watching the session.
                refreshSession()
                Outcome.Success(result.data.username)
            }
        }
    }

    /**
     * Disconnects a provider. The server refuses to remove the last one — the
     * website signs in through OAuth only, so dropping it would lock the
     * account out even with a password set.
     */
    suspend fun unlinkProvider(provider: AuthProvider): Outcome<Unit> {
        val result = runCatchingApi { api.unlinkProvider(UnlinkRequest(provider.path)) }
        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                refreshSession()
                Outcome.Success(Unit)
            }
        }
    }

    /**
     * Where to send the browser to connect a second provider.
     *
     * This one is a browser round trip because it ends at the provider's own
     * consent screen, and the endpoint authenticates with the **session cookie**
     * — there is no bearer-token variant. A Custom Tab shares the browser's
     * cookie jar, so it works when the user has signed in on the website in that
     * browser, and returns `link_error=session` when they have not.
     */
    fun linkProviderUrl(baseUrl: String, provider: AuthProvider): String =
        "${baseUrl.trimEnd('/')}/auth/${provider.path}?link=1&next=/profile"

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
                    device_name = deviceName(),
                )
            )
        }

        return when (result) {
            is Outcome.Failure -> {
                reportAuthError(result.error.message)
                result
            }

            is Outcome.Success -> {
                settings.setAuthToken(result.data.token)
                settings.setPkceVerifier(null)
                clearAuthError()
                // Publishes the signed-in user to everything watching [session].
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
