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
import dev.xenoncolt.jellyflix.core.GoogleAuth
import dev.xenoncolt.jellyflix.core.GoogleAuthResult
import dev.xenoncolt.jellyflix.core.Outcome
import dev.xenoncolt.jellyflix.core.Usernames
import dev.xenoncolt.jellyflix.data.remote.dto.BookmarkRecord
import dev.xenoncolt.jellyflix.data.remote.dto.DownloadRecord
import dev.xenoncolt.jellyflix.data.remote.dto.ProfileStats
import dev.xenoncolt.jellyflix.data.remote.dto.SessionUser
import dev.xenoncolt.jellyflix.data.repo.AccountRepository
import dev.xenoncolt.jellyflix.data.repo.AuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
    val username: UsernameUiState = UsernameUiState(),
)

/**
 * The username prompt, shown when the provider's handle collided and the
 * account is sitting on a placeholder (handoff 6).
 */
@Immutable
data class UsernameUiState(
    val value: String = "",
    /** Local format complaint, or the server's reason once checked. */
    val message: String? = null,
    val available: Boolean = false,
    val checking: Boolean = false,
    val submitting: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val account: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** Guards against re-fetching bookmarks and history for the same user. */
    private var loadedFor: String? = null

    init {
        observeSession()
        observeAuthErrors()
        refresh()
    }

    /**
     * Follows the repository's session rather than snapshotting it once.
     *
     * This is what makes sign-in actually land. The OAuth deep link comes back
     * to the *activity*, not to this screen, so nothing here ever learns the
     * exchange happened — the profile would sit on its startup snapshot showing
     * "signed out" while the token was already stored. Worse, if the callback
     * spawns a fresh activity, this ViewModel's own startup `/api/me` can race
     * the token being written and lose. Watching the singleton repository fixes
     * both: whoever completes the sign-in publishes here.
     */
    private fun observeSession() {
        viewModelScope.launch {
            // The initial null just means "not fetched yet" — skipping it avoids
            // a flash of the signed-out screen on launch.
            account.session.filterNotNull().collect { me ->
                _state.update {
                    it.copy(
                        loading = false,
                        signingIn = false,
                        user = me.user,
                        stats = me.stats,
                    )
                }

                val userId = me.user?.id
                if (userId == null) {
                    loadedFor = null
                    _state.update { it.copy(bookmarks = emptyList(), history = emptyList()) }
                } else if (userId != loadedFor) {
                    loadedFor = userId
                    loadAccountData()
                }
            }
        }
    }

    private fun observeAuthErrors() {
        viewModelScope.launch {
            account.authError.collect { message ->
                if (message != null) {
                    _state.update { it.copy(signingIn = false, error = message) }
                }
            }
        }
    }

    /** Only meaningful once signed in; both endpoints 401 when anonymous. */
    private suspend fun loadAccountData() {
        account.bookmarks().getOrNull()?.let { bookmarks ->
            _state.update { it.copy(bookmarks = bookmarks) }
        }
        account.downloadHistory().getOrNull()?.let { history ->
            _state.update { it.copy(history = history) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            // Success flows back through [observeSession], so there is nothing to
            // apply here — only the failure needs reporting.
            val result = account.refreshSession()
            if (result is Outcome.Failure) {
                _state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    /**
     * Opens the provider in a Custom Tab — deliberately not a WebView.
     *
     * A WebView would put the user's provider credentials inside our process
     * and give up the OS-level session, which is exactly what RFC 8252 tells
     * native apps not to do. Google rejects embedded webviews outright.
     */
    /**
     * Google sign-in through the system account sheet, with the browser flow as
     * a fallback.
     *
     * Needs an Activity context — Credential Manager throws on an application
     * one — which is why this takes it as a parameter instead of using the
     * injected context.
     *
     * The fallback matters more than it looks: the native sheet is unavailable
     * on a device with no Play Services, no Google account, or a build whose
     * signing certificate isn't registered against the Android OAuth client.
     * None of those should leave the user unable to sign in at all.
     */
    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, error = null) }

            when (val result = GoogleAuth.signIn(activityContext)) {
                is GoogleAuthResult.Success ->
                    // Failures are reported through the repository's authError.
                    account.signInWithGoogleIdToken(result.credential)

                GoogleAuthResult.Cancelled ->
                    _state.update { it.copy(signingIn = false) }

                is GoogleAuthResult.Unavailable -> {
                    // Straight to the browser rather than showing a dead end.
                    beginSignIn(AuthProvider.GOOGLE)
                }

                is GoogleAuthResult.Failure ->
                    _state.update { it.copy(signingIn = false, error = result.message) }
            }
        }
    }

    fun beginSignIn(provider: AuthProvider) {
        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, error = null) }

            val url = account.beginSignIn(
                baseUrl = BuildConfig.API_BASE_URL,
                scheme = BuildConfig.AUTH_SCHEME,
                provider = provider,
            )

            if (!openInBrowser(url)) {
                _state.update {
                    it.copy(
                        signingIn = false,
                        error = "No browser available to sign in with.",
                    )
                }
            }
        }
    }

    /**
     * Signs in with a password set on the website — no browser involved.
     *
     * The server answers every credential failure with the same 401 so accounts
     * cannot be enumerated, and 429 with a retry window once throttled. Both
     * arrive as a server-written message, which is safe to show verbatim.
     */
    fun signInWithPassword(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Enter your username and password.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(signingIn = true, error = null) }

            when (val result = account.signInWithPassword(identifier, password)) {
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

    /**
     * Opens the website's sign-in page, where accounts are actually created.
     * There is no in-app registration: the handle comes from the provider, so
     * there is nothing for a sign-up form to ask (handoff 6).
     */
    fun openAccountPage() {
        if (!openInBrowser(BuildConfig.API_BASE_URL.trimEnd('/') + "/login")) {
            _state.update { it.copy(error = "No browser available.") }
        }
    }

    // --- Username prompt ----------------------------------------------------

    private var usernameCheck: Job? = null

    /**
     * Checks availability as the user types, but only after they pause.
     *
     * Debounced deliberately: the endpoint is a database lookup per keystroke
     * otherwise, and the answer for a half-typed name is noise. The local format
     * rules are applied first, so an obviously wrong shape never reaches the
     * server at all.
     */
    fun onUsernameChanged(value: String) {
        usernameCheck?.cancel()

        val formatError = Usernames.formatError(value)
        _state.update {
            it.copy(
                username = it.username.copy(
                    value = value,
                    message = formatError,
                    available = false,
                    checking = formatError == null && value.isNotBlank(),
                )
            )
        }

        if (formatError != null) return

        usernameCheck = viewModelScope.launch {
            delay(400)
            when (val result = account.checkUsername(value)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        username = it.username.copy(
                            checking = false,
                            available = result.data.available,
                            message = if (result.data.available) {
                                null
                            } else {
                                result.data.message ?: "That username is taken."
                            },
                        )
                    )
                }

                is Outcome.Failure -> _state.update {
                    // Not fatal: the submit itself is the real check.
                    it.copy(username = it.username.copy(checking = false, message = null))
                }
            }
        }
    }

    fun submitUsername() {
        val candidate = _state.value.username.value
        Usernames.formatError(candidate)?.let { message ->
            _state.update { it.copy(username = it.username.copy(message = message)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(username = it.username.copy(submitting = true)) }

            when (val result = account.setUsername(candidate)) {
                is Outcome.Success -> {
                    // refresh() picks up the cleared usernamePending flag.
                    _state.update { it.copy(username = UsernameUiState()) }
                    refresh()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(
                        username = it.username.copy(
                            submitting = false,
                            available = false,
                            message = result.error.message,
                        )
                    )
                }
            }
        }
    }

    // --- Linked providers ---------------------------------------------------

    /**
     * Opens the provider's consent screen to connect it to this account, so
     * Discord and Google both sign in to the same one.
     *
     * The endpoint authenticates with the website session cookie rather than a
     * bearer token, so this only completes when the user has signed in on the
     * website in the same browser. Anything else comes back as `link_error`.
     */
    fun linkProvider(provider: AuthProvider) {
        if (!openInBrowser(account.linkProviderUrl(BuildConfig.API_BASE_URL, provider))) {
            _state.update { it.copy(error = "No browser available.") }
        }
    }

    fun unlinkProvider(provider: AuthProvider) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            // The server refuses to drop the last provider; surface its reason.
            (account.unlinkProvider(provider) as? Outcome.Failure)?.let { failure ->
                _state.update { it.copy(error = failure.error.message) }
            }
        }
    }

    private fun openInBrowser(url: String): Boolean = runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .apply { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            .launchUrl(context, url.toUri())
    }.isSuccess

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
            // Clears the sheet's "auto sign in with this account" state, so the
            // next attempt offers a choice instead of silently reusing one.
            GoogleAuth.signOut(context)
            account.signOut()
            _state.value = ProfileUiState(loading = false)
        }
    }
}
