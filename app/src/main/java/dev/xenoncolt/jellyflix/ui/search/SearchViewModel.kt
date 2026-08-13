package dev.xenoncolt.jellyflix.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.AppError
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.repo.MediaRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<MediaSummary> = emptyList(),
    val loading: Boolean = false,
    val error: AppError? = null,
    val hasSearched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        // Debounced so typing "interstellar" is one request, not eleven. The
        // server caches searches for 600s, but the phone's radio does not care.
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun clear() {
        _state.value = SearchUiState()
        queryFlow.value = ""
    }

    private suspend fun runSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update {
                it.copy(results = emptyList(), loading = false, error = null, hasSearched = false)
            }
            return
        }

        _state.update { it.copy(loading = true, error = null) }

        when (val result = repository.search(trimmed)) {
            is Outcome.Success -> _state.update {
                // Ignore a response for a query the user has already moved past.
                if (it.query.trim() != trimmed) it
                else it.copy(
                    results = result.data,
                    loading = false,
                    error = null,
                    hasSearched = true,
                )
            }

            is Outcome.Failure -> _state.update {
                it.copy(loading = false, error = result.error, hasSearched = true)
            }
        }
    }
}
