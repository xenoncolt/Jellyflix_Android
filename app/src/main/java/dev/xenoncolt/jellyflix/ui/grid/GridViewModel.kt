package dev.xenoncolt.jellyflix.ui.grid

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.AppError
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.repo.DiscoverKind
import dev.xenoncolt.jellyflix.data.repo.MediaRepository
import dev.xenoncolt.jellyflix.ui.nav.GridRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sort tabs offered on the Movies / Series grids. */
enum class GridSort(val label: String, val kind: DiscoverKind) {
    Popular("Popular", DiscoverKind.Popular),
    TopRated("Top Rated", DiscoverKind.TopRated),
    Upcoming("Upcoming", DiscoverKind.Upcoming),
    NowPlaying("In Cinemas", DiscoverKind.NowPlaying),
}

@Immutable
data class GridUiState(
    val title: String = "",
    val items: List<MediaSummary> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val sort: GridSort? = null,
    val mediaType: MediaType? = null,
    val trendingWindow: String? = null,
    val showSortTabs: Boolean = false,
    val showTrendingFilters: Boolean = false,
) {
    val canLoadMore: Boolean get() = page < totalPages && !loadingMore && !loading
}

@HiltViewModel
class GridViewModel @Inject constructor(
    private val repository: MediaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: GridRoute = savedStateHandle.toRoute<GridRoute>()

    private val _state = MutableStateFlow(
        GridUiState(
            title = route.title,
            mediaType = route.type?.let { MediaType.fromWire(it) },
            sort = when (route.kind) {
                "popular" -> GridSort.Popular
                "top_rated" -> GridSort.TopRated
                "upcoming" -> GridSort.Upcoming
                "now_playing" -> GridSort.NowPlaying
                else -> null
            },
            // Sort tabs only make sense for the plain list kinds; a genre or
            // studio grid has its own fixed ordering.
            showSortTabs = route.kind in setOf("popular", "top_rated", "upcoming", "now_playing"),
            showTrendingFilters = route.kind == "trending",
            trendingWindow = if (route.kind == "trending") "day" else null,
        )
    )
    val state: StateFlow<GridUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadNextPage(reset = true)
    }

    fun selectSort(sort: GridSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        loadNextPage(reset = true)
    }

    fun selectMediaType(type: MediaType?) {
        if (_state.value.mediaType == type) return
        _state.update { it.copy(mediaType = type) }
        loadNextPage(reset = true)
    }

    fun selectTrendingWindow(window: String) {
        if (_state.value.trendingWindow == window) return
        _state.update { it.copy(trendingWindow = window) }
        loadNextPage(reset = true)
    }

    fun retry() = loadNextPage(reset = _state.value.items.isEmpty())

    /**
     * Appends the next page, or starts over when [reset].
     *
     * The previous job is cancelled on reset so a slow page-3 response cannot
     * land after a filter change and repopulate the grid with stale rows.
     */
    fun loadNextPage(reset: Boolean = false) {
        val current = _state.value
        if (!reset && !current.canLoadMore) return

        if (reset) loadJob?.cancel()
        else if (loadJob?.isActive == true) return

        val nextPage = if (reset) 1 else current.page + 1

        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    error = null,
                    items = if (reset) emptyList() else it.items,
                )
            }

            val effectiveKind = when {
                current.showSortTabs -> current.sort?.kind ?: DiscoverKind.Popular
                else -> DiscoverKind.entries.firstOrNull { it.wire == route.kind }
                    ?: DiscoverKind.Popular
            }

            val result = repository.discover(
                kind = effectiveKind,
                page = nextPage,
                type = current.mediaType,
                scope = if (current.showTrendingFilters) {
                    current.mediaType?.wire ?: "all"
                } else null,
                window = current.trendingWindow,
                id = route.id,
                query = route.query,
            )

            when (result) {
                is Outcome.Success -> _state.update { state ->
                    val incoming = result.data.results
                    // The API can repeat a title across pages; de-dupe so lazy
                    // list keys stay unique (duplicate keys crash LazyGrid).
                    val existing = state.items.mapTo(HashSet()) { it.key }
                    val merged = state.items + incoming.filter { existing.add(it.key) }

                    state.copy(
                        items = merged,
                        page = result.data.page,
                        totalPages = result.data.totalPages,
                        loading = false,
                        loadingMore = false,
                        error = null,
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, loadingMore = false, error = result.error)
                }
            }
        }
    }
}
