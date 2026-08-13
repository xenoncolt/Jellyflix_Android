package dev.xenoncolt.jellyflix.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.AppError
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.Genre
import dev.xenoncolt.jellyflix.data.remote.dto.HomeResponse
import dev.xenoncolt.jellyflix.data.repo.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: HomeResponse? = null,
    val error: AppError? = null,
) {
    /**
     * Genre id -> name, built once per response so the hero carousel can label
     * a title without a lookup allocation per frame.
     */
    val genreNames: Map<Int, String> = data?.let { home ->
        buildMap {
            home.movieGenres.forEach { put(it.id, it.name) }
            home.seriesGenres.forEach { putIfAbsent(it.id, it.name) }
        }
    } ?: emptyMap()

    fun genresFor(ids: List<Int>): List<Genre> =
        ids.mapNotNull { id -> genreNames[id]?.let { Genre(id, it) } }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * One call fills all nine rails. The server already caches this per TTL
     * window, so pull-to-refresh is cheap and there is no client-side cache to
     * invalidate.
     */
    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.data == null,
                    refreshing = isRefresh,
                    error = null,
                )
            }

            when (val result = repository.home()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        data = result.data,
                        error = null,
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        // Keep whatever is on screen; an error banner beats a blank page.
                        error = result.error,
                    )
                }
            }
        }
    }
}
