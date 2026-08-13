package dev.xenoncolt.jellyflix.data.remote.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Wire shapes for the movhubs HTTP API (MOBILE-HANDOFF section 6).
 *
 * Every field carries a default. The JSON parser runs with `ignoreUnknownKeys`
 * and `explicitNulls = false`, so a server-side field addition or a null where
 * we expected a value degrades to a default instead of throwing mid-screen.
 *
 * `@Immutable` matters for performance: it tells the Compose compiler these can
 * never change under it, so lists of them skip recomposition when a parent
 * recomposes for unrelated reasons.
 */

@Serializable
enum class MediaType {
    @SerialName("movie") MOVIE,
    @SerialName("tv") TV;

    /** The value the API expects in a path or query segment. */
    val wire: String get() = if (this == MOVIE) "movie" else "tv"

    val label: String get() = if (this == MOVIE) "MOVIE" else "SERIES"

    companion object {
        fun fromWire(value: String?): MediaType = if (value == "tv") TV else MOVIE
    }
}

@Immutable
@Serializable
data class MediaSummary(
    val id: Int = 0,
    val mediaType: MediaType = MediaType.MOVIE,
    val title: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: String? = null,
    val voteAverage: Double = 0.0,
    val overview: String = "",
    val genreIds: List<Int> = emptyList(),
) {
    /** Stable identity for lazy-list keys — ids repeat across movie/tv. */
    val key: String get() = "${mediaType.wire}:$id"
}

@Immutable
@Serializable
data class Genre(
    val id: Int = 0,
    val name: String = "",
)

/** Genre tile on the home screen — carries artwork the plain [Genre] lacks. */
@Immutable
@Serializable
data class GenreTile(
    val id: Int = 0,
    val name: String = "",
    val backdropPath: String? = null,
)

/** Studio or network tile. */
@Immutable
@Serializable
data class CompanyTile(
    val id: Int = 0,
    val name: String = "",
    val logoPath: String? = null,
)

@Immutable
@Serializable
data class CastMember(
    val id: Int = 0,
    val name: String = "",
    /**
     * The live API sends this as `role`; the handoff calls it `character`.
     * Accept both so neither a server rename nor a doc correction breaks the
     * cast rail.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("role", "character")
    val character: String = "",
    val profilePath: String? = null,
)

@Immutable
@Serializable
data class CrewMember(
    val id: Int = 0,
    val name: String = "",
    val job: String = "",
    val department: String = "",
    val profilePath: String? = null,
)

@Immutable
@Serializable
data class SeasonSummary(
    val seasonNumber: Int = 0,
    val name: String = "",
    val episodeCount: Int = 0,
    val posterPath: String? = null,
    val airDate: String? = null,
    val overview: String = "",
)

@Immutable
@Serializable
data class WatchProvider(
    val id: Int = 0,
    val name: String = "",
    val logoPath: String? = null,
)

@Immutable
@Serializable
data class ExternalIds(
    val imdb: String? = null,
    val tvdb: String? = null,
)

@Immutable
@Serializable
data class MediaDetail(
    val id: Int = 0,
    val mediaType: MediaType = MediaType.MOVIE,
    val title: String = "",
    val originalTitle: String? = null,
    val tagline: String? = null,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: String? = null,
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val popularity: Double = 0.0,
    val certification: String? = null,
    val runtime: Int? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val status: String? = null,
    val releaseDate: String? = null,
    val nextAirDate: String? = null,
    val originalLanguage: String? = null,
    val revenue: Long? = null,
    val budget: Long? = null,
    val trailerKey: String? = null,
    val genres: List<Genre> = emptyList(),
    // Server may send these as bare strings or as {id,name} objects.
    @Serializable(with = NameListSerializer::class)
    val keywords: List<String> = emptyList(),
    @Serializable(with = NameListSerializer::class)
    val productionCountries: List<String> = emptyList(),
    val studios: List<CompanyTile> = emptyList(),
    val networks: List<CompanyTile> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val crew: List<CrewMember> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    val recommendations: List<MediaSummary> = emptyList(),
    val similar: List<MediaSummary> = emptyList(),
    val providers: List<WatchProvider> = emptyList(),
    val externalIds: ExternalIds? = null,
) {
    val director: String? get() = crew.firstOrNull { it.job.equals("Director", true) }?.name

    val writers: List<String>
        get() = crew.filter {
            it.job.equals("Writer", true) || it.job.equals("Screenplay", true) ||
                it.job.equals("Story", true)
        }.map { it.name }.distinct()

    fun toSummary() = MediaSummary(
        id = id,
        mediaType = mediaType,
        title = title,
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = year,
        voteAverage = voteAverage,
        overview = overview,
        genreIds = genres.map { it.id },
    )
}

@Serializable
data class MediaDetailResponse(
    val media: MediaDetail = MediaDetail(),
    val bookmarked: Boolean = false,
)

@Serializable
data class HomeResponse(
    val trending: List<MediaSummary> = emptyList(),
    val popularMovies: List<MediaSummary> = emptyList(),
    val upcomingMovies: List<MediaSummary> = emptyList(),
    val movieGenres: List<GenreTile> = emptyList(),
    val studios: List<CompanyTile> = emptyList(),
    val popularSeries: List<MediaSummary> = emptyList(),
    val upcomingSeries: List<MediaSummary> = emptyList(),
    val seriesGenres: List<GenreTile> = emptyList(),
    val networks: List<CompanyTile> = emptyList(),
)

@Serializable
data class SearchResponse(
    val results: List<MediaSummary> = emptyList(),
)

@Serializable
data class DiscoverResponse(
    val results: List<MediaSummary> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
)

@Serializable
data class GenresResponse(
    val movie: List<Genre> = emptyList(),
    val tv: List<Genre> = emptyList(),
)

@Serializable
data class ApiError(
    val error: String = "Something went wrong",
    val requiresAuth: Boolean = false,
)
