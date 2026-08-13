package dev.xenoncolt.jellyflix.ui.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation Compose 2.8+).
 *
 * Serializable objects rather than string routes means a typo in an argument is
 * a compile error instead of a blank screen at runtime.
 */

@Serializable data object HomeRoute

@Serializable data object SearchRoute

@Serializable data object DownloadsRoute

@Serializable data object ProfileRoute

@Serializable data object BrowseRoute

@Serializable
data class DetailRoute(val type: String, val id: Int)

/**
 * A paginated grid. [kind] maps onto `/api/discover?kind=`; [id] carries the
 * genre / company / network id where the kind needs one.
 */
@Serializable
data class GridRoute(
    val kind: String,
    val title: String,
    val type: String? = null,
    val id: Int? = null,
    val query: String? = null,
)
