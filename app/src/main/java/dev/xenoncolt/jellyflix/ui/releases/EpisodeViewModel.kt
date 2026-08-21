package dev.xenoncolt.jellyflix.ui.releases

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.EpisodeSummary
import dev.xenoncolt.jellyflix.data.remote.dto.SeasonDetail
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.repo.ReleaseRepository
import dev.xenoncolt.jellyflix.torrent.TorrentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class EpisodeUiState(
    val loading: Boolean = true,
    val detail: SeasonDetail? = null,
    val error: String? = null,
    /** Episode numbers already downloaded, so the list can mark them. */
    val downloaded: Set<Int> = emptySet(),
) {
    /**
     * Episodes that have actually aired.
     *
     * Filtered here rather than in the UI so there is one place deciding it —
     * the server answers 409 for anything else, so an unaired row could only
     * ever lead to a dead end.
     */
    val airedEpisodes: List<EpisodeSummary>
        get() = detail?.episodes?.filter { it.aired }.orEmpty()
}

@HiltViewModel
class EpisodeViewModel @Inject constructor(
    private val releases: ReleaseRepository,
    private val torrents: TorrentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EpisodeUiState())
    val state: StateFlow<EpisodeUiState> = _state.asStateFlow()

    private var loadedFor: Pair<Int, Int>? = null

    fun load(mediaId: Int, seasonNumber: Int) {
        val key = mediaId to seasonNumber
        if (loadedFor == key && _state.value.detail != null) return
        loadedFor = key

        viewModelScope.launch {
            _state.value = EpisodeUiState(loading = true)

            _state.value = when (val result = releases.season(mediaId, seasonNumber)) {
                is Outcome.Success -> EpisodeUiState(loading = false, detail = result.data)
                is Outcome.Failure -> EpisodeUiState(
                    loading = false,
                    error = result.error.message,
                )
            }
        }

        // Kept as its own collection so a download finishing while the sheet is
        // open marks the row without reloading the season.
        viewModelScope.launch {
            torrents.observeForMedia(mediaId, MediaType.TV).collect { entities ->
                val downloaded = entities
                    .filter { it.season == seasonNumber }
                    .mapNotNull { it.episode }
                    .toSet()
                _state.update { it.copy(downloaded = downloaded) }
            }
        }
    }
}
