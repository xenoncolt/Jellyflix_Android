package dev.xenoncolt.jellyflix.data.remote.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** MOBILE-HANDOFF sections 6.4 / 6.5. */

@Immutable
@Serializable
data class SessionUser(
    val id: String = "",
    /** Unique handle. Doubles as a login identifier for password sign-in. */
    val username: String = "",
    @SerialName("displayName") val serverDisplayName: String? = null,
    /** The older shape of the same idea; kept so a stale server still renders. */
    val globalName: String? = null,
    val avatarUrl: String? = null,
    /** Also accepted as a login identifier. */
    val email: String? = null,
    /** False until the user sets a password on the website (handoff 6). */
    val hasPassword: Boolean = false,
    /**
     * e.g. ["discord"] or ["discord","google"]. One account carries both once
     * linked, and either provider then signs in to the same bookmarks and
     * history — the server links them automatically when a provider reports a
     * **verified** email that already exists (handoff 6).
     */
    val providers: List<String> = emptyList(),
    /**
     * True when the provider's handle was already taken and the account is
     * sitting on a placeholder like `name-3f9c21`. Prompt for a real username
     * before anything else.
     */
    val usernamePending: Boolean = false,
) {
    val displayName: String
        get() = serverDisplayName?.takeIf { it.isNotBlank() }
            ?: globalName?.takeIf { it.isNotBlank() }
            ?: username
}

@Immutable
@Serializable
data class ProfileStats(
    val downloadCount: Int = 0,
    val bookmarkCount: Int = 0,
    val distinctTitles: Int = 0,
    val totalBytes: Long = 0,
)

/**
 * `/api/me` answers 200 with `user: null` when anonymous rather than 401, so the
 * app can call it on launch without treating the normal case as an error.
 */
@Serializable
data class MeResponse(
    val user: SessionUser? = null,
    val stats: ProfileStats? = null,
)

@Immutable
@Serializable
data class BookmarkRecord(
    val mediaType: MediaType = MediaType.MOVIE,
    val mediaId: Int = 0,
    val title: String = "",
    val posterPath: String? = null,
    val year: String? = null,
    val createdAt: String? = null,
) {
    val key: String get() = "${mediaType.wire}:$mediaId"
}

@Serializable
data class BookmarksResponse(
    val bookmarks: List<BookmarkRecord> = emptyList(),
)

@Serializable
data class BookmarkToggleResponse(
    val bookmarked: Boolean = false,
)

/**
 * `action` is deliberately non-null here. The handoff warns that omitting it
 * falls back to toggle behaviour, and a retried toggle on a flaky connection
 * silently undoes itself.
 */
@Serializable
data class BookmarkRequest(
    val mediaType: String,
    val mediaId: Int,
    val title: String,
    val posterPath: String? = null,
    val year: String? = null,
    val action: String,
)

@Immutable
@Serializable
data class DownloadRecord(
    val mediaType: MediaType = MediaType.MOVIE,
    val mediaId: Int = 0,
    val title: String = "",
    val posterPath: String? = null,
    val releaseTitle: String = "",
    val indexer: String = "",
    val size: Long = 0,
    val quality: String? = null,
    val season: Int? = null,
    val method: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class DownloadsResponse(
    val downloads: List<DownloadRecord> = emptyList(),
)

@Serializable
data class DownloadLogRequest(
    val mediaType: String,
    val mediaId: Int,
    val title: String,
    val posterPath: String? = null,
    val releaseTitle: String,
    val indexer: String,
    val size: Long,
    val quality: String? = null,
    val season: Int? = null,
    val method: String,
)

/**
 * Password sign-in (handoff 8.1). [identifier] is the username *or* the email —
 * the server accepts either, and answers with the same 401 for every kind of
 * failure so accounts cannot be enumerated.
 */
@Serializable
data class PasswordLoginRequest(
    val identifier: String,
    val password: String,
    val device_name: String,
)

@Serializable
data class TokenExchangeRequest(
    val code: String,
    val code_verifier: String,
    val device_name: String,
)

@Serializable
data class TokenExchangeResponse(
    val token: String = "",
    val expiresAt: String? = null,
    val user: SessionUser? = null,
)

@Serializable
data class RevokeResponse(
    val revoked: Boolean = false,
)

/** Handoff 8.4. `reason` is `"taken"` or `"invalid"` when unavailable. */
@Serializable
data class UsernameAvailability(
    val available: Boolean = false,
    val reason: String? = null,
    val message: String? = null,
)

@Serializable
data class UsernameRequest(val username: String)

@Serializable
data class UsernameResponse(
    val ok: Boolean = false,
    val username: String = "",
)

@Serializable
data class UnlinkRequest(val provider: String)

@Serializable
data class OkResponse(val ok: Boolean = false)
