package dev.xenoncolt.jellyflix.ui.profile

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xenoncolt.jellyflix.BuildConfig
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRecord
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadRecord
import dev.xenoncolt.jellyflix.data.remote.dto.ProfileStats
import dev.xenoncolt.jellyflix.data.remote.dto.SessionUser
import dev.xenoncolt.jellyflix.data.repo.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ProfileUiState(
    val loading: Boolean = true,
    val user: SessionUser? = null,
    val stats: ProfileStats? = null,
    val bookmarks: List<BookmarkRecord> = emptyList(),
    val history: List<DownloadRecord> = emptyList(),
    val error: String? = null,
    val signingIn: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val account: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            when (val me = account.refreshSession()) {
                is Outcome.Failure -> {
                    _state.update { it.copy(loading = false, error = me.error.message) }
                    return@launch
                }

                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        user = me.data.user,
                        stats = me.data.stats,
                    )
                }
            }

            // Only meaningful once signed in; both endpoints 401 when anonymous.
            if (_state.value.user != null) {
                account.bookmarks().getOrNull()?.let { bookmarks ->
                    _state.update { it.copy(bookmarks = bookmarks) }
                }
                account.downloadHistory().getOrNull()?.let { history ->
                    _state.update { it.copy(history = history) }
                }
            }
        }
    }

    /**
     * Opens Discord in a Custom Tab — deliberately not a WebView.
     *
     * A WebView would put the user's Discord credentials inside our process and
     * give up the OS-level session, which is exactly what RFC 8252 tells native
     * apps not to do.
     */
    fun beginSignIn() {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, error = null) }

            val url = account.beginSignIn(
                baseUrl = BuildConfig.API_BASE_URL,
                scheme = BuildConfig.AUTH_SCHEME,
            )

            runCatching {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .apply { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    .launchUrl(context, url.toUri())
            }.onFailure {
                _state.update {
                    it.copy(
                        signingIn = false,
                        error = "No browser available to sign in with.",
                    )
                }
            }
        }
    }

    /** Called when `movhubs://auth?code=…` comes back through the deep link. */
    fun completeSignIn(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, error = null) }

            when (val result = account.completeSignIn(code)) {
                is Outcome.Success -> {
                    _state.update { it.copy(signingIn = false) }
                    refresh()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(signingIn = false, error = result.error.message)
                }
            }
        }
    }

    fun onSignInCancelled(reason: String?) {
        _state.update {
            it.copy(
                signingIn = false,
                error = if (reason == "denied") null else "Sign-in didn't complete.",
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            account.signOut()
            _state.value = ProfileUiState(loading = false)
        }
    }
}
