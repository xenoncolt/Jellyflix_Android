package dev.xenoncolt.jellyflix.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.AppError
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.local.DownloadEntity
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.repo.AccountRepository
import dev.xenoncolt.jellyflix.data.repo.MediaRepository
import dev.xenoncolt.jellyflix.torrent.TorrentRepository
import dev.xenoncolt.jellyflix.ui.nav.DetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class DetailUiState(
    val loading: Boolean = true,
    val media: MediaDetail? = null,
    val bookmarked: Boolean = false,
    val bookmarkPending: Boolean = false,
    val error: AppError? = null,
    val requiresAuthForBookmark: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val account: AccountRepository,
    torrents: TorrentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: DetailRoute = savedStateHandle.toRoute<DetailRoute>()
    val mediaType: MediaType = MediaType.fromWire(route.type)
    val mediaId: Int = route.id

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /**
     * Everything already downloading or downloaded for this title, so a season
     * that is in flight shows progress instead of a Download button.
     */
    val downloads: StateFlow<List<DownloadEntity>> =
        torrents.observeForMedia(mediaId, mediaType)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.media == null, error = null) }

            when (val result = repository.detail(mediaType, mediaId)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        media = result.data.media,
                        bookmarked = result.data.bookmarked,
                        error = null,
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }

    /**
     * Optimistic toggle with rollback. The request always carries an explicit
     * add/remove action, so a retry cannot silently undo itself.
     */
    fun toggleBookmark() {
        val media = _state.value.media ?: return
        if (_state.value.bookmarkPending) return

        val target = !_state.value.bookmarked
        _state.update { it.copy(bookmarked = target, bookmarkPending = true) }

        viewModelScope.launch {
            val result = account.setBookmarked(
                type = media.mediaType,
                id = media.id,
                title = media.title,
                posterPath = media.posterPath,
                year = media.year,
                bookmarked = target,
            )

            when (result) {
                is Outcome.Success -> _state.update {
                    it.copy(bookmarked = result.data, bookmarkPending = false)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        bookmarked = !target, // roll back
                        bookmarkPending = false,
                        requiresAuthForBookmark = result.error.requiresAuth,
                        error = result.error.takeIf { e -> !e.requiresAuth },
                    )
                }
            }
        }
    }

    fun consumeAuthPrompt() {
        _state.update { it.copy(requiresAuthForBookmark = false) }
    }
}
