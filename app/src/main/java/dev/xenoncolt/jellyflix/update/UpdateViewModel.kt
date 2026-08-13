package dev.xenoncolt.jellyflix.update

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class UpdateUiState(
    val available: AvailableUpdate? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val needsInstallPermission: Boolean = false,
    val dismissed: Boolean = false,
) {
    val showDialog: Boolean get() = available != null && !dismissed
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.check()?.let { update ->
                _state.update { it.copy(available = update) }
            }
        }
    }

    fun dismiss() {
        _state.update { it.copy(dismissed = true) }
    }

    /**
     * Downloads and installs in one go. Android won't let a sideloaded app
     * install without the user confirming at the system prompt, so "automatic"
     * here means we fetch the APK and open the installer directly — no browser,
     * no hunting for the file in Downloads.
     */
    fun downloadAndInstall() {
        val update = _state.value.available ?: return
        if (_state.value.downloading) return

        if (!repository.canInstall()) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, error = null) }

            val result = repository.download(update) { fraction ->
                _state.update { it.copy(progress = fraction) }
            }

            result.fold(
                onSuccess = { apk ->
                    val installed = repository.install(apk)
                    _state.update {
                        it.copy(
                            downloading = false,
                            error = installed.exceptionOrNull()?.let { e ->
                                "Couldn't open the installer: ${e.message}"
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            downloading = false,
                            error = error.message ?: "Update download failed",
                        )
                    }
                },
            )
        }
    }

    fun installPermissionIntent() = repository.installPermissionIntent()

    fun consumePermissionPrompt() {
        _state.update { it.copy(needsInstallPermission = false) }
    }
}
