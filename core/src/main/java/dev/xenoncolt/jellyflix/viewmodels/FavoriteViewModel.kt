package dev.xenoncolt.jellyflix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.Constants
import dev.xenoncolt.jellyflix.core.R
import dev.xenoncolt.jellyflix.models.FavoriteSection
import dev.xenoncolt.jellyflix.models.FindroidEpisode
import dev.xenoncolt.jellyflix.models.FindroidMovie
import dev.xenoncolt.jellyflix.models.FindroidShow
import dev.xenoncolt.jellyflix.models.UiText
import dev.xenoncolt.jellyflix.repository.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    sealed class UiState {
        data class Normal(val favoriteSections: List<FavoriteSection>) : UiState()
        data object Loading : UiState()
        data class Error(val error: Exception) : UiState()
    }

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.emit(UiState.Loading)
            try {
                val items = jellyfinRepository.getFavoriteItems()

                val favoriteSections = mutableListOf<FavoriteSection>()

                withContext(Dispatchers.Default) {
                    FavoriteSection(
                        Constants.FAVORITE_TYPE_MOVIES,
                        UiText.StringResource(R.string.movies_label),
                        items.filterIsInstance<FindroidMovie>(),
                    ).let {
                        if (it.items.isNotEmpty()) {
                            favoriteSections.add(
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
                            favoriteSections.add(
                                it,
                            )
                        }
                    }
                    FavoriteSection(
                        Constants.FAVORITE_TYPE_EPISODES,
                        UiText.StringResource(R.string.episodes_label),
                        items.filterIsInstance<FindroidEpisode>(),
                    ).let {
                        if (it.items.isNotEmpty()) {
                            favoriteSections.add(
                                it,
                            )
                        }
                    }
                }

                _uiState.emit(UiState.Normal(favoriteSections))
            } catch (e: Exception) {
                _uiState.emit(UiState.Error(e))
            }
        }
    }
}