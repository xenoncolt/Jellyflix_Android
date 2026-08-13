package dev.xenoncolt.jellyflix.core

/**
 * TMDB image CDN URLs (MOBILE-HANDOFF section 9). No API key is required for
 * the image CDN, which is why this is the one non-movhubs host the app touches.
 *
 * Picking the right size matters a lot for scroll smoothness: a poster grid that
 * pulls `original` decodes multi-megabyte bitmaps and drops frames. Always ask
 * for the smallest size that covers the slot.
 */
object Tmdb {
    private const val BASE = "https://image.tmdb.org/t/p/"

    enum class PosterSize(val path: String) {
        W185("w185"),   // rails
        W342("w342"),   // grids
        W500("w500"),   // detail hero poster
    }

    enum class BackdropSize(val path: String) {
        W780("w780"),
        W1280("w1280"),
        Original("original"),
    }

    enum class ProfileSize(val path: String) { W185("w185") }

    enum class LogoSize(val path: String) {
        W92("w92"),
        W154("w154"),
        W300("w300"),
    }

    fun poster(path: String?, size: PosterSize = PosterSize.W342): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE${size.path}$it" }

    fun backdrop(path: String?, size: BackdropSize = BackdropSize.W1280): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE${size.path}$it" }

    fun profile(path: String?, size: ProfileSize = ProfileSize.W185): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE${size.path}$it" }

    fun logo(path: String?, size: LogoSize = LogoSize.W154): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE${size.path}$it" }
}
