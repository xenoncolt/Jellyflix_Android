package dev.xenoncolt.jellyflix.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xenoncolt.jellyflix.repository.JellyfinRepository
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsDeviceViewModel
@Inject internal constructor(
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            try {
                jellyfinRepository.updateDeviceName(name)
            } catch (e: Exception) {
                Timber.e("Could not update device name")
            }
        }
    }
}
