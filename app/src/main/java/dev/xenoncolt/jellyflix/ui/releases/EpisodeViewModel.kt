package dev.xenoncolt.jellyflix.ui.releases

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.EpisodeSummary
import dev.xenoncolt.jellyflix.data.remote.dto.SeasonDetail
import dev.xenoncolt.jellyflix.data.local.DownloadState
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType
import dev.xenoncolt.jellyflix.data.repo.ReleaseRepository
import dev.xenoncolt.jellyflix.player.PlaybackRepository
import dev.xenoncolt.jellyflix.torrent.TorrentRepository
import kotlinx.coroutines.Job
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
    /** Keyed by episode number — what, if anything, is on disk for each. */
    val downloads: Map<Int, EpisodeDownload> = emptyMap(),
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

/**
 * What an episode's download looks like to the list.
 *
 * Enough to decide between "download this", "downloading", and "play this" —
 * once a file is on the device, sending the user back to the release picker is
 * the wrong answer.
 */
@Immutable
data class EpisodeDownload(
    val infoHash: String,
    val state: DownloadState,
    val progress: Float,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val ready: Boolean
        get() = state == DownloadState.Completed || state == DownloadState.Seeding

    val inProgress: Boolean get() = !ready && state != DownloadState.Failed

    /** Only offer to resume once there is something worth resuming to. */
    val resumable: Boolean
        get() = ready && positionMs > RESUME_FLOOR_MS && durationMs > positionMs

    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)

    private companion object {
        const val RESUME_FLOOR_MS = 30_000L
    }
}

@HiltViewModel
class EpisodeViewModel @Inject constructor(
    private val releases: ReleaseRepository,
    private val torrents: TorrentRepository,
    private val playback: PlaybackRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EpisodeUiState())
    val state: StateFlow<EpisodeUiState> = _state.asStateFlow()

    private var loadedFor: Pair<Int, Int>? = null
    private var downloadJob: Job? = null

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

        // Its own collection so a download finishing while the sheet is open
        // updates the row without reloading the season.
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            torrents.observeForMedia(mediaId, MediaType.TV).collect { entities ->
                val forSeason = entities.filter {
                    it.season == seasonNumber && it.episode != null
                }

                val downloads = forSeason.associate { entity ->
                    val resume = if (entity.isTerminal) {
                        playback.resumeFor(entity.infoHash)
                    } else {
                        null
                    }
                    entity.episode!! to EpisodeDownload(
                        infoHash = entity.infoHash,
                        state = entity.state,
                        progress = entity.progress,
                        positionMs = resume?.positionMs ?: 0,
                        durationMs = resume?.durationMs ?: 0,
                    )
                }

                _state.update { it.copy(downloads = downloads) }
            }
        }
    }
}
