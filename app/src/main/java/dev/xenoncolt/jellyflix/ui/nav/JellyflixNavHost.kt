package dev.xenoncolt.jellyflix.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.ui.browse.BrowseScreen
import dev.xenoncolt.jellyflix.ui.detail.DetailScreen
import dev.xenoncolt.jellyflix.ui.downloads.DownloadsScreen
import dev.xenoncolt.jellyflix.ui.grid.GridScreen
import dev.xenoncolt.jellyflix.player.PlayerScreen
import dev.xenoncolt.jellyflix.ui.home.HomeScreen
import dev.xenoncolt.jellyflix.ui.profile.ProfileScreen
import dev.xenoncolt.jellyflix.ui.releases.EpisodeSheet
import dev.xenoncolt.jellyflix.ui.releases.ReleaseSheet
import dev.xenoncolt.jellyflix.ui.search.SearchScreen
import dev.xenoncolt.jellyflix.ui.theme.Tokens
import dev.xenoncolt.jellyflix.update.UpdateDialog
import kotlin.reflect.KClass

private data class BottomTab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(HomeRoute, HomeRoute::class, "Home", Icons.Rounded.Home),
    BottomTab(BrowseRoute, BrowseRoute::class, "Browse", Icons.Rounded.VideoLibrary),
    BottomTab(DownloadsRoute, DownloadsRoute::class, "Downloads", Icons.Rounded.Download),
)

@Composable
fun JellyflixNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startOnDownloads: Boolean = false,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = bottomTabs.any { tab ->
        currentDestination?.hasRoute(tab.routeClass) == true
    }

    /**
     * The release sheet is deliberately not a nav destination: it needs the
     * fully-loaded [MediaDetail] the detail screen already has, and routing it
     * would mean re-fetching the title just to draw a sheet over it.
     */
    var releaseTarget by remember { mutableStateOf<ReleaseTarget?>(null) }

    /** The season whose episode picker is open, for a season still airing. */
    var episodeTarget by remember { mutableStateOf<Pair<MediaDetail, Int>?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = Tokens.Ink950,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Tokens.Ink900) {
                    bottomTabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.routeClass) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        // Single top-level back stack: tapping a
                                        // tab never piles up duplicate copies.
                                        popUpTo(HomeRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Tokens.Mist200,
                                indicatorColor = Tokens.Brand500,
                                unselectedIconColor = Tokens.Mist400,
                                unselectedTextColor = Tokens.Mist400,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startOnDownloads) DownloadsRoute else HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            enterTransition = {
                slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(220))
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = {
                slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(180))
            },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onMediaClick = { navController.toDetail(it) },
                    onGridClick = { navController.navigate(it) },
                    onSearchClick = { navController.navigate(SearchRoute) },
                    onProfileClick = { navController.navigate(ProfileRoute) },
                )
            }

            composable<BrowseRoute> {
                BrowseScreen(onGridClick = { navController.navigate(it) })
            }

            composable<DownloadsRoute> {
                DownloadsScreen(
                    onPlay = { infoHash -> navController.navigate(PlayerRoute(infoHash)) },
                )
            }

            composable<PlayerRoute> { entry ->
                PlayerScreen(
                    infoHash = entry.toRoute<PlayerRoute>().infoHash,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<SearchRoute> {
                SearchScreen(
                    onMediaClick = { navController.toDetail(it) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable<ProfileRoute> {
                ProfileScreen(onMediaClick = { navController.toDetail(it) })
            }

            composable<GridRoute> {
                GridScreen(
                    onMediaClick = { navController.toDetail(it) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable<DetailRoute> {
                DetailScreen(
                    onMediaClick = { navController.toDetail(it) },
                    onBack = { navController.popBackStack() },
                    onHome = {
                        navController.navigate(HomeRoute) {
                            popUpTo(HomeRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onDownloadRequest = { media, season ->
                        releaseTarget = ReleaseTarget(media, season)
                    },
                    onPickEpisodes = { media, season -> episodeTarget = media to season },
                    onPlay = { infoHash -> navController.navigate(PlayerRoute(infoHash)) },
                    onSignInRequest = { navController.navigate(ProfileRoute) },
                )
            }
        }
    }

    // Checks GitHub once per launch and shows a prompt only if there's a newer tag.
    UpdateDialog()

    episodeTarget?.let { (media, season) ->
        EpisodeSheet(
            media = media,
            seasonNumber = season,
            onDismiss = { episodeTarget = null },
            onEpisodePicked = { episode ->
                // Straight from the episode list into its releases, so picking
                // one is a single flow rather than two separate journeys.
                episodeTarget = null
                releaseTarget = ReleaseTarget(media, season, episode)
            },
        )
    }

    releaseTarget?.let { target ->
        ReleaseSheet(
            media = target.media,
            season = target.season,
            episode = target.episode,
            onDismiss = { releaseTarget = null },
            onDownloadStarted = {
                releaseTarget = null
                navController.navigate(DownloadsRoute) {
                    popUpTo(HomeRoute) { saveState = true }
                    launchSingleTop = true
                }
            },
        )
    }
}

private fun NavHostController.toDetail(media: MediaSummary) {
    navigate(DetailRoute(type = media.mediaType.wire, id = media.id))
}

/**
 * What the release sheet is being opened for.
 *
 * A triple of nullable ints would be unreadable at the call sites, and [episode]
 * has to travel all the way to `/api/magnet` — the server resolves a release id
 * against the search that produced it, so dropping it here would break the
 * download rather than just the search.
 */
private data class ReleaseTarget(
    val media: MediaDetail,
    val season: Int?,
    val episode: Int? = null,
)
