package dev.xenoncolt.jellyflix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.AppPreferences
import dev.xenoncolt.jellyflix.Constants
import dev.xenoncolt.jellyflix.core.R
import dev.xenoncolt.jellyflix.models.FavoriteSection
import dev.xenoncolt.jellyflix.models.FindroidMovie
import dev.xenoncolt.jellyflix.models.FindroidShow
import dev.xenoncolt.jellyflix.models.UiText
import dev.xenoncolt.jellyflix.repository.JellyfinRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val repository: JellyfinRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<DownloadsEvent>()
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    sealed class UiState {
        data class Normal(val sections: List<FavoriteSection>) : UiState()
        data object Loading : UiState()
        data class Error(val error: Exception) : UiState()
    }

    init {
        testServerConnection()
    }

    private fun testServerConnection() {
        viewModelScope.launch {
            try {
                if (appPreferences.offlineMode) return@launch
                repository.getPublicSystemInfo()
                // Give the UI a chance to load
                delay(100)
            } catch (e: Exception) {
                eventsChannel.send(DownloadsEvent.ConnectionError(e))
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.emit(UiState.Loading)

            val sections = mutableListOf<FavoriteSection>()

            val items = repository.getDownloads()

            FavoriteSection(
                Constants.FAVORITE_TYPE_MOVIES,
                UiText.StringResource(R.string.movies_label),
                items.filterIsInstance<FindroidMovie>(),
            ).let {
                if (it.items.isNotEmpty()) {
                    sections.add(
                        it,
                    )
                }
            }
            FavoriteSection(
                Constants.FAVORITE_TYPE_SHOWS,
                UiText.StringResource(R.string.shows_label),
                items.filterIsInstance<FindroidShow>(),
            ).let {
                if (it.items.isNotEmpty()) {
                    sections.add(
                        it,
                    )
                }
            }
            _uiState.emit(UiState.Normal(sections))
        }
    }
}

sealed interface DownloadsEvent {
    data class ConnectionError(val error: Exception) : DownloadsEvent
}