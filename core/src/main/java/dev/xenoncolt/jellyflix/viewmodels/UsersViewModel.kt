package dev.xenoncolt.jellyflix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.api.JellyfinApi
import dev.xenoncolt.jellyflix.database.ServerDatabaseDao
import dev.xenoncolt.jellyflix.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UsersViewModel
@Inject
constructor(
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    sealed class UiState {
        data class Normal(val users: List<User>) : UiState()
        data object Loading : UiState()
        data class Error(val error: Exception) : UiState()
    }

    private val eventsChannel = Channel<UsersEvent>()
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    private var currentServerId: String = ""

    fun loadUsers(serverId: String) {
        currentServerId = serverId
        viewModelScope.launch {
            _uiState.emit(UiState.Loading)
            try {
                val serverWithUser = database.getServerWithUsers(serverId)
                _uiState.emit(UiState.Normal(serverWithUser.users))
            } catch (e: Exception) {
                _uiState.emit(UiState.Error(e))
            }
        }
    }

    /**
     * Delete user from database
     *
     * @param user The user
     */
    fun deleteUser(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUser = database.getServerCurrentUser(currentServerId)
            if (user == currentUser) {
                Timber.e("You cannot delete the current user")
                return@launch
            }
            database.deleteUser(user.id)
            loadUsers(currentServerId)
        }
    }

    fun loginAsUser(user: User) {
        viewModelScope.launch {
            val server = database.get(currentServerId) ?: return@launch
            server.currentUserId = user.id
            database.update(server)

            jellyfinApi.apply {
                api.accessToken = user.accessToken
                userId = user.id
            }

            eventsChannel.send(UsersEvent.NavigateToHome)
        }
    }
}

sealed interface UsersEvent {
    data object NavigateToHome : UsersEvent
}