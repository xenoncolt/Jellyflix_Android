package dev.xenoncolt.jellyflix.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.Genre
import dev.xenoncolt.jellyflix.data.repo.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class BrowseUiState(
    val movieGenres: List<Genre> = emptyList(),
    val seriesGenres: List<Genre> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Genres are effectively static (86 400s TTL server-side), so one
            // fetch per process is plenty.
            when (val result = repository.genres()) {
                is Outcome.Success -> _state.value = BrowseUiState(
                    movieGenres = result.data.movie,
                    seriesGenres = result.data.tv,
                    loading = false,
                )

                is Outcome.Failure -> _state.value = BrowseUiState(loading = false)
            }
        }
    }
}
