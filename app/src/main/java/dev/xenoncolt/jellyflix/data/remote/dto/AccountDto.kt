package dev.xenoncolt.jellyflix.data.remote.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** MOBILE-HANDOFF sections 6.4 / 6.5. */

@Immutable
@Serializable
data class SessionUser(
    val id: String = "",
    val username: String = "",
    val globalName: String? = null,
    val avatarUrl: String? = null,
) {
    val displayName: String get() = globalName?.takeIf { it.isNotBlank() } ?: username
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
