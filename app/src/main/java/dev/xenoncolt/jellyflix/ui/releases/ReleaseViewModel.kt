package dev.xenoncolt.jellyflix.ui.releases

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.AppError
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.MediaDetail
import dev.xenoncolt.jellyflix.data.remote.dto.TorrentRelease
import dev.xenoncolt.jellyflix.data.repo.ReleaseRepository
import dev.xenoncolt.jellyflix.torrent.TorrentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ReleaseUiState(
    val loading: Boolean = true,
    /** Tier 2 is still in flight; results keep arriving. */
    val loadingMore: Boolean = false,
    val releases: List<TorrentRelease> = emptyList(),
    val error: AppError? = null,
    val resolutionFilter: String? = null,
    val startingGuid: String? = null,
    val startedInfoHash: String? = null,
    val startError: String? = null,
) {
    val availableResolutions: List<String>
        get() = releases.mapNotNull { it.resolution }
            .distinct()
            .sortedByDescending { it.dropLast(1).toIntOrNull() ?: 0 }

    val visibleReleases: List<TorrentRelease>
        get() = resolutionFilter
            ?.let { filter -> releases.filter { it.resolution == filter } }
            ?: releases
}

/**
 * Backs the release picker sheet.
 *
 * Search shape follows handoff section 6.2: tier 1 (~4s) renders as soon as it
 * lands, tier 2 (~13s) merges in behind it. Waiting for both would double the
 * time to first result for no benefit.
 */
@HiltViewModel
class ReleaseViewModel @Inject constructor(
    private val releases: ReleaseRepository,
    private val torrents: TorrentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReleaseUiState())
    val state: StateFlow<ReleaseUiState> = _state.asStateFlow()

    private var media: MediaDetail? = null
    private var season: Int? = null
    private var searchJob: Job? = null

    fun search(media: MediaDetail, season: Int?) {
        // Same target as the request already in flight — don't restart it.
        if (this.media?.id == media.id && this.season == season && searchJob?.isActive == true) {
            return
        }

        this.media = media
        this.season = season
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _state.value = ReleaseUiState(loading = true, loadingMore = true)

            // Fire both tiers at once; render whichever arrives first.
            val tier2 = async {
                releases.searchTier2(media.mediaType, media.id, season)
            }

            when (val tier1 = releases.searchTier1(media.mediaType, media.id, season)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        releases = releases.merge(tier1.data, emptyList()),
                    )
                }

                is Outcome.Failure -> _state.update {
                    // Tier 2 may still save us, so this is not fatal yet.
                    it.copy(loading = false, error = tier1.error)
                }
            }

            when (val result = tier2.await()) {
                is Outcome.Success -> _state.update { current ->
                    current.copy(
                        loadingMore = false,
                        releases = releases.merge(current.releases, result.data),
                        // Tier 2 answered, so a tier-1 failure no longer matters.
                        error = if (result.data.isNotEmpty()) null else current.error,
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        loadingMore = false,
                        error = if (it.releases.isEmpty()) result.error else null,
                    )
                }
            }
        }
    }

    fun setResolutionFilter(resolution: String?) {
        _state.update { it.copy(resolutionFilter = resolution) }
    }

    fun consumeStartResult() {
        _state.update { it.copy(startedInfoHash = null, startError = null) }
    }

    /**
     * Resolves a release to a magnet or `.torrent` and hands it to the engine.
     *
     * A stale `guid` (the server's search cache is 5 min fresh / 60 min stale)
     * comes back as a 404. That means "search again", not "this button is
     * broken", so we re-run the search and tell the user to retry.
     */
    fun startDownload(release: TorrentRelease) {
        val media = media ?: return
        if (_state.value.startingGuid != null) return

        viewModelScope.launch {
            _state.update { it.copy(startingGuid = release.guid, startError = null) }

            when (val source = releases.resolve(media.mediaType, media.id, release, season)) {
                is Outcome.Failure -> {
                    if (source.error.isStaleGuid) {
                        _state.update {
                            it.copy(
                                startingGuid = null,
                                startError = "That release expired. Refreshing the list…",
                            )
                        }
                        search(media, season)
                    } else {
                        _state.update {
                            it.copy(startingGuid = null, startError = source.error.message)
                        }
                    }
                }

                is Outcome.Success -> {
                    val result = torrents.startDownload(
                        media = media,
                        release = release,
                        source = source.data,
                        season = season,
                    )
                    _state.update {
                        it.copy(
                            startingGuid = null,
                            startedInfoHash = result.getOrNull(),
                            startError = result.exceptionOrNull()?.message,
                        )
                    }
                }
            }
        }
    }
}
